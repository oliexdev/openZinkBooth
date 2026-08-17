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
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photo.openzinkbooth.R
import com.photo.openzinkbooth.ui.components.ZinkActionButton
import com.photo.openzinkbooth.core.pixelart.PixelArtAnalyzer
import com.photo.openzinkbooth.core.pixelart.sprite.EquipmentRandomizer
import com.photo.openzinkbooth.core.pixelart.stats.StatCalculator
import com.photo.openzinkbooth.ui.viewmodel.PixelArtAnalysisState
import com.photo.openzinkbooth.ui.viewmodel.ZinkUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelArtPreviewScreen(
    state:               ZinkUiState,
    onBack:              () -> Unit,
    onSelectEquipment:   (EquipmentRandomizer.EquipmentSet) -> Unit,
    onRandomizeEquipment: () -> Unit,
    onPrint:             (Bitmap) -> Unit,
    printerReady:        Boolean,
    windowSizeClass:     androidx.compose.material3.windowsizeclass.WindowSizeClass? = null,
    modifier:            Modifier = Modifier,
) {
    val result    = state.pixelArtResult
    val activeSet = state.pixelArtEquipmentSet
    val context   = androidx.compose.ui.platform.LocalContext.current

    val printBitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = result,
        key2 = state.pixelArtEquipment,
        key3 = state.pixelArtEquipmentSet,
    ) {
        value = null
        if (result == null) return@produceState
        value = withContext(Dispatchers.Default) {
            buildPixelArtCard(result, state, context)
        }
    }

    val cardAlpha by animateFloatAsState(
        targetValue   = if (printBitmap != null) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label         = "cardFade",
    )
    val blinkTransition = rememberInfiniteTransition(label = "blink")
    val blinkAlpha by blinkTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.85f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "blink",
    )

    val configuration  = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape    = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val widthClass     = windowSizeClass?.widthSizeClass  ?: androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Compact
    val heightClass    = windowSizeClass?.heightSizeClass ?: androidx.compose.material3.windowsizeclass.WindowHeightSizeClass.Medium
    val isPhoneLandscape = isLandscape && heightClass == androidx.compose.material3.windowsizeclass.WindowHeightSizeClass.Compact

    val cardReady = printBitmap != null
    val hasResult = result != null && result.allFeatures.isNotEmpty()
    val bmp       = printBitmap

    // Shared preview card composable
    @Composable
    fun PreviewCard(modifier2: Modifier) {
        Box(
            modifier         = modifier2
                .clip(RoundedCornerShape(8.dp))
                .aspectRatio(state.printerPrintWidth.toFloat() / state.printerPrintHeight.toFloat()),
            contentAlignment = Alignment.Center,
        ) {
            if (cardReady) {
                Image(
                    bitmap             = bmp!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.pixelart_card_description),
                    contentScale       = ContentScale.Fit,
                    filterQuality      = FilterQuality.None,
                    modifier           = Modifier.fillMaxSize().graphicsLayer { alpha = cardAlpha },
                )
            } else {
                LoadingCard(blinkAlpha = blinkAlpha, progressText = state.pixelArtProgressText)
            }
        }
    }

    // Shared controls composable
    @Composable
    fun Controls(modifier2: Modifier = Modifier) {
        Column(modifier = modifier2, horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            EquipmentSetSelector(
                activeSet         = if (hasResult) activeSet else EquipmentRandomizer.EquipmentSet.NONE,
                onSelectEquipment = if (hasResult) onSelectEquipment else { _ -> },
                onRandomize       = if (hasResult) onRandomizeEquipment else ({  }),
                modifier          = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = if (hasResult) 1f else 0.4f },
            )
            Spacer(Modifier.height(12.dp))
            ZinkActionButton(
                icon               = Icons.Outlined.Print,
                label              = stringResource(R.string.pixelart_print),
                contentDescription = stringResource(R.string.pixelart_print),
                onClick            = { if (bmp != null) onPrint(bmp) },
                enabled            = printerReady && cardReady,
                modifier           = Modifier.fillMaxWidth(),
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("✨ " + stringResource(R.string.pixelart_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.pixelart_close_description),
                            tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { padding ->
        val contentMod = Modifier.padding(padding).fillMaxSize()

        if (isPhoneLandscape) {
            // Phone landscape: card fills height, controls beside it
            Row(modifier = contentMod.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PreviewCard(modifier2 = Modifier
                    .fillMaxHeight()
                    .weight(0.5f)
                    .aspectRatio(state.printerPrintWidth.toFloat() / state.printerPrintHeight.toFloat(), matchHeightConstraintsFirst = true))
                Controls(modifier2 = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp))
            }
        } else if (isLandscape) {
            // Tablet landscape: card fills height, controls beside
            Row(modifier = contentMod.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically) {
                PreviewCard(modifier2 = Modifier
                    .fillMaxHeight(0.92f)
                    .weight(0.45f)
                    .aspectRatio(state.printerPrintWidth.toFloat() / state.printerPrintHeight.toFloat(), matchHeightConstraintsFirst = true))
                Controls(modifier2 = Modifier.weight(0.55f).padding(vertical = 16.dp))
            }
        } else {
            // Portrait: card takes remaining space, controls pinned below
            val hPad = when (widthClass) {
                androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Expanded -> 64.dp
                androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Medium   -> 32.dp
                else -> 16.dp
            }
            Column(
                modifier            = contentMod.padding(horizontal = hPad),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Card takes all available space not used by controls
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(state.printerPrintWidth.toFloat() / state.printerPrintHeight.toFloat(), matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cardReady) {
                            Image(bitmap = bmp!!.asImageBitmap(), contentDescription = stringResource(R.string.pixelart_card_description),
                                contentScale = ContentScale.Fit, filterQuality = FilterQuality.None,
                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = cardAlpha })
                        } else {
                            LoadingCard(blinkAlpha = blinkAlpha, progressText = state.pixelArtProgressText)
                        }
                    }
                }
                Controls(modifier2 = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ── Loading card skeleton ─────────────────────────────────────────────────────

@Composable
private fun LoadingCard(blinkAlpha: Float, progressText: String = "Initialising…") {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val W = size.width; val H = size.height
            val BORDER     = 0f
            val STATS_H    = H * 0.228f
            val PANEL_TOP  = H - STATS_H - BORDER
            val TOP_BAND_H = H * 0.068f
            val INNER_L    = BORDER + W * 0.025f
            val INNER_R    = W - BORDER - W * 0.025f
            val INNER_T    = BORDER + W * 0.022f
            val INNER_B    = H - BORDER - W * 0.022f

            // ── SNES border ────────────────────────────────────────────────
            val snesGrey  = Color(0xFF1A1A2E)
            val snesLight = Color(0xFF323259)
            val snesDark  = Color(0xFF0A0A19)
            drawRect(snesGrey, topLeft = Offset(BORDER, BORDER),
                size = androidx.compose.ui.geometry.Size(W - 2*BORDER, H - 2*BORDER))
            drawLine(snesLight, Offset(BORDER, BORDER), Offset(W-BORDER, BORDER), 2f)
            drawLine(snesLight, Offset(BORDER, BORDER), Offset(BORDER, H-BORDER), 2f)
            drawLine(snesDark,  Offset(BORDER, H-BORDER), Offset(W-BORDER, H-BORDER), 2f)
            drawLine(snesDark,  Offset(W-BORDER, BORDER), Offset(W-BORDER, H-BORDER), 2f)
            drawRect(Color(0xFF3C4150), topLeft = Offset(INNER_L, INNER_T),
                size = androidx.compose.ui.geometry.Size(INNER_R-INNER_L, INNER_B-INNER_T),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
            drawRect(androidx.compose.ui.graphics.Color.White,
                topLeft = Offset(INNER_L+2f, INNER_T+2f),
                size = androidx.compose.ui.geometry.Size(INNER_R-INNER_L-4f, INNER_B-INNER_T-4f))

            // ── Dither helper ──────────────────────────────────────────────
            val d1 = Color(0xFF0D1222); val d2 = Color(0xFF12193C)
            fun dither(l: Float, t: Float, r: Float, b: Float, tile: Float = 6f) {
                var ty = t
                while (ty < b) {
                    var tx = l
                    while (tx < r) {
                        val even = ((((tx-l)/tile).toInt() + ((ty-t)/tile).toInt()) % 2 == 0)
                        drawRect(if (even) d1 else d2,
                            topLeft = Offset(tx, ty),
                            size = androidx.compose.ui.geometry.Size(minOf(tile, r-tx), minOf(tile, b-ty)))
                        tx += tile
                    }
                    ty += tile
                }
            }

            dither(INNER_L+2f, INNER_T+2f, INNER_R-2f, INNER_T+2f+TOP_BAND_H)
            dither(INNER_L+2f, PANEL_TOP, INNER_R-2f, INNER_B-2f)

            // ── Gold lines ─────────────────────────────────────────────────
            val gold = Color(0xFFC8AA32)
            drawLine(gold, Offset(INNER_L+2f, PANEL_TOP), Offset(INNER_R-2f, PANEL_TOP), 3f)
            drawLine(gold, Offset(INNER_L+2f, INNER_T+2f+TOP_BAND_H), Offset(INNER_R-2f, INNER_T+2f+TOP_BAND_H), 3f)

            // ── Blink dots — below the scene center (under progress text) ──
            val sceneTop = INNER_T + 2f + TOP_BAND_H
            val sceneBot = PANEL_TOP
            val sceneMidY = (sceneTop + sceneBot) / 2f
            val dotsY = sceneMidY + H * 0.10f   // further below center, under text
            val dotR  = W * 0.012f
            listOf(-1, 0, 1).forEach { idx ->
                val a = blinkAlpha * (1f - kotlin.math.abs(idx) * 0.3f)
                drawCircle(gold.copy(alpha = a.coerceIn(0f, 1f)),
                    radius = dotR, center = Offset(W/2f + idx * dotR * 3f, dotsY))
            }

            // ── Placeholder stat slots — pure blinkAlpha for true fade ─────
            val colW   = (INNER_R - INNER_L - 4f) / 2f
            val rowH   = STATS_H / 2f
            val iconSz = W * 0.048f
            val barW   = colW * 0.55f
            val barH   = W * 0.020f
            for (row in 0..1) {
                for (col in 0..1) {
                    val ox   = INNER_L + 2f + col * colW + colW * 0.06f
                    val midY = PANEL_TOP + row * rowH + rowH / 2f
                    drawCircle(Color(0xFF3A3A6A).copy(alpha = blinkAlpha),
                        radius = iconSz / 2f, center = Offset(ox + iconSz/2f, midY))
                    val bx = ox + iconSz + W * 0.02f
                    drawRect(Color(0xFF4A4A7A).copy(alpha = blinkAlpha * 0.7f),
                        topLeft = Offset(bx, midY - barH * 1.8f),
                        size = androidx.compose.ui.geometry.Size(barW * 0.5f, barH * 0.7f))
                    drawRect(Color(0xFF5A5A9A).copy(alpha = blinkAlpha),
                        topLeft = Offset(bx, midY - barH * 0.5f),
                        size = androidx.compose.ui.geometry.Size(barW, barH))
                }
            }
        }

        // ── Progress text — centered in scene, slightly above dots ────────
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text  = progressText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFFFFD700),
                    fontSize   = 13.sp,
                ),
                modifier = Modifier.padding(bottom = 60.dp),
            )
        }
    }
}

