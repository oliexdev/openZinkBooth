/*
 * openZinkBooth
 * Copyright (C) 2026 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.photo.openzinkbooth.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.photo.openzinkbooth.R
import com.photo.openzinkbooth.core.database.FrameEntry
import com.photo.openzinkbooth.core.database.sanitizeFrameName
import com.photo.openzinkbooth.ui.components.composeFrameThumbnail

// ---------------------------------------------------------------------------
// FrameManagerScreen – M3 frame list manager with smooth drag-and-drop.
//
// Drag implementation uses LazyListState.layoutInfo to hit-test which item
// is under the pointer on every drag event, enabling continuous multi-step
// reordering in a single drag gesture.
//
// M3 layout per row:
//   Leading:  Switch (visibility)
//   Content:  Thumbnail + Name + type label
//   Trailing: ⋮ menu (custom only) + ≡ DragHandle
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameManagerScreen(
    entries: List<FrameEntry>,
    onBack: () -> Unit,
    onSetVisible: (String, Boolean) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onAddCustom: (android.net.Uri, String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    loadCustomBitmap: (String) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingUri    by remember { mutableStateOf<android.net.Uri?>(null) }
    var renameTarget  by remember { mutableStateOf<FrameEntry.Custom?>(null) }
    var deleteTarget  by remember { mutableStateOf<FrameEntry.Custom?>(null) }

    // draggedIndex – which list index is currently being dragged
    // dragOffsetY  – visual Y offset of the floating item (pointer delta from drag start)
    // pointerY     – absolute Y of the pointer within the LazyColumn, updated on every move
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY  by remember { mutableStateOf(0f) }
    var pointerY     by remember { mutableStateOf(0f) }

    val listState = rememberLazyListState()

    // Hit-test: find which visible item contains the given absolute Y coordinate.
    // Returns the index into [entries], or null if nothing is found.
    fun itemIndexAt(absoluteY: Float): Int? {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { itemInfo ->
            absoluteY >= itemInfo.offset && absoluteY < itemInfo.offset + itemInfo.size
        }
        return item?.index
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) { pendingUri = uri; showAddDialog = true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.frame_manager_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.camera_back_description)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.frame_manager_add)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.PhotoFilter,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text  = stringResource(R.string.frame_manager_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state   = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // Single pointerInput on the entire LazyColumn so we can
                    // hit-test any item, not just the one the gesture started on.
                    .pointerInput(entries.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset: Offset ->
                                val startIndex = itemIndexAt(offset.y)
                                draggedIndex = startIndex
                                dragOffsetY  = 0f
                                pointerY     = offset.y
                            },
                            onDrag = { _, dragAmount ->
                                dragOffsetY += dragAmount.y
                                pointerY    += dragAmount.y

                                // Find the item currently under the pointer
                                val targetIndex = itemIndexAt(pointerY) ?: return@detectDragGesturesAfterLongPress
                                val currentIndex = draggedIndex ?: return@detectDragGesturesAfterLongPress

                                if (targetIndex != currentIndex) {
                                    // Swap one step toward targetIndex per event so the
                                    // visual order matches what the user expects
                                    if (targetIndex < currentIndex && currentIndex > 0) {
                                        onMoveUp(currentIndex)
                                        draggedIndex = currentIndex - 1
                                        // Compensate offset so the item stays under the finger
                                        val itemSize = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.index == draggedIndex }?.size?.toFloat() ?: 0f
                                        dragOffsetY += itemSize
                                    } else if (targetIndex > currentIndex && currentIndex < entries.size - 1) {
                                        onMoveDown(currentIndex)
                                        draggedIndex = currentIndex + 1
                                        val itemSize = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.index == draggedIndex }?.size?.toFloat() ?: 0f
                                        dragOffsetY -= itemSize
                                    }
                                }
                            },
                            onDragEnd    = { draggedIndex = null; dragOffsetY = 0f },
                            onDragCancel = { draggedIndex = null; dragOffsetY = 0f }
                        )
                    },
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(entries, key = { _, e -> e.id }) { index, entry ->
                    val isDragging = draggedIndex == index

                    FrameListItem(
                        entry           = entry,
                        isDragging      = isDragging,
                        dragOffsetY     = if (isDragging) dragOffsetY else 0f,
                        onSetVisible    = { onSetVisible(entry.id, it) },
                        onRenameRequest = if (entry is FrameEntry.Custom) {
                            { renameTarget = entry }
                        } else null,
                        onDeleteRequest = if (entry is FrameEntry.Custom) {
                            { deleteTarget = entry }
                        } else null,
                        loadBitmap      = {
                            if (entry is FrameEntry.Custom) loadCustomBitmap(entry.filename)
                            else null
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    // ── Add dialog ────────────────────────────────────────────────────────────
    if (showAddDialog && pendingUri != null) {
        var name by remember { mutableStateOf("") }
        // Reject a name that maps to an already-existing custom frame.
        val isDuplicate = sanitizeFrameName(name.trim()).let { s ->
            s.isNotEmpty() && entries.any { it is FrameEntry.Custom && it.filename == s }
        }
        AlertDialog(
            onDismissRequest = { showAddDialog = false; pendingUri = null },
            icon    = { Icon(Icons.Outlined.PhotoFilter, contentDescription = null) },
            title   = { Text(stringResource(R.string.frame_manager_add_title)) },
            text    = {
                OutlinedTextField(
                    value          = name,
                    onValueChange  = { name = it },
                    label          = { Text(stringResource(R.string.frame_manager_name_label)) },
                    singleLine     = true,
                    isError        = isDuplicate,
                    supportingText = if (isDuplicate) {
                        { Text(stringResource(R.string.frame_manager_name_exists)) }
                    } else null,
                    modifier       = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank() && !isDuplicate) {
                            onAddCustom(pendingUri!!, name.trim())
                            showAddDialog = false; pendingUri = null
                        }
                    },
                    enabled = name.isNotBlank() && !isDuplicate
                ) { Text(stringResource(R.string.frame_manager_add_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; pendingUri = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ── Rename dialog ─────────────────────────────────────────────────────────
    renameTarget?.let { entry ->
        var name by remember(entry) { mutableStateOf(entry.filename) }
        // Reject a name that maps to a *different* existing custom frame
        // (keeping the entry's own name is fine — that's a no-op rename).
        val isDuplicate = sanitizeFrameName(name.trim()).let { s ->
            s.isNotEmpty() && s != entry.filename &&
                entries.any { it is FrameEntry.Custom && it.filename == s }
        }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            icon    = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            title   = { Text(stringResource(R.string.frame_manager_rename_title)) },
            text    = {
                OutlinedTextField(
                    value          = name,
                    onValueChange  = { name = it },
                    label          = { Text(stringResource(R.string.frame_manager_name_label)) },
                    singleLine     = true,
                    isError        = isDuplicate,
                    supportingText = if (isDuplicate) {
                        { Text(stringResource(R.string.frame_manager_name_exists)) }
                    } else null,
                    modifier       = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank() && !isDuplicate) {
                            onRename(entry.filename, name.trim())
                            renameTarget = null
                        }
                    },
                    enabled = name.isNotBlank() && !isDuplicate
                ) { Text(stringResource(R.string.frame_manager_rename_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ── Delete confirmation ───────────────────────────────────────────────────
    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon    = {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title   = { Text(stringResource(R.string.frame_manager_delete_title)) },
            text    = { Text(stringResource(R.string.frame_manager_delete_body, entry.filename)) },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(entry.filename); deleteTarget = null },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.frame_manager_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Single list item – no drag gesture here; the LazyColumn handles it above.
// ---------------------------------------------------------------------------

@Composable
private fun FrameListItem(
    entry: FrameEntry,
    isDragging: Boolean,
    dragOffsetY: Float,
    onSetVisible: (Boolean) -> Unit,
    onRenameRequest: (() -> Unit)?,
    onDeleteRequest: (() -> Unit)?,
    loadBitmap: () -> Bitmap?,
    modifier: Modifier = Modifier
) {
    val label = when (entry) {
        is FrameEntry.BuiltIn -> stringResource(entry.frameType.labelRes)
        is FrameEntry.Custom  -> entry.filename
    }

    val thumbnail: Bitmap? = when (entry) {
        is FrameEntry.BuiltIn -> remember(entry.frameType) {
            val placeholder = Bitmap.createBitmap(40, 60, android.graphics.Bitmap.Config.ARGB_8888)
            composeFrameThumbnail(placeholder, entry.frameType)
        }
        is FrameEntry.Custom -> remember(entry.filename) { loadBitmap() }
    }

    var menuExpanded by remember { mutableStateOf(false) }
    val rowAlpha = if (entry.visible) 1f else 0.38f

    ListItem(
        leadingContent = {
            Switch(
                checked         = entry.visible,
                onCheckedChange = onSetVisible
            )
        },
        headlineContent = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.alpha(rowAlpha)
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap             = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .size(width = 28.dp, height = 40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(4.dp)
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 28.dp, height = 40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                Text(
                    text     = label,
                    style    = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = rowAlpha)
                )
            }
        },
        supportingContent = {
            Text(
                text  = if (entry is FrameEntry.BuiltIn)
                    stringResource(R.string.frame_manager_type_builtin)
                else
                    stringResource(R.string.frame_manager_type_custom),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = rowAlpha),
                modifier = Modifier.padding(start = 40.dp)
            )
        },
        trailingContent = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier              = Modifier.padding(end = 4.dp)
            ) {
                // Overflow menu – custom frames only
                if (onRenameRequest != null || onDeleteRequest != null) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded         = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            onRenameRequest?.let {
                                DropdownMenuItem(
                                    text        = { Text(stringResource(R.string.frame_manager_rename_title)) },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                    onClick     = { menuExpanded = false; it() }
                                )
                            }
                            onDeleteRequest?.let {
                                DropdownMenuItem(
                                    text        = {
                                        Text(
                                            stringResource(R.string.frame_manager_delete_confirm),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = { menuExpanded = false; it() }
                                )
                            }
                        }
                    }
                }

                // Drag handle – visual affordance, gesture is on the LazyColumn
                Icon(
                    imageVector        = Icons.Outlined.DragHandle,
                    contentDescription = stringResource(R.string.frame_manager_drag_handle),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier
                        .padding(end = 8.dp)
                        .size(24.dp)
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isDragging)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY    = dragOffsetY
                shadowElevation = if (isDragging) 8f else 0f
            }
            .zIndex(if (isDragging) 1f else 0f)
    )
}