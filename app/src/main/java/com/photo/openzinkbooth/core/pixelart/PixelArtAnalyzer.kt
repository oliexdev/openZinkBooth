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

package com.photo.openzinkbooth.core.pixelart

import com.photo.openzinkbooth.core.pixelart.analyzer.FaceAttributeAnalyzer
import com.photo.openzinkbooth.core.pixelart.analyzer.FaceParsingAnalyzer
import com.photo.openzinkbooth.core.pixelart.analyzer.FaceLandmarkDetector
import com.photo.openzinkbooth.core.pixelart.analyzer.PersonSegmenter
import com.photo.openzinkbooth.core.pixelart.analyzer.CharacterFeatures
import com.photo.openzinkbooth.core.pixelart.analyzer.HairLength
import com.photo.openzinkbooth.core.pixelart.analyzer.ClothingStyle
import com.photo.openzinkbooth.core.pixelart.sprite.EquipmentRandomizer
import com.photo.openzinkbooth.core.pixelart.sprite.SpriteMapper
import com.photo.openzinkbooth.core.pixelart.sprite.SpriteCompositor
import com.photo.openzinkbooth.core.pixelart.stats.StatCalculator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import com.photo.openzinkbooth.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * PixelArtAnalyzer
 *
 * Pipeline coordinator for the Pixel Art feature.
 * Orchestrates: segmentation → face detection → gender/age → face parsing → sprite generation.
 *
 * Usage:
 *   val analyzer = PixelArtAnalyzer(context)
 *   val result   = analyzer.analyze(photo)
 */
class PixelArtAnalyzer(private val context: Context) {

    companion object {
        private const val TAG = "PixelArtAnalyzer"
    }

    private val segmenter          = PersonSegmenter(context)
    private val landmarkDetector   = FaceLandmarkDetector(context)
    private val attributeAnalyzer  = FaceAttributeAnalyzer(context)
    private val faceParsingAnalyzer = FaceParsingAnalyzer(context)
    private val spriteCompositor   = SpriteCompositor(context)

    // ── Result data class ─────────────────────────────────────────────────────

    data class PixelArtResult(
        val originalPhoto:   Bitmap,
        val segmentedPhoto:  Bitmap?,
        val allFeatures:     List<CharacterFeatures>,
        val spriteCanvas:    Bitmap?,               // sprites positioned like original
        val stats:           List<CharacterStats>,  // one per person
        val faces:           List<FaceLandmarkDetector.FaceResult>,
    )

    data class CharacterStats(
        val hp:  Int,   // 0–100
        val str: Int,   // 0–100
        val def: Int,   // 0–100
        val mag: Int,   // 0–100
        val spd: Int,   // 0–100
    )

    // ── Main entry point ──────────────────────────────────────────────────────

    /**
     * Preloads all models in parallel.
     * Call this as soon as Pixel Art mode is activated, before the user takes a photo.
     * analyze() will skip init if models are already loaded.
     */
    suspend fun preloadModels() = withContext(Dispatchers.IO) {
        LogManager.d(TAG, "Preloading models in parallel…")
        val t0 = System.currentTimeMillis()
        kotlinx.coroutines.coroutineScope {
            listOf(
                async { segmenter.init() },
                async { landmarkDetector.init() },
                async { attributeAnalyzer.init() },
                async { faceParsingAnalyzer.init() },
            ).forEach { it.await() }
        }
        LogManager.d(TAG, "Preload done in ${System.currentTimeMillis() - t0}ms")
    }