// ── Equipment set selector ────────────────────────────────────────────────────

@Composable
private fun EquipmentSetSelector(
    activeSet:          EquipmentRandomizer.EquipmentSet,
    onSelectEquipment:  (EquipmentRandomizer.EquipmentSet) -> Unit,
    onRandomize:        () -> Unit,
    modifier:           Modifier = Modifier,
) {
    val isActive = activeSet != EquipmentRandomizer.EquipmentSet.NONE

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(10.dp))
        MagicButton(
            isActive  = isActive,
            onClick   = {
                if (isActive) onSelectEquipment(EquipmentRandomizer.EquipmentSet.NONE)
                else          onRandomize()
            },
            modifier  = Modifier.fillMaxWidth(0.65f),
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MagicButton(
    isActive: Boolean,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    data class Star(val x: Float, val y: Float, val size: Float)
    // Fixed star positions (stable)
    val stars = remember {
        (0 until 12).map {
            Star(
                x    = (5..95).random() / 100f,
                y    = (5..95).random() / 100f,
                size = (6..14).random().toFloat(),
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "magic")

    // Glow pulse
    val glowAlpha by transition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.9f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    // Single shared time value — stars use offset of their index
    val time by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "time",
    )

    // Compute star alphas from shared time with per-star offset
    val starAlphas = stars.mapIndexed { i, _ ->
        val offset = i / stars.size.toFloat()
        val t = ((time + offset) % 1f)
        // Bell curve: peak at 0.5
        if (t < 0.5f) t * 2f else (1f - t) * 2f
    }

    val inactiveColor = Color(0xFF6B21A8)  // deep purple
    val goldColor     = Color(0xFFFFD700)
    val buttonColor   = inactiveColor      // always purple — active state shown via border

    Box(modifier = modifier.height(44.dp)) {
        // Glow shadow when inactive
        if (!isActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(4.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                goldColor.copy(alpha = glowAlpha * 0.6f),
                                Color.Transparent,
                            )
                        ),
                        shape = RoundedCornerShape(50)
                    )
            )
        }

        // Main button
        Button(
            onClick  = onClick,
            colors   = ButtonDefaults.buttonColors(containerColor = buttonColor),
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isActive) Modifier.border(
                        width = 2.5.dp,
                        color = Color(0xFFFFD700),
                        shape = RoundedCornerShape(50),
                    ) else Modifier
                ),
            shape    = RoundedCornerShape(50),
        ) {
            Text(
                text  = "🔮 " + stringResource(R.string.pixelart_transform),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color      = if (isActive) Color(0xFFFFD700) else Color(0xFFFFD700),
                ),
            )
        }

        // Stars: animated when inactive, frozen when active
        Canvas(modifier = Modifier.matchParentSize()) {
            stars.forEachIndexed { i, star ->
                // When active: use frozen position (fixed alpha from index)
                val alpha = if (isActive) {
                    // Frozen at a nice spread — alternating visibility
                    if (i % 3 == 0) 0.85f else if (i % 3 == 1) 0.5f else 0.25f
                } else {
                    starAlphas[i]
                }
                if (alpha > 0.05f) {
                    val cx = size.width  * star.x
                    val cy = size.height * star.y
                    val s  = star.size * alpha
                    drawLine(
                        color       = goldColor.copy(alpha = alpha),
                        start       = Offset(cx, cy - s),
                        end         = Offset(cx, cy + s),
                        strokeWidth = s * 0.35f,
                    )
                    drawLine(
                        color       = goldColor.copy(alpha = alpha),
                        start       = Offset(cx - s, cy),
                        end         = Offset(cx + s, cy),
                        strokeWidth = s * 0.35f,
                    )
                    val d = s * 0.55f
                    drawLine(
                        color       = goldColor.copy(alpha = alpha * 0.6f),
                        start       = Offset(cx - d, cy - d),
                        end         = Offset(cx + d, cy + d),
                        strokeWidth = s * 0.2f,
                    )
                    drawLine(
                        color       = goldColor.copy(alpha = alpha * 0.6f),
                        start       = Offset(cx + d, cy - d),
                        end         = Offset(cx - d, cy + d),
                        strokeWidth = s * 0.2f,
                    )
                }
            }
        }
    }
}

