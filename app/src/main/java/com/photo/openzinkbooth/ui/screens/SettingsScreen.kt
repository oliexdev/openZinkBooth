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

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.photo.openzinkbooth.R
import com.photo.openzinkbooth.ui.viewmodel.RemoteShutterKey
import com.photo.openzinkbooth.ui.viewmodel.ZinkUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: ZinkUiState,
    onBack: () -> Unit,
    onNavigateToPrinter: () -> Unit,
    onNavigateToPrinterConfig: () -> Unit,
    onNavigateToFrameManager: () -> Unit,
    onToggleFrontCamera: (Boolean) -> Unit,
    onToggleFlash: (Boolean) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleShutterSound: (Boolean) -> Unit,
    onStorageUriSelected: (Uri?) -> Unit,
    onToggleRemoteShutter: (Boolean) -> Unit,
    onSetRemoteShutterKey: (RemoteShutterKey) -> Unit,
    onTogglePixelArtMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Persist read+write permission across app restarts; without this
            // the ContentResolver loses access to the folder after the process dies.
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        onStorageUriSelected(uri)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.camera_back_description))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            SettingsGroup(title = stringResource(R.string.settings_group_printer)) {
                PrinterSettingsRow(
                    printerName        = state.printerModelName.ifBlank {
                        stringResource(R.string.printer_no_printer_selected)
                    },
                    connected          = state.printerConnected,
                    onNavigateToPrinter      = onNavigateToPrinter,
                    onNavigateToPrinterConfig = onNavigateToPrinterConfig
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_group_camera)) {
                SettingsToggleRow(
                    icon     = Icons.Outlined.CameraFront,
                    label    = stringResource(R.string.settings_front_camera),
                    checked  = state.useFrontCamera,
                    onToggle = onToggleFrontCamera
                )
                // Flash: always show for back camera.
                // For front camera only show on API 34+ (FLASH_MODE_SCREEN support).
                val showFlashOption = !state.useFrontCamera ||
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                if (showFlashOption) {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    SettingsToggleRow(
                        icon     = if (state.useFrontCamera) Icons.Outlined.WbSunny
                        else                      Icons.Outlined.FlashOn,
                        label    = stringResource(
                            if (state.useFrontCamera) R.string.settings_flash_screen
                            else                      R.string.settings_flash
                        ),
                        checked  = state.flashEnabled,
                        onToggle = onToggleFlash
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                SettingsToggleRow(
                    icon     = Icons.Outlined.VolumeUp,
                    label    = stringResource(R.string.settings_shutter_sound),
                    checked  = state.shutterSoundEnabled,
                    onToggle = onToggleShutterSound
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                SettingsToggleRow(
                    icon     = Icons.Outlined.SettingsRemote,
                    label    = stringResource(R.string.settings_remote_shutter_enabled),
                    checked  = state.remoteShutterEnabled,
                    onToggle = onToggleRemoteShutter
                )
                if (state.remoteShutterEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    RemoteShutterKeyRow(
                        selected = state.remoteShutterKey,
                        onSelect = onSetRemoteShutterKey
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.settings_group_appearance)) {
                SettingsToggleRow(
                    icon     = Icons.Outlined.Palette,
                    label    = stringResource(R.string.settings_dynamic_color),
                    checked  = state.dynamicColor,
                    onToggle = onToggleDynamicColor
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                SettingsActionRow(
                    icon    = Icons.Outlined.PhotoFilter,
                    label   = stringResource(R.string.settings_frames),
                    onClick = onNavigateToFrameManager
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_group_app)) {
                StorageLocationRow(
                    storageUri = state.storageUri,
                    onPick     = { folderPicker.launch(state.storageUri) },
                    onClear    = { onStorageUriSelected(null) }
                )
            }

            // ── Pixel Art Mode ────────────────────────────────────────────────────
            SettingsGroup(title = stringResource(R.string.settings_pixelart_group)) {
                PixelArtSettingsRow(
                    checked  = state.pixelArtMode,
                    onToggle = onTogglePixelArtMode,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text     = title,
            style    = MaterialTheme.typography.labelMedium,
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun PixelArtSettingsRow(
    checked:  Boolean,
    onToggle: (Boolean) -> Unit,
) {
    // Star positions — stable across recompositions
    data class Star(val x: Float, val y: Float, val size: Float)
    val stars = remember {
        (0 until 10).map {
            Star(
                x    = (5..95).random() / 100f,
                y    = (5..95).random() / 100f,
                size = (5..10).random().toFloat(),
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "pixelArtStars")
    val time by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = androidx.compose.animation.core.tween(4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "starTime",
    )
    val starAlphas = stars.mapIndexed { i, _ ->
        val t = ((time + i / stars.size.toFloat()) % 1f)
        if (t < 0.5f) t * 2f else (1f - t) * 2f
    }

    val goldColor = if (isSystemInDarkTheme()) Color(0xFFFFD700)   // bright gold in dark mode
    else                       Color(0xFFB8860B)   // dark goldenrod in light mode

    Box {
        SettingsToggleRow(
            icon     = Icons.Outlined.AutoAwesome,
            label    = stringResource(R.string.settings_pixelart_mode),
            checked  = checked,
            onToggle = onToggle,
        )
        // Stars always animated
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 56.dp), // avoid overlap with switch
        ) {
            stars.forEachIndexed { i, star ->
                val alpha = starAlphas[i]
                if (alpha > 0.05f) {
                    val cx = size.width  * star.x
                    val cy = size.height * star.y
                    val s  = star.size * alpha
                    drawLine(goldColor.copy(alpha = alpha),
                        start = Offset(cx, cy - s), end = Offset(cx, cy + s),
                        strokeWidth = s * 0.3f)
                    drawLine(goldColor.copy(alpha = alpha),
                        start = Offset(cx - s, cy), end = Offset(cx + s, cy),
                        strokeWidth = s * 0.3f)
                    val d = s * 0.5f
                    drawLine(goldColor.copy(alpha = alpha * 0.5f),
                        start = Offset(cx - d, cy - d), end = Offset(cx + d, cy + d),
                        strokeWidth = s * 0.2f)
                    drawLine(goldColor.copy(alpha = alpha * 0.5f),
                        start = Offset(cx + d, cy - d), end = Offset(cx - d, cy + d),
                        strokeWidth = s * 0.2f)
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    label: String,
    sublabel: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsIcon(icon, enabled)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (sublabel != null) {
                Text(
                    text  = sublabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                        .let { if (enabled) it else it.copy(alpha = 0.38f) }
                )
            }
        }
        Switch(
            checked         = checked,
            onCheckedChange = onToggle,
            enabled         = enabled
        )
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showDot: Boolean = false,
    dotColor: Color = MaterialTheme.colorScheme.tertiary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsIcon(icon)
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier.size(7.dp).clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text  = value,
                style = MaterialTheme.typography.labelMedium,
                color = valueColor
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    label: String,
    sublabel: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsIcon(icon, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = label,
                style = MaterialTheme.typography.bodyLarge,
                color = tint
            )
            if (sublabel != null) {
                Text(
                    text  = sublabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector        = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsIcon(
    icon: ImageVector,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Icon(
        imageVector        = icon,
        contentDescription = null,
        tint               = if (enabled) tint else tint.copy(alpha = 0.38f),
        modifier           = Modifier.size(24.dp)
    )
}

// ---------------------------------------------------------------------------
// Storage location row – shows the configured path with an X to clear it,
// or a placeholder when no path is set.
// ---------------------------------------------------------------------------

@Composable
private fun StorageLocationRow(
    storageUri: android.net.Uri?,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsIcon(Icons.Outlined.FolderOpen)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = stringResource(R.string.settings_storage_location),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text  = storageUri
                    ?.lastPathSegment?.substringAfterLast(':')
                    ?: stringResource(R.string.settings_storage_none),
                style = MaterialTheme.typography.bodySmall,
                color = if (storageUri != null)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (storageUri != null) {
            // X icon to clear the storage location
            IconButton(onClick = onClear) {
                Icon(
                    imageVector        = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.settings_storage_clear),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(18.dp)
                )
            }
        } else {
            Icon(
                imageVector        = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(18.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Printer row – left area opens PrinterScreen, gear icon opens PrinterConfigScreen.
// Only shows the gear when connected (config only available via RFCOMM).
// ---------------------------------------------------------------------------

@Composable
private fun PrinterSettingsRow(
    printerName: String,
    connected: Boolean,
    onNavigateToPrinter: () -> Unit,
    onNavigateToPrinterConfig: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigateToPrinter)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsIcon(
            icon = Icons.Outlined.Print,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = printerName,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text  = if (connected)
                    stringResource(R.string.settings_printer_connected)
                else
                    stringResource(R.string.settings_printer_disconnected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Gear icon – only visible when connected (RFCOMM required for config)
        if (connected) {
            IconButton(onClick = onNavigateToPrinterConfig) {
                Icon(
                    imageVector        = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.printer_config_title),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(22.dp)
                )
            }
        } else {
            Icon(
                imageVector        = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(18.dp)
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteShutterKeyRow(
    selected: RemoteShutterKey,
    onSelect: (RemoteShutterKey) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val keys = RemoteShutterKey.entries

    ListItem(
        leadingContent = {
            Icon(
                Icons.Outlined.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        headlineContent = {
            Text(
                stringResource(R.string.settings_remote_key_label),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(
                        text  = stringResource(selected.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Outlined.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint     = MaterialTheme.colorScheme.primary
                    )
                }
                DropdownMenu(
                    expanded         = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    keys.forEach { key ->
                        DropdownMenuItem(
                            text         = { Text(stringResource(key.labelRes)) },
                            onClick      = { onSelect(key); expanded = false },
                            trailingIcon = if (key == selected) {
                                { Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary) }
                            } else null
                        )
                    }
                }
            }
        }
    )
}