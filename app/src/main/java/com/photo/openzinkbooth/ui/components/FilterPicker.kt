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

package com.photo.openzinkbooth.ui.components

import androidx.compose.material3.MaterialTheme
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.animation.core.animateFloatAsState
import android.graphics.Paint as AndroidPaint
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.photo.openzinkbooth.ui.viewmodel.FilterType
import com.photo.openzinkbooth.ui.theme.*

@Composable
fun FilterPicker(
    photo: Bitmap,
    selected: FilterType,
    onSelect: (FilterType) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    thumbWidth: androidx.compose.ui.unit.Dp = 72.dp,
    vertical: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (vertical) {
        androidx.compose.foundation.lazy.LazyColumn(
            state                = listState,
            verticalArrangement  = Arrangement.spacedBy(8.dp),
            contentPadding       = PaddingValues(vertical = 2.dp),
            modifier             = modifier
        ) {
            items(FilterType.entries) { filter ->
                FilterThumb(
                    photo      = photo,
                    filter     = filter,
                    active     = filter == selected,
                    thumbWidth = thumbWidth,
                    onClick    = { onSelect(filter) }
                )
            }
        }
    } else {
        Column(modifier = modifier) {
            LazyRow(
                state                 = listState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding        = PaddingValues(horizontal = 2.dp)
            ) {
                items(FilterType.entries) { filter ->
                    FilterThumb(
                        photo      = photo,
                        filter     = filter,
                        active     = filter == selected,
                        thumbWidth = thumbWidth,
                        onClick    = { onSelect(filter) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterThumb(
    photo: Bitmap,
    filter: FilterType,
    active: Boolean,
    thumbWidth: androidx.compose.ui.unit.Dp = 72.dp,
    onClick: () -> Unit
) {
    val filtered = remember(photo, filter) {
        applyFilterThumbnail(photo, filter)
    }

    val scale by animateFloatAsState(
        if (active) 1.05f else 1f,
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(thumbWidth)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
            .scale(scale)
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .border(
                    width = if (active) 3.dp else 1.dp,
                    color = if (active)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outlineVariant
                )
        ) {
            Image(
                bitmap = filtered.asImageBitmap(),
                contentDescription = stringResource(filter.labelRes),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            if (active) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Text(
            text = stringResource(filter.labelRes),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (active)
                    androidx.compose.ui.text.font.FontWeight.Bold
                else null
            ),
            color = if (active)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

// ---------------------------------------------------------------------------
// Filter application functions
// ---------------------------------------------------------------------------

/**
 * Apply filter for thumbnail display in the picker row.
 * Center-crops to 2:3 ratio first, then scales to 150×225px.
 * This matches the actual print crop so thumbnails look correct
 * regardless of the original image aspect ratio.
 */
private fun applyFilterThumbnail(source: Bitmap, filter: FilterType): Bitmap {
    val cropped = centerCrop2x3(source, thumbW = 150, thumbH = 225)
    val matrix  = filterColorMatrix(filter) ?: return cropped
    val result  = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
    val canvas  = AndroidCanvas(result)
    val paint   = AndroidPaint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
    canvas.drawBitmap(cropped, 0f, 0f, paint)
    return result
}

/**
 * Scale-to-fill + center-crop to the given 2:3 target dimensions.
 * Equivalent to CSS `object-fit: cover` centered.
 */
private fun centerCrop2x3(source: Bitmap, thumbW: Int, thumbH: Int): Bitmap {
    val scale   = maxOf(thumbW.toFloat() / source.width, thumbH.toFloat() / source.height)
    val scaledW = (source.width  * scale).toInt()
    val scaledH = (source.height * scale).toInt()
    val scaled  = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
    val cropX   = (scaledW - thumbW) / 2
    val cropY   = (scaledH - thumbH) / 2
    return Bitmap.createBitmap(scaled, cropX, cropY, thumbW, thumbH)
}

/**
 * Apply filter for the preview screen – preserves original resolution
 * and aspect ratio. Only the ColorMatrix is applied, no scaling.
 * This ensures the preview looks sharp and proportions are correct.
 */
fun applyFilter(source: Bitmap, filter: FilterType): Bitmap {
    val matrix = filterColorMatrix(filter) ?: return source
    val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(result)
    val paint  = AndroidPaint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
    canvas.drawBitmap(source, 0f, 0f, paint)
    return result
}

/**
 * Apply filter at full print resolution.
 * Called by the print pipeline before sending to SprocketPrinter.
 */
fun applyFilterForPrint(source: Bitmap, filter: FilterType): Bitmap = applyFilter(source, filter)

private fun filterColorMatrix(filter: FilterType): ColorMatrix? = when (filter) {
    FilterType.ORIGINAL -> null

    // grayscale(1) – desaturate completely
    FilterType.BW -> ColorMatrix().apply { setSaturation(0f) }

    // sepia(0.55) contrast(0.92) brightness(1.05)
    FilterType.VINTAGE -> ColorMatrix().apply {
        setSaturation(0f)
        postConcat(ColorMatrix(floatArrayOf(
            1.07f, 0.74f, 0.28f, 0f, 0f,
            0.40f, 0.69f, 0.22f, 0f, 0f,
            0.31f, 0.35f, 0.47f, 0f, 0f,
            0f,    0f,    0f,    1f, 0f
        )))
        // contrast 0.92 + brightness 1.05
        postConcat(ColorMatrix(floatArrayOf(
            0.92f, 0f,    0f,    0f, 12f,
            0f,    0.92f, 0f,    0f, 12f,
            0f,    0f,    0.92f, 0f, 12f,
            0f,    0f,    0f,    1f,  0f
        )))
    }

    // saturate(2) contrast(1.08)
    FilterType.VIVID -> ColorMatrix().apply {
        setSaturation(2f)
        postConcat(ColorMatrix(floatArrayOf(
            1.08f, 0f,    0f,    0f, -10f,
            0f,    1.08f, 0f,    0f, -10f,
            0f,    0f,    1.08f, 0f, -10f,
            0f,    0f,    0f,    1f,   0f
        )))
    }

    // saturate(0.65) brightness(1.12) contrast(0.88)
    FilterType.FADED -> ColorMatrix().apply {
        setSaturation(0.65f)
        postConcat(ColorMatrix(floatArrayOf(
            0.88f, 0f,    0f,    0f, 30f,
            0f,    0.88f, 0f,    0f, 30f,
            0f,    0f,    0.88f, 0f, 30f,
            0f,    0f,    0f,    1f,  0f
        )))
    }

    // Cool blue tones: slight desaturation + a cooler colour temperature
    // (pull red down a little, lift blue) for a clean, natural cool look.
    FilterType.COOL -> ColorMatrix().apply {
        // setSaturation() resets the entire matrix, so it must run FIRST;
        // the temperature shift is concatenated after.
        setSaturation(0.85f)
        postConcat(ColorMatrix(floatArrayOf(
            0.90f, 0f,    0f,    0f,  0f,
            0f,    0.98f, 0f,    0f,  2f,
            0f,    0f,    1.10f, 0f, 10f,
            0f,    0f,    0f,    1f,  0f
        )))
    }
}