    suspend fun analyze(
        photo: Bitmap,
        onProgress: (String) -> Unit = {},
    ): PixelArtResult = withContext(Dispatchers.IO) {

        // Step 1 — Init all models in parallel (no-op if already loaded)
        onProgress("pixelart_progress_init")
        val t0 = System.currentTimeMillis()
        val (segOk, _, attrOk, parseOk) = kotlinx.coroutines.coroutineScope {
            listOf(
                async { segmenter.init() },
                async { landmarkDetector.init() },
                async { attributeAnalyzer.init() },
                async { faceParsingAnalyzer.init() },
            ).map { it.await() }
        }
        LogManager.d(TAG, "Models ready in ${System.currentTimeMillis() - t0}ms")

        // Step 2 — Segmentation
        onProgress("pixelart_progress_segment")
        val segmented = segmenter.segment(photo)

        // Step 3 — Face detection
        onProgress("pixelart_progress_detect")
        val flat  = segmented?.flattenAlpha() ?: photo
        val faces = landmarkDetector.detect(flat)


        if (faces.isEmpty()) {
            return@withContext PixelArtResult(photo, segmented, emptyList(), null, emptyList(), emptyList())
        }

        // Step 4 — Analyse faces sequentially (OpenCV Net is NOT thread-safe)
        onProgress("pixelart_progress_analyse:${faces.size}")
        val sortedFaces = faces.sortedBy { it.boundingBox.centerX() }
        val allFeatures = sortedFaces.map { face ->
            val attrs   = if (attrOk)  attributeAnalyzer.analyze(flat, face.boundingBox) else null
            val parsing = if (parseOk) faceParsingAnalyzer.analyze(photo, face.boundingBox, segmented) else null
            CharacterFeatures(
                skinColor     = parsing?.skinColor     ?: Color.rgb(210, 180, 140),
                hairColor     = parsing?.hairColor     ?: Color.rgb(50, 30, 10),
                hairLength    = parsing?.hairLength    ?: HairLength.MEDIUM,
                faceShape     = parsing?.faceShape     ?: FaceAttributeAnalyzer.FaceShape.OVAL,
                hasGlasses    = parsing?.hasGlasses    ?: false,
                hasNecklace   = parsing?.hasNecklace   ?: false,
                hasBeard      = (parsing?.hasBeard ?: false) &&
                        (attrs?.gender == FaceAttributeAnalyzer.Gender.MALE),
                clothingColor = parsing?.clothingColor ?: Color.rgb(70, 130, 180),
                clothingStyle = parsing?.clothingStyle ?: ClothingStyle.CASUAL,
                eyeColor      = parsing?.eyeColor      ?: FaceParsingAnalyzer.EyeColor.BROWN,
                gender        = attrs?.gender          ?: FaceAttributeAnalyzer.Gender.MALE,
                ageGroup      = attrs?.ageGroup        ?: FaceAttributeAnalyzer.AgeGroup.ADULT,
                ageYears      = attrs?.ageYears        ?: 25,
                faceRect      = face.boundingBox,
                landmarks     = face.landmarks,
            )
        }

        // Step 5 — Generate sprites
        onProgress("pixelart_progress_generate")
        val stableFeatures = allFeatures.map { SpriteMapper.resolveStableChoices(it) }

        // Log analysis results per face
        stableFeatures.forEachIndexed { i, f ->
            LogManager.d(TAG, "Face $i: gender=${f.gender} age=${f.ageGroup}(${f.ageYears}) " +
                    "shape=${f.faceShape} skin=#${Integer.toHexString(f.skinColor)} " +
                    "hair=#${Integer.toHexString(f.hairColor)} length=${f.hairLength} " +
                    "eyes=${f.eyeColor} beard=${f.hasBeard} glasses=${f.hasGlasses} " +
                    "necklace=${f.hasNecklace} cloth=#${Integer.toHexString(f.clothingColor)} " +
                    "style=${f.clothingStyle}")
        }
        val sprites = generateSprites(stableFeatures, List(stableFeatures.size) { null })

        // Step 6 — Compose sprite canvas with depth positioning
        val spriteCanvas = buildSpriteCanvas(photo, stableFeatures, sprites)

        // Step 7 — Calculate stats
        val stats: List<CharacterStats> = stableFeatures.map { StatCalculator.calculate(it, EquipmentRandomizer.EquipmentSet.NONE) }


        PixelArtResult(
            originalPhoto  = photo,
            segmentedPhoto = segmented,
            allFeatures    = stableFeatures,
            spriteCanvas   = spriteCanvas,
            stats          = stats,
            faces          = faces,
        )
    }

