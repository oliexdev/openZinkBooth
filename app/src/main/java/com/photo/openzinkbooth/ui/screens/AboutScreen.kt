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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.outlined.*
import android.graphics.BitmapFactory
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photo.openzinkbooth.BuildConfig
import com.photo.openzinkbooth.R
import com.photo.openzinkbooth.core.utils.LogManager
import com.photo.openzinkbooth.ui.components.ScreenHeader
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// AboutScreen – app info, project links, debug tools (DEBUG builds only)
// ---------------------------------------------------------------------------

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    lastPrintBitmap: Bitmap? = null,
    debugDryRun: Boolean = false,
    onToggleDebugDryRun: () -> Unit = {},
    onTestPrint: (Bitmap) -> Unit = {},
    onLoadTestImage: (String) -> Unit = {},
    printWidth: Int = 640,
    printHeight: Int = 1002,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        ScreenHeader(title = stringResource(R.string.nav_about), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // ── App identity ─────────────────────────────────────────────────
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier         = Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(0xFF156080)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter            = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = stringResource(R.string.app_name),
                        modifier           = Modifier.size(128.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text       = stringResource(R.string.app_name),
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text  = stringResource(
                        R.string.about_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Project info ─────────────────────────────────────────────────
            AboutSectionTitle(stringResource(R.string.about_project_title))

            AboutListItem(
                icon       = Icons.Outlined.Person,
                headline   = "olie.xdeveloper",
                supporting = stringResource(R.string.about_maintainer_label)
            )
            AboutListItem(
                icon       = Icons.Outlined.Home,
                headline   = "openZinkBooth",
                supporting = stringResource(R.string.about_github_label),
                url        = "https://github.com/oliexdev/openZinkBooth",
                onOpenUrl  = { url ->
                    try { uriHandler.openUri(url) }
                    catch (e: Exception) { LogManager.e("AboutScreen", "Cannot open URL: $url", e) }
                }
            )
            AboutListItem(
                icon       = Icons.Outlined.Gavel,
                headline   = "GNU GPL v3.0",
                supporting = stringResource(R.string.about_license_label),
                url        = "https://www.gnu.org/licenses/gpl-3.0.html",
                onOpenUrl  = { url ->
                    try { uriHandler.openUri(url) }
                    catch (e: Exception) { LogManager.e("AboutScreen", "Cannot open URL: $url", e) }
                }
            )

            // ── Pixel Art credits ─────────────────────────────────────────────
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))
            AboutSectionTitle(text = stringResource(R.string.about_pixelart_credits))

            AboutListItem(
                icon       = Icons.Outlined.Palette,
                headline   = "LPC Sprites",
                supporting = "Universal LPC Spritesheet Character Generator — CC-BY-SA 3.0",
                url        = "https://opengameart.org/content/lpc-collection",
                onOpenUrl  = { url ->
                    try { uriHandler.openUri(url) }
                    catch (e: Exception) { LogManager.e("AboutScreen", "Cannot open URL: $url", e) }
                }
            )

            // Collapsible LPC authors
            var authorsExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { authorsExpanded = !authorsExpanded }
                    .padding(start = 56.dp, end = 16.dp, top = 0.dp, bottom = 4.dp)
                    .offset(y = (-8).dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text     = stringResource(if (authorsExpanded) R.string.pixelart_credits_hide_authors else R.string.pixelart_credits_show_authors),
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector        = if (authorsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                )
            }
            if (authorsExpanded) {
                Text(
                    text = "Johannes Sjölund (wulax), Michael Whitlock (bigbeargames), " +
                            "Matthew Krohn (makrohn), Nila122, David Conway Jr. (JaidynReiman), " +
                            "Carlo Enrico Victoria (Nemisys), Thane Brimhall (pennomi), laetissima, " +
                            "bluecarrot16, Luke Mehl, Benjamin K. Smith (BenCreating), MuffinElZangano, " +
                            "Durrani, kheftel, Stephen Challener (Redshrike), William.Thompsonj, " +
                            "Marcel van de Steeg (MadMarcel), TheraHedwig, Evert, Pierre Vigier (pvigier), " +
                            "Eliza Wyatt (ElizaWy), Sander Frenken (castelonia), dalonedrau, " +
                            "Lanea Zimmerman (Sharm), Manuel Riecke (MrBeast), Barbara Riviera, " +
                            "Joe White, Mandi Paugh, Shaun Williams, Daniel Eddeland (daneeklu), " +
                            "Emilio J. Sanchez-Sierra, drjamgo, gr3yh47, tskaufma, Fabzy, Yamilian, " +
                            "Skorpio, Tuomo Untinen (reemax), Tracy, thecilekli, LordNeo, " +
                            "Stafford McIntyre, PlatForge project, DCSS authors, DarkwallLKE, " +
                            "Charles Sanchez (CharlesGabriel), Radomir Dopieralski, macmanmatty, " +
                            "Cobra Hubbard (BlueVortexGames), Inboxninja, kcilds/Rocetti/Eredah, " +
                            "Napsio (Vitruvian Studio), The Foreman, AntumDeluge",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            AboutListItem(
                icon       = Icons.Outlined.Palette,
                headline   = "Pixel Art Backgrounds",
                supporting = "freepixel.art — free for personal & commercial use, no attribution required",
                url        = "https://freepixel.art/browse/backgrounds",
                onOpenUrl  = { url ->
                    try { uriHandler.openUri(url) }
                    catch (e: Exception) { LogManager.e("AboutScreen", "Cannot open URL: $url", e) }
                }
            )


            // ── Debug tools (DEBUG builds only) ──────────────────────────────
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))

                AboutSectionTitle(
                    text  = stringResource(R.string.about_debug_title),
                    color = MaterialTheme.colorScheme.error
                )

                TestImagePicker(onLoadTestImage = onLoadTestImage)


                // Dry-run toggle – skips actual printing, stores bitmap for preview
                ListItem(
                    headlineContent   = { Text("Dry Run (kein Druck)") },
                    supportingContent = { Text("Druckt nicht, zeigt nur das Bitmap unten") },
                    leadingContent    = {
                        Icon(Icons.Outlined.Print, null,
                            tint = MaterialTheme.colorScheme.error)
                    },
                    trailingContent   = {
                        Switch(
                            checked         = debugDryRun,
                            onCheckedChange = { onToggleDebugDryRun() }
                        )
                    }
                )

                // Last sent print bitmap – tap to inspect
                lastPrintBitmap?.let { bmp ->
                    Text(
                        text     = "Last sent to printer: ${bmp.width}×${bmp.height}px",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.foundation.Image(
                        bitmap             = bmp.asImageBitmap(),
                        contentDescription = "Last print bitmap",
                        contentScale       = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier           = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                            .border(
                                2.dp,
                                MaterialTheme.colorScheme.error,
                                androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                            )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                DebugTools()

                Spacer(modifier = Modifier.height(8.dp))

                // Test print button – generates a calibration image
                Button(
                    onClick  = {
                        val testBitmap = createTestImage(printWidth, printHeight)
                        onTestPrint(testBitmap)
                    },
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor   = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Outlined.Straighten, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Print (Kalibrierung)")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Debug tools section
// ---------------------------------------------------------------------------

@Composable
private fun DebugTools() {
    val logFile = LogManager.getLogFile()
    val logEntryCount by produceState(initialValue = 0) {
        value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            LogManager.getActualLogEntryCount()
        }
    }

    val scope   = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val success = LogManager.exportLogToUri(context, uri)
                if (success) LogManager.i("AboutScreen", "Log export successful")
                else         LogManager.e("AboutScreen", "Log export failed")
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ListItem(
            headlineContent   = { Text(stringResource(R.string.about_ble_log_lines, logEntryCount)) },
            supportingContent = {
                Text(
                    logFile?.name ?: stringResource(R.string.about_log_no_file),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            leadingContent = {
                Icon(Icons.Outlined.Terminal, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error)
            }
        )
        OutlinedButton(
            onClick  = { exportLauncher.launch("openzinkbooth_ble_log.txt") },
            enabled  = logFile != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            colors   = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Outlined.FileUpload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.about_export_log))
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable sub-components
// ---------------------------------------------------------------------------


// ---------------------------------------------------------------------------
// Test image picker – thumbnail row + load button
// ---------------------------------------------------------------------------

@Composable
private fun TestImagePicker(onLoadTestImage: (String) -> Unit) {
    val context  = androidx.compose.ui.platform.LocalContext.current
    val images   = listOf(
        "images/test_family_1.jpg",
        "images/test_person_1.jpg",
        "images/test_person_2.jpg",
        "images/test_person_3.jpg",
        "images/test_person_4.jpg",
    )
    var selected by remember { mutableStateOf(images.first()) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {

        // Thumbnail row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(images) { filename ->
                val bmp = remember(filename) {
                    runCatching {
                        context.assets.open(filename)
                            .use { BitmapFactory.decodeStream(it) }
                    }.getOrNull()
                }
                val isSelected = filename == selected
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { selected = filename },
                ) {
                    if (bmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap           = bmp.asImageBitmap(),
                            contentDescription = filename,
                            contentScale     = ContentScale.Crop,
                            modifier         = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("?", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Load button
        OutlinedButton(
            onClick  = { onLoadTestImage(selected) },
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Outlined.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Load: $selected", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AboutSectionTitle(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.labelLarge,
        color      = color,
        fontWeight = FontWeight.Bold,
        modifier   = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun AboutListItem(
    icon: ImageVector,
    headline: String,
    supporting: String? = null,
    url: String? = null,
    onOpenUrl: ((String) -> Unit)? = null
) {
    ListItem(
        headlineContent   = { Text(headline, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = supporting?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        leadingContent    = { Icon(icon, contentDescription = null) },
        trailingContent   = if (url != null) {
            {
                Icon(
                    Icons.AutoMirrored.Filled.Launch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else null,
        modifier = if (url != null && onOpenUrl != null)
            Modifier.clickable { onOpenUrl(url) }
        else
            Modifier,
        colors = ListItemDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        )
    )
}

// ---------------------------------------------------------------------------
// Calibration test image generator
//
// Produces a bitmap with measurable elements to verify printer output:
// - 8mm thick red border outside (≈100px at 313dpi) — easy to measure how
//   much (if any) is cut off on each side after printing
// - 5mm tick marks with mm labels along all four edges (inside the red border)
// - L-shaped corner markers in all four corners
// - Diagonals from corner to corner (detect stretching/skew)
// - Center crosshair with 5mm circle (verify centering: should land at
//   25mm × 38mm on the 50×76mm Zink paper)
// - Resolution info text in the center
// ---------------------------------------------------------------------------
fun createTestImage(width: Int, height: Int): Bitmap {
    // Zink paper is 50mm × 76mm. Common print resolution is 313dpi for
    // 640×1002 paper, but we calculate dpi from the actual bitmap size:
    val dpiX = width  / (50f / 25.4f)   // px per inch (width direction)
    val dpiY = height / (76f / 25.4f)   // px per inch (height direction)
    val pxPerMm = (dpiX + dpiY) / 2f / 25.4f  // average px per mm

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val borderPx = (8f * pxPerMm).toInt()  // 8mm red border

    // ── Red outer border (8mm wide) ──────────────────────────────────────
    val redPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.RED
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = false
    }
    canvas.drawRect(0f, 0f, width.toFloat(), borderPx.toFloat(), redPaint)
    canvas.drawRect(0f, height - borderPx.toFloat(), width.toFloat(), height.toFloat(), redPaint)
    canvas.drawRect(0f, 0f, borderPx.toFloat(), height.toFloat(), redPaint)
    canvas.drawRect(width - borderPx.toFloat(), 0f, width.toFloat(), height.toFloat(), redPaint)

    // ── Black tick marks every 5mm inside the red border ─────────────────
    val tickPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 1.5f
        isAntiAlias = false
    }
    val labelPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 2f * pxPerMm   // 6mm tall labels for readability
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    val tickLength = (3f * pxPerMm).toInt()
    val widthMm  = (width  / pxPerMm).toInt()
    val heightMm = (height / pxPerMm).toInt()

    // Top + bottom tick marks (with mm labels)
    var mm = 0
    while (mm <= widthMm) {
        val x = mm * pxPerMm
        canvas.drawLine(x, borderPx.toFloat(), x, (borderPx + tickLength).toFloat(), tickPaint)
        canvas.drawLine(x, (height - borderPx).toFloat(), x, (height - borderPx - tickLength).toFloat(), tickPaint)
        if (mm % 10 == 0 && mm > 0 && mm < widthMm) {
            canvas.drawText(mm.toString(), x + 2f, borderPx + tickLength + labelPaint.textSize, labelPaint)
            canvas.drawText(mm.toString(), x + 2f, height - borderPx - tickLength - 4f, labelPaint)
        }
        mm += 5
    }

    // Left + right tick marks (with mm labels)
    mm = 0
    while (mm <= heightMm) {
        val y = mm * pxPerMm
        canvas.drawLine(borderPx.toFloat(), y, (borderPx + tickLength).toFloat(), y, tickPaint)
        canvas.drawLine((width - borderPx).toFloat(), y, (width - borderPx - tickLength).toFloat(), y, tickPaint)
        if (mm % 10 == 0 && mm > 0 && mm < heightMm) {
            canvas.drawText(mm.toString(), borderPx + tickLength + 4f, y + labelPaint.textSize / 2, labelPaint)
            canvas.drawText(mm.toString(), width - borderPx - tickLength - labelPaint.measureText(mm.toString()) - 4f, y + labelPaint.textSize / 2, labelPaint)
        }
        mm += 5
    }

    // ── L-shaped corner markers (10mm long, 1.5mm thick) ─────────────────
    val cornerLen = (10f * pxPerMm)
    val cornerThick = (1.5f * pxPerMm)
    val cornerPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = false
    }
    val inset = borderPx.toFloat() + (5f * pxPerMm)  // 5mm inside the red border

    // Top-left
    canvas.drawRect(inset, inset, inset + cornerLen, inset + cornerThick, cornerPaint)
    canvas.drawRect(inset, inset, inset + cornerThick, inset + cornerLen, cornerPaint)
    // Top-right
    canvas.drawRect(width - inset - cornerLen, inset, width - inset, inset + cornerThick, cornerPaint)
    canvas.drawRect(width - inset - cornerThick, inset, width - inset, inset + cornerLen, cornerPaint)
    // Bottom-left
    canvas.drawRect(inset, height - inset - cornerThick, inset + cornerLen, height - inset, cornerPaint)
    canvas.drawRect(inset, height - inset - cornerLen, inset + cornerThick, height - inset, cornerPaint)
    // Bottom-right
    canvas.drawRect(width - inset - cornerLen, height - inset - cornerThick, width - inset, height - inset, cornerPaint)
    canvas.drawRect(width - inset - cornerThick, height - inset - cornerLen, width - inset, height - inset, cornerPaint)

    // ── Diagonals (thin gray lines) ──────────────────────────────────────
    val diagPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.LTGRAY
        strokeWidth = 1f
        isAntiAlias = true
    }
    canvas.drawLine(borderPx.toFloat(), borderPx.toFloat(),
        (width - borderPx).toFloat(), (height - borderPx).toFloat(), diagPaint)
    canvas.drawLine((width - borderPx).toFloat(), borderPx.toFloat(),
        borderPx.toFloat(), (height - borderPx).toFloat(), diagPaint)

    // ── Center crosshair (1.5mm thick) with 5mm circle ───────────────────
    val cx = width / 2f
    val cy = height / 2f
    val crossLen = (8f * pxPerMm)
    val crossPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 1.5f * pxPerMm
        style = android.graphics.Paint.Style.STROKE
        isAntiAlias = false
    }
    canvas.drawLine(cx - crossLen, cy, cx + crossLen, cy, crossPaint)
    canvas.drawLine(cx, cy - crossLen, cx, cy + crossLen, crossPaint)
    crossPaint.strokeWidth = 1f
    canvas.drawCircle(cx, cy, 5f * pxPerMm, crossPaint)

    // ── Orientation labels OBEN / UNTEN / LINKS / RECHTS ─────────────────
    val orientPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 4f * pxPerMm
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText("TOP", cx, borderPx + (18f * pxPerMm), orientPaint)
    canvas.drawText("BOTTOM", cx, height - borderPx - (12f * pxPerMm), orientPaint)

    canvas.save()
    canvas.rotate(-90f, borderPx + (12f * pxPerMm), cy)
    canvas.drawText("LEFT", borderPx + (12f * pxPerMm), cy, orientPaint)
    canvas.restore()

    canvas.save()
    canvas.rotate(90f, width - borderPx - (12f * pxPerMm), cy)
    canvas.drawText("RIGHT", width - borderPx - (12f * pxPerMm), cy, orientPaint)
    canvas.restore()

    // ── Info text in center ──────────────────────────────────────────────
    val infoPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 2f * pxPerMm
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText("${width} × ${height} px", cx, cy + (12f * pxPerMm), infoPaint)
    canvas.drawText("50 × 76 mm", cx, cy + (20f * pxPerMm), infoPaint)
    canvas.drawText("%.0f dpi".format((dpiX + dpiY) / 2), cx, cy + (28f * pxPerMm), infoPaint)

    return bitmap
}