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

package com.photo.openzinkbooth.core.pixelart.sprite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SpriteCompositor
 *
 * Composites LPC sprite layers into a single 64×64 character bitmap.
 *
 * LPC standard animation sheet layout (per file):
 *   All pre-cropped animation files have 4 rows = 4 directions:
 *     Row 0 = North  (facing away)
 *     Row 1 = West   (facing left)
 *     Row 2 = South  (facing viewer) ← we always use this
 *     Row 3 = East   (facing right)
 *
 *   Exception: hurt files have only 1 row (always south-facing)
 *
 * Frame column: always col 0 = first frame of the animation.
 *   For combat_idle: frame 0 = main combat stance ← body uses this
 *   For walk:        frame 0 = standing neutral    ← weapons use this
 *
 * These two poses are close but not identical → slight misalignment is expected
 * and acceptable at pixel-art scale.
 */
class SpriteCompositor(private val context: Context) {

    companion object {
        private const val TAG       = "SpriteCompositor"
        private const val SPRITE_ROOT = "lpc"
        private const val FRAME_W   = 64
        private const val FRAME_H   = 64
        private const val SOUTH_ROW = 2     // index into 4-direction files
    }

    suspend fun composite(
        config: SpriteMapper.SpriteConfig,
        scaleFactor: Int = 1,
    ): Bitmap = withContext(Dispatchers.IO) {

        var result = Bitmap.createBitmap(FRAME_W, FRAME_H, Bitmap.Config.ARGB_8888)

        for (layer in config.layers) {
            val layerBmp = loadLayer(layer) ?: continue
            if (layer.path.startsWith("weapon/") || layer.path.startsWith("shield/")) {
            }
            result = alphaBlit(result, layerBmp)
        }

        if (scaleFactor > 1)
            Bitmap.createScaledBitmap(result, FRAME_W * scaleFactor, FRAME_H * scaleFactor, false)
        else result
    }

    // ── Layer loading ─────────────────────────────────────────────────────────

    private fun loadLayer(layer: SpriteMapper.SpriteLayer): Bitmap? {
        return try {
            val sheet = context.assets.open("$SPRITE_ROOT/${layer.path}").use {
                android.graphics.BitmapFactory.decodeStream(it)
            }?.copy(Bitmap.Config.ARGB_8888, true) ?: return null

            val frame = extractFrame(sheet, layer.path)

            if (layer.recolor != null) recolorBitmap(frame, layer.recolor) else frame

        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts the south-facing frame (row=2, col=0) from a sprite sheet.
     *
     * All character sprites are now walk.png (576x256 = 9 frames × 4 dirs).
     * Weapons use walk/VARIANT.png (same layout).
     * Shields use combat_idle/COLOR.png (128x256 = 2 frames × 4 dirs).
     * Shadow uses slash/shadow.png (384x256 = 6 frames × 4 dirs).
     * Hurt files (384x64) are single-row — take col=0.
     *
     * All cases: south = row 2 (for multi-direction files).
     */
    private fun extractFrame(sheet: Bitmap, path: String): Bitmap {
        val w = sheet.width
        val h = sheet.height

        // Single frame
        if (w == FRAME_W && h == FRAME_H) return sheet

        // Single-row file (hurt: 384x64) — already south-facing
        if (h <= FRAME_H) {
            return Bitmap.createBitmap(sheet, 0, 0, FRAME_W, FRAME_H)
        }

        // 8-direction file (h=512): south = row 4, use middle frame for best visibility
        // e.g. great bow 1024x512 — frame 0 has bow barely visible, middle frame better
        if (h == FRAME_H * 8) {
            val totalCols = w / FRAME_W
            val midCol = totalCols / 2
            return Bitmap.createBitmap(sheet, midCol * FRAME_W, SOUTH_ROW * 2 * FRAME_H, FRAME_W, FRAME_H)
        }

        // Standard 4-direction file (h=256): south = row 2
        if (h >= FRAME_H * 4) {
            return Bitmap.createBitmap(sheet, 0, SOUTH_ROW * FRAME_H, FRAME_W, FRAME_H)
        }

        // Fallback
        return Bitmap.createBitmap(sheet, 0, 0, FRAME_W, FRAME_H)
    }

    // ── Recoloring ────────────────────────────────────────────────────────────

    private fun recolorBitmap(src: Bitmap, targetArgb: Int): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val tr = (targetArgb shr 16) and 0xFF
        val tg = (targetArgb shr  8) and 0xFF
        val tb =  targetArgb         and 0xFF

        for (i in pixels.indices) {
            val px = pixels[i]
            val alpha = (px shr 24) and 0xFF
            if (alpha == 0) continue

            val r = (px shr 16) and 0xFF
            val g = (px shr  8) and 0xFF
            val b =  px         and 0xFF

            val brightness = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            val scale = brightness * 1.8f

            pixels[i] = (alpha shl 24) or
                    ((tr * scale).toInt().coerceIn(0, 255) shl 16) or
                    ((tg * scale).toInt().coerceIn(0, 255) shl  8) or
                    (tb * scale).toInt().coerceIn(0, 255)
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    // ── Alpha compositing ─────────────────────────────────────────────────────

    private fun alphaBlit(bottom: Bitmap, top: Bitmap): Bitmap {
        val w = bottom.width; val h = bottom.height
        val botPx = IntArray(w * h)
        val topPx = IntArray(top.width * top.height)
        bottom.getPixels(botPx, 0, w, 0, 0, w, h)
        top.getPixels(topPx, 0, top.width, 0, 0, top.width, top.height)

        val tw = top.width; val th = top.height

        for (py in 0 until minOf(h, th)) {
            for (px in 0 until minOf(w, tw)) {
                val bi = py * w  + px
                val ti = py * tw + px
                val tpx = topPx[ti]
                val ta = (tpx shr 24) and 0xFF
                if (ta == 0) continue
                if (ta == 255) { botPx[bi] = tpx; continue }

                val bpx = botPx[bi]
                val ba  = (bpx shr 24) and 0xFF
                val af  = ta / 255f
                val bf  = (1f - af) * (ba / 255f)
                val sum = af + bf + 0.001f
                botPx[bi] = (((af + bf) * 255f).toInt().coerceIn(0, 255) shl 24) or
                        (((((tpx shr 16) and 0xFF) * af + ((bpx shr 16) and 0xFF) * bf) / sum).toInt().coerceIn(0, 255) shl 16) or
                        (((((tpx shr  8) and 0xFF) * af + ((bpx shr  8) and 0xFF) * bf) / sum).toInt().coerceIn(0, 255) shl  8) or
                        ((( (tpx        and 0xFF)  * af + ( bpx        and 0xFF)  * bf) / sum).toInt().coerceIn(0, 255))
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(botPx, 0, w, 0, 0, w, h)
        return result
    }

    fun release() { /* stateless */ }
}