    // ── Sprite generation ─────────────────────────────────────────────────────

    suspend fun generateSprites(
        allFeatures: List<CharacterFeatures>,
        equipment:   List<EquipmentRandomizer.Equipment?>,
    ): List<Bitmap> = allFeatures.mapIndexedNotNull { i, f ->
        val equip = equipment.getOrNull(i)
        try { spriteCompositor.composite(SpriteMapper.map(f, equip), scaleFactor = 4) }
        catch (e: Exception) { LogManager.w(TAG, "Sprite failed: ${e.message}"); null }
    }

    // ── Sprite canvas builder ─────────────────────────────────────────────────

    fun buildSpriteCanvas(
        original:    Bitmap,
        allFeatures: List<CharacterFeatures>,
        sprites:     List<Bitmap>,
    ): Bitmap? {
        if (sprites.isEmpty()) return null
        val imgW = original.width.toFloat()
        val imgH = original.height.toFloat()
        val n    = allFeatures.size

        // Face Y positions → depth (higher Y = further back)
        val faceYs = allFeatures.map { f -> f.faceRect?.centerY() ?: (imgH / 2f) }
        val maxY   = faceYs.maxOrNull() ?: imgH
        val minY   = faceYs.minOrNull() ?: 0f

        // Fixed sprite size: 64px * scaleFactor=4 = 256px
        val sprW = sprites.firstOrNull()?.width  ?: 256
        val sprH = sprites.firstOrNull()?.height ?: 256

        // Canvas always matches Zink 2:3 print ratio (640x960)
        // Sprites are positioned by face location relative to original photo
        val CANVAS_W = 640
        val CANVAS_H = 960
        val bmp = Bitmap.createBitmap(CANVAS_W, CANVAS_H, Bitmap.Config.ARGB_8888)
        val c   = Canvas(bmp)
        c.drawColor(Color.TRANSPARENT)

        data class Entry(val f: CharacterFeatures, val sprite: Bitmap, val faceY: Float)
        val entries = allFeatures.mapIndexedNotNull { i, f ->
            val s = sprites.getOrNull(i) ?: return@mapIndexedNotNull null
            Entry(f, s, faceYs.getOrElse(i) { imgH / 2f })
        }.sortedByDescending { it.faceY }

        // Sprites are placed in bottom half of canvas, positioned by face X
        val placementAreaY = CANVAS_H - sprH - 20   // bottom margin
        val yRange = (CANVAS_H * 0.08f).toInt()     // slight depth variation

        for (entry in entries) {
            val depthRatio = if (maxY > minY) (entry.faceY - minY) / (maxY - minY) else 0.5f
            val faceCX = entry.f.faceRect?.centerX() ?: (imgW / 2f)
            val normX  = faceCX / imgW
            val compX  = 0.05f + normX * 0.9f
            val destX  = ((CANVAS_W - sprW) * compX).toInt().coerceIn(0, CANVAS_W - sprW)
            val destY  = (placementAreaY - depthRatio * yRange).toInt().coerceIn(0, CANVAS_H - sprH)
            c.drawBitmap(entry.sprite, destX.toFloat(), destY.toFloat(), null)
        }
        return bmp
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    fun release() {
        segmenter.release()
        landmarkDetector.release()
        attributeAnalyzer.release()
        faceParsingAnalyzer.release()
        LogManager.d(TAG, "Released")
    }
}

// ── Bitmap extension ──────────────────────────────────────────────────────────

fun Bitmap.flattenAlpha(): Bitmap {
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(result).apply {
        drawColor(Color.BLACK)
        drawBitmap(this@flattenAlpha, 0f, 0f, null)
    }
    return result
}