// ── Print card builder ────────────────────────────────────────────────────────

/**
 * Builds the final print bitmap:
 *   - Original photo as background (darkened)
 *   - 8-bit style border
 *   - HP hearts top-left
 *   - Sprite canvas overlaid on photo
 *   - STR / DEF / MAG bottom bar
 */
private fun buildPixelArtCard(
    result:  PixelArtAnalyzer.PixelArtResult,
    state:   ZinkUiState,
    context: android.content.Context,
): Bitmap {
    val W = state.printerPrintWidth
    val H = state.printerPrintHeight
    val card = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val c    = Canvas(card)
    c.drawColor(AndroidColor.WHITE)

    val BORDER    = 0f
    val STATS_H   = 230f
    val PANEL_TOP = H - STATS_H - BORDER
    val INNER_L   = BORDER + 16f
    val INNER_R   = W - BORDER - 16f
    val INNER_T   = BORDER + 14f
    val INNER_B   = H - BORDER - 14f
    val TOP_BAND_H = 68f   // wider top band

    // SNES grey palette
    val snesGrey  = AndroidColor.argb(255, 26,  26,  46)
    val snesLight = AndroidColor.argb(255, 50,  50,  90)
    val snesDark  = AndroidColor.argb(255, 10,  10,  25)
    // Dither slightly darker than frame
    val ditherCol1 = AndroidColor.argb(255, 13,  18,  45)
    val ditherCol2 = AndroidColor.argb(255, 18,  26,  60)

    fun dither(left: Float, top: Float, right: Float, bottom: Float, tile: Float = 6f) {
        val p1 = Paint().apply { color = ditherCol1; style = Paint.Style.FILL; isAntiAlias = false }
        val p2 = Paint().apply { color = ditherCol2; style = Paint.Style.FILL; isAntiAlias = false }
        var ty = top
        while (ty < bottom) {
            var tx = left
            while (tx < right) {
                val even = ((((tx-left)/tile).toInt() + ((ty-top)/tile).toInt()) % 2 == 0)
                c.drawRect(tx, ty, minOf(tx+tile,right), minOf(ty+tile,bottom), if (even) p1 else p2)
                tx += tile
            }
            ty += tile
        }
    }

    // ── 1. SNES border ────────────────────────────────────────────────────
    val outerFill = Paint().apply { color = snesGrey; style = Paint.Style.FILL; isAntiAlias = false }
    c.drawRect(BORDER, BORDER, W-BORDER, H-BORDER, outerFill)
    val lp = Paint().apply { color = snesLight; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = false }
    val dp = Paint().apply { color = snesDark;  style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = false }
    c.drawLine(BORDER, BORDER, W-BORDER, BORDER, lp)
    c.drawLine(BORDER, BORDER, BORDER, H-BORDER, lp)
    c.drawLine(BORDER, H-BORDER, W-BORDER, H-BORDER, dp)
    c.drawLine(W-BORDER, BORDER, W-BORDER, H-BORDER, dp)
    val ip = Paint().apply { color = AndroidColor.argb(255,60,65,75); style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = false }
    c.drawRect(INNER_L, INNER_T, INNER_R, INNER_B, ip)
    val wp = Paint().apply { color = AndroidColor.WHITE; style = Paint.Style.FILL }
    c.drawRect(INNER_L+2f, INNER_T+2f, INNER_R-2f, INNER_B-2f, wp)

    // ── 2. Top band dither ────────────────────────────────────────────────
    dither(INNER_L+2f, INNER_T+2f, INNER_R-2f, INNER_T+2f+TOP_BAND_H)

    // ── 3. Stats panel dither ─────────────────────────────────────────────
    dither(INNER_L+2f, PANEL_TOP, INNER_R-2f, INNER_B-2f)

    val goldLine = Paint().apply { color = AndroidColor.argb(255,200,170,50); strokeWidth = 3f; style = Paint.Style.STROKE; isAntiAlias = false }
    c.drawLine(INNER_L+2f, PANEL_TOP, INNER_R-2f, PANEL_TOP, goldLine)
    c.drawLine(INNER_L+2f, INNER_T+2f+TOP_BAND_H, INNER_R-2f, INNER_T+2f+TOP_BAND_H, goldLine)

    // ── 4. Pixel helpers ──────────────────────────────────────────────────
    fun drawPixels(cx: Float, cy: Float, pattern: List<String>, color: Int, px: Float) {
        val p = Paint().apply { this.color = color; style = Paint.Style.FILL; isAntiAlias = false }
        pattern.forEachIndexed { row, line ->
            line.forEachIndexed { col, bit ->
                if (bit == '1') c.drawRect(cx+col*px, cy+row*px, cx+col*px+px, cy+row*px+px, p)
            }
        }
    }

    fun drawBlock(x: Float, y: Float, size: Float, fillColor: Int, filled: Boolean) {
        val px = size / 9f
        val fp = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
        if (filled) {
            fp.color = fillColor
            c.drawRect(x, y, x+size, y+size, fp)
            fp.color = AndroidColor.argb(90,255,255,255)
            c.drawRect(x+px, y+px, x+size-px, y+3*px, fp)
            c.drawRect(x+px, y+px, x+3*px, y+size-px, fp)
            fp.color = AndroidColor.argb(80,0,0,0)
            c.drawRect(x+size-2*px, y+px, x+size-px, y+size-px, fp)
            c.drawRect(x+px, y+size-2*px, x+size-px, y+size-px, fp)
        } else {
            fp.color = AndroidColor.argb(255,90,90,90)
            c.drawRect(x, y, x+size, y+px, fp)
            c.drawRect(x, y+size-px, x+size, y+size, fp)
            c.drawRect(x, y, x+px, y+size, fp)
            c.drawRect(x+size-px, y, x+size, y+size, fp)
            fp.color = AndroidColor.argb(255,45,45,45)
            c.drawRect(x+px, y+px, x+size-px, y+size-px, fp)
        }
    }

    // ── 5. Class name banner (unique classes of all persons) ──────────────
    val classNames: List<String> = if (state.pixelArtEquipmentSet == EquipmentRandomizer.EquipmentSet.NONE) {
        emptyList()
    } else {
        state.pixelArtEquipment
            .mapNotNull { equip ->
                when (equip.set) {
                    EquipmentRandomizer.EquipmentSet.WARRIOR -> "WARRIOR"
                    EquipmentRandomizer.EquipmentSet.PALADIN -> "PALADIN"
                    EquipmentRandomizer.EquipmentSet.ARCHER  -> "ARCHER"
                    EquipmentRandomizer.EquipmentSet.ROGUE   -> "ROGUE"
                    EquipmentRandomizer.EquipmentSet.MAGE    -> "MAGE"
                    else -> null
                }
            }
            .distinct()
    }
    val bandMidY = INNER_T + 2f + TOP_BAND_H / 2f
    if (classNames.isNotEmpty()) {
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = AndroidColor.argb(255, 255, 215, 70)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 26f
        }
        val maxNameWidth = (INNER_R - INNER_L) / 2f - 20f  // leave space for hearts
        // Scale font so all lines use same size
        val line1 = classNames.take(2).joinToString(" · ")
        val line2 = if (classNames.size > 2) classNames.drop(2).joinToString(" · ") else null
        val fontSize = if (line2 != null) 21f else 26f
        namePaint.textSize = fontSize
        if (line2 != null) {
            c.drawText(line1, INNER_L + 16f, bandMidY - 6f, namePaint)
            c.drawText(line2, INNER_L + 16f, bandMidY + fontSize + 2f, namePaint)
        } else {
            c.drawText(line1, INNER_L + 16f, bandMidY + 10f, namePaint)
        }
    }

    // Hearts right-aligned in top band
    val stats  = result.stats.firstOrNull()
    val hpFull = if (stats != null) (stats.hp / 20).coerceIn(0, 5) else 5
    val heartPattern = listOf(
        "011011100","111111110","111111111","111111111",
        "011111110","001111100","000111000","000010000",
    )
    val heartPx    = 4f
    val heartW     = 9 * heartPx
    val heartsTotal = 5 * heartW + 4 * 7f
    var hx = INNER_R - 16f - heartsTotal
    val hy = bandMidY - 8 * heartPx / 2f
    for (i in 0 until 5) {
        drawPixels(hx, hy, heartPattern, if (i < hpFull) AndroidColor.RED else AndroidColor.argb(255, 100, 20, 20), heartPx)
        hx += heartW + 7f
    }

    // ── 6. Background image (random per render, all 5 backgrounds) ───────
    // Gold border: left + right lines around scene (top/bottom already drawn as dividers)
    val sceneBorderPaint = Paint().apply {
        color       = AndroidColor.argb(200, 200, 170, 50)
        style       = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = false
    }
    val sceneL   = INNER_L + 2f
    val sceneR   = INNER_R - 2f
    val sceneTop = INNER_T + 2f + TOP_BAND_H
    val sceneBot = PANEL_TOP
    c.drawLine(sceneL, sceneTop, sceneL, sceneBot, sceneBorderPaint)
    c.drawLine(sceneR, sceneTop, sceneR, sceneBot, sceneBorderPaint)
    val bgFiles = listOf(
        "lpc/backgrounds/background_bright_trees.png",
        "lpc/backgrounds/background_dark_trees.png",
        "lpc/backgrounds/background_fire_place.png",
        "lpc/backgrounds/background_light_tree.png",
        "lpc/backgrounds/background_statue.png",
        "lpc/backgrounds/background_cave.png",
        "lpc/backgrounds/background_crystal.png",
        "lpc/backgrounds/background_crystal2.png",
        "lpc/backgrounds/background_orange_tree.png",
        "lpc/backgrounds/background_village.png",
    )
    // Pick deterministically from result (stable across recompositions)
    val bgFile = if (state.pixelArtEquipmentSet == EquipmentRandomizer.EquipmentSet.NONE) null
    else bgFiles[(result.hashCode() and 0x7FFFFFFF) % bgFiles.size]

    val areaLeft  = INNER_L + 2f
    val areaRight = INNER_R - 2f
    val areaTop   = INNER_T + 2f + TOP_BAND_H
    val areaBot   = PANEL_TOP.toFloat()
    val areaW     = areaRight - areaLeft
    val areaH     = areaBot - areaTop

    if (bgFile != null) {
        // Black fill behind background image (shows through alpha)
        val blackPaint = Paint().apply { color = AndroidColor.BLACK; style = Paint.Style.FILL }
        c.drawRect(areaLeft, areaTop, areaRight, areaBot, blackPaint)
        try {
            val bgBmp = context.assets.open(bgFile).use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
            if (bgBmp != null) {
                // Tree backgrounds offset right so characters stand on the path
                val isTreeBg = bgFile.contains("trees")
                val bgW = areaW.toInt()
                val bgH = areaH.toInt()
                // Scale source slightly wider for tree bgs to allow horizontal offset
                val srcScale = if (isTreeBg) 1.25f else 1.0f
                val scaledW = (bgW * srcScale).toInt()
                val bgScaled = Bitmap.createScaledBitmap(bgBmp, scaledW, bgH, true)
                // Draw with offset: tree bgs shifted right (show path on left side)
                val offsetX = if (isTreeBg) areaLeft - (scaledW - bgW) * 0.1f
                else areaLeft
                c.save()
                c.clipRect(areaLeft, areaTop, areaRight, areaBot)
                c.drawBitmap(bgScaled, offsetX, areaTop, null)
                c.restore()
            }
        } catch (e: Exception) {
            android.util.Log.w("PixelArtCard", "BG load failed: $bgFile — ${e.message}")
        }
    }

    // ── 7. Sprites ────────────────────────────────────────────────────────
    val sprites = result.spriteCanvas
    if (sprites != null) {
        val scale = minOf(areaW / sprites.width.toFloat(), (areaH * 0.90f) / sprites.height.toFloat())
        val sw = (sprites.width  * scale).toInt()
        val sh = (sprites.height * scale).toInt()
        // Tree backgrounds: path runs bottom-center → upper-left → shift character left onto path
        val isTreeBg = bgFile != null && bgFile.contains("trees")
        val centerX  = if (isTreeBg) W / 2f + sw * 0.35f else W / 2f
        c.drawBitmap(Bitmap.createScaledBitmap(sprites, sw, sh, false), centerX - sw / 2f, areaBot - sh, null)
    }

    // ── 7. Icons + stat blocks ────────────────────────────────────────────
    fun loadBoltIcon(ctx: android.content.Context): Bitmap? = try {
        ctx.assets.open("lpc/icons/bolt.png").use {
            val bmp = android.graphics.BitmapFactory.decodeStream(it) ?: return@loadBoltIcon null
            Bitmap.createScaledBitmap(bmp, 48, 48, false)
        }
    } catch (e: Exception) { null }

    fun loadSpriteIcon(assetPath: String): Bitmap? = try {
        val sheet = context.assets.open("lpc/$assetPath").use {
            android.graphics.BitmapFactory.decodeStream(it)
        } ?: return@loadSpriteIcon null
        // Extract south frame (row 2 for 4-dir sheets)
        val srcY = if (sheet.height >= 256) 128 else 0
        val frame = Bitmap.createBitmap(sheet, 0, srcY, 64, 64)
        // Find bounding box of non-transparent pixels
        var minX = 63; var maxX = 0; var minY = 63; var maxY = 0
        for (y in 0 until 64) for (x in 0 until 64) {
            if ((frame.getPixel(x, y) ushr 24) > 10) {
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
        }
        if (maxX <= minX || maxY <= minY) return@loadSpriteIcon null
        // Add 2px padding
        minX = (minX - 2).coerceAtLeast(0); minY = (minY - 2).coerceAtLeast(0)
        maxX = (maxX + 2).coerceAtMost(63); maxY = (maxY + 2).coerceAtMost(63)
        val cropped = Bitmap.createBitmap(frame, minX, minY, maxX-minX+1, maxY-minY+1)
        // Scale to icon size (square, no filtering for pixel look)
        Bitmap.createScaledBitmap(cropped, 48, 48, false)
    } catch (e: Exception) { null }

    val iconSTR = loadSpriteIcon("weapon/sword/longsword/walk/longsword.png")
    val iconDEF = loadSpriteIcon("hat/helmet/barbuta/male/walk.png")
    val iconMAG = loadSpriteIcon("weapon/magic/crystal/universal/foreground/walk/crystal.png")
    val iconEND = loadBoltIcon(context)

    if (stats != null) {
        data class StatInfo(val label: String, val value: Int, val fillColor: Int, val icon: Bitmap?)
        val statList = listOf(
            StatInfo("STR", stats.str, AndroidColor.argb(255,220,120,30),  iconSTR),
            StatInfo("DEF", stats.def, AndroidColor.argb(255,60,130,220),  iconDEF),
            StatInfo("MAG", stats.mag, AndroidColor.argb(255,160,60,220),  iconMAG),
            StatInfo("END", stats.spd, AndroidColor.argb(255,50,180,80),   iconEND),
        )

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE; textSize = 20f; typeface = Typeface.MONOSPACE
        }

        val colW    = (INNER_R - INNER_L) / 2f
        val iconSz  = 48f
        val blockSz = 28f
        val blockGap = 5f
        val leftW   = iconSz + 8f + 40f

        // Two rows closer together
        val panelCenter = PANEL_TOP + STATS_H / 2f
        val rowGap  = 80f
        val row0Y   = panelCenter - rowGap / 2f
        val row1Y   = panelCenter + rowGap / 2f

        for (i in statList.indices) {
            val stat   = statList[i]
            val col    = i % 2
            val colX   = INNER_L + col * colW
            val totalW = leftW + 5 * blockSz + 4 * blockGap
            val ox     = colX + (colW - totalW) / 2f
            val midY   = if (i < 2) row0Y else row1Y
            val iconY  = midY - iconSz / 2f
            val blockY = midY - blockSz / 2f

            // LPC sprite icon or pixel bolt for END
            stat.icon?.let { icon -> c.drawBitmap(icon, ox, iconY, null) }
            val labelOffsetX = ox + 56f
            c.drawText(stat.label, labelOffsetX, midY + 7f, labelPaint)

            val filledN = (stat.value / 20f).toInt().coerceIn(0, 5)
            for (b in 0 until 5) {
                drawBlock(ox + leftW + b*(blockSz+blockGap), blockY, blockSz, stat.fillColor, b < filledN)
            }
        }
    }

    return card
}