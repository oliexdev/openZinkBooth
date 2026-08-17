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

package com.photo.openzinkbooth.core.pixelart.analyzer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import com.photo.openzinkbooth.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.*
import org.opencv.dnn.Dnn
import org.opencv.dnn.Net
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream

/**
 * FaceParsingAnalyzer
 *
 * Uses BiSeNet face parsing ONNX model to extract all facial features
 * in a single inference pass.
 *
 * Model: yakhyo/face-parsing resnet18.onnx (~51MB, MIT license)
 * https://github.com/yakhyo/face-parsing
 *
 * CelebAMask-HQ classes:
 *   0=bg, 1=skin, 2=l_brow, 3=r_brow, 4=l_eye, 5=r_eye,
 *   6=glasses, 7=l_ear, 8=r_ear, 9=ear_r, 10=nose,
 *   11=mouth, 12=u_lip, 13=l_lip, 14=neck, 15=neck_l,
 *   16=cloth, 17=hair, 18=hat
 *
 * Input:  [1, 3, 512, 512] BGR, mean=127.5, std=127.5, swapRB=false
 * Output: [1, 19, 512, 512] class logits → argmax → class mask
 */
class FaceParsingAnalyzer(private val context: Context) {

    private var net: Net? = null

    companion object {
        private const val TAG        = "FaceParsingAnalyzer"
        private const val MODEL      = "model/face_parsing.onnx"
        private const val INPUT_SIZE = 512

        // Official class indices from yakhyo/face-parsing utils/common.py
        // 0=bg, 1=skin, 2=l_brow, 3=r_brow, 4=l_eye, 5=r_eye,
        // 6=eye_g(glasses), 7=l_ear, 8=r_ear, 9=ear_r(earring),
        // 10=nose, 11=mouth, 12=u_lip, 13=l_lip, 14=neck,
        // 15=neck_l(necklace), 16=cloth, 17=hair, 18=hat
        private const val CLS_SKIN      = 1
        private const val CLS_L_BROW    = 2
        private const val CLS_R_BROW    = 3
        private const val CLS_L_EYE     = 4
        private const val CLS_R_EYE     = 5
        private const val CLS_GLASSES   = 6
        private const val CLS_EARRING   = 9
        private const val CLS_NOSE      = 10
        private const val CLS_NECK      = 14
        private const val CLS_NECKLACE  = 15
        private const val CLS_CLOTH     = 16
        private const val CLS_HAIR      = 17
    }

    data class ParsingResult(
        val skinColor:     Int,                          // ARGB
        val hairColor:     Int,                          // ARGB
        val hairLength:    HairLength,
        val hasGlasses:    Boolean,
        val hasNecklace:   Boolean,
        val clothingColor: Int,                          // ARGB
        val clothingStyle: ClothingStyle,
        val hairPixels:    Int,                          // for debug
        val glassesPixels: Int,                          // for debug
        val necklacePixels: Int,                         // for debug
        val hasBeard:      Boolean,
        val eyeColor:      EyeColor,
        val faceShape:     FaceAttributeAnalyzer.FaceShape,
    )

    enum class EyeColor(val folderName: String) {
        BROWN("brown"), BLUE("blue"), GREEN("green"),
        GRAY("gray"), AMBER("orange")
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        if (net != null) return@withContext true
        try {
            val file = File(context.cacheDir, MODEL).also { it.parentFile?.mkdirs() }
            if (!file.exists() || file.length() == 0L) {
                LogManager.d(TAG, "Copying $MODEL from assets...")
                context.assets.open(MODEL).use { inp ->
                    FileOutputStream(file).use { out -> inp.copyTo(out) }
                }
            }
            LogManager.d(TAG, "Loading $MODEL (${file.length()} bytes)")
            net = Dnn.readNetFromONNX(file.absolutePath)
            LogManager.d(TAG, "Model loaded successfully")
            true
        } catch (e: Exception) {
            LogManager.e(TAG, "Init failed: ${e.message}", e)
            false
        }
    }

    // ── Main analysis ─────────────────────────────────────────────────────────

    /**
     * Runs BiSeNet on a face-centered crop of [source].
     * The crop is expanded to include hair above the head and
     * clothing below the chin.
     *
     * @param source    Full original bitmap
     * @param faceRect  Face bounding box in original image coordinates
     */
    suspend fun analyze(source: Bitmap, faceRect: RectF, segmented: Bitmap? = null): ParsingResult? =
        withContext(Dispatchers.IO) {
            val net = net ?: return@withContext null
            try {
                val fh    = faceRect.height()
                val fw    = faceRect.width()

                // Expand crop: 80% above (hair), 30% sides, 50% below (clothing)
                // Use segmented image (no background) if available
                val imgSrc = segmented ?: source
                val origW  = imgSrc.width
                val origH  = imgSrc.height
                val x1 = (faceRect.left   - fw * 0.3f).toInt().coerceIn(0, origW - 1)
                val y1 = (faceRect.top    - fh * 0.8f).toInt().coerceIn(0, origH - 1)
                val x2 = (faceRect.right  + fw * 0.3f).toInt().coerceIn(0, origW - 1)
                val y2 = (faceRect.bottom + fh * 1.0f).toInt().coerceIn(0, origH - 1)
                val cw = (x2 - x1).coerceAtLeast(1)
                val ch = (y2 - y1).coerceAtLeast(1)

                val crop    = Bitmap.createBitmap(imgSrc, x1, y1, cw, ch)
                val bgrMat  = bitmapToBgrMat(crop)
                val resized = Mat()
                Imgproc.resize(bgrMat, resized,
                    Size(INPUT_SIZE.toDouble(), INPUT_SIZE.toDouble()))

                // Preprocess: mean=127.5, std=127.5, swapRB=false (BGR stays BGR)
                val blob = Dnn.blobFromImage(
                    resized, 1.0 / 127.5,
                    Size(INPUT_SIZE.toDouble(), INPUT_SIZE.toDouble()),
                    Scalar(127.5, 127.5, 127.5),
                    false, false
                )
                net.setInput(blob)
                val output = net.forward() // [1, 19, 512, 512]

                // Argmax across class dimension → [512, 512] class mask
                val classMask = argmax19(output)

                // Scale factors from 512×512 back to crop size
                val scaleX = cw.toFloat() / INPUT_SIZE
                val scaleY = ch.toFloat() / INPUT_SIZE

                // ── Extract per-class pixel lists ─────────────────────────────────
                val skinPixels     = mutableListOf<Pair<Int,Int>>()
                val hairPixelsList = mutableListOf<Pair<Int,Int>>()
                val clothPixels    = mutableListOf<Pair<Int,Int>>()
                var glassesCount   = 0
                var hairCount      = 0
                var necklaceCount  = 0
                var neckCount      = 0
                val eyePixels      = mutableListOf<Pair<Int,Int>>()

                for (py in 0 until INPUT_SIZE) {
                    for (px in 0 until INPUT_SIZE) {
                        when (classMask[py * INPUT_SIZE + px]) {
                            CLS_SKIN      -> skinPixels.add(Pair(px, py))
                            CLS_HAIR      -> { hairPixelsList.add(Pair(px, py)); hairCount++ }
                            CLS_GLASSES   -> glassesCount++
                            CLS_CLOTH     -> clothPixels.add(Pair(px, py))
                            CLS_NECKLACE  -> necklaceCount++
                            CLS_NECK      -> neckCount++
                            CLS_L_EYE, CLS_R_EYE -> eyePixels.add(Pair(px, py))
                        }
                    }
                }

                LogManager.d(TAG, "cloth=${clothPixels.size} skin=${skinPixels.size} hair=$hairCount")
                // ── Colors from original crop ─────────────────────────────────
                val skinColor = averageColor(
                    crop, skinPixels, scaleX, scaleY,
                    maxY = (INPUT_SIZE * 0.65f).toInt()  // only upper 65% of face = no beard area
                )
                val hairColor     = averageColor(crop, hairPixelsList, scaleX, scaleY)
                val clothingColor = if (clothPixels.isEmpty())
                    Color.rgb(120, 120, 120)
                else
                    averageColor(crop, clothPixels, scaleX, scaleY)

                // ── Eye color from iris pixels ────────────────────────────────
                val eyeColor = detectEyeColor(crop, eyePixels, scaleX, scaleY)

                // ── Hair length ───────────────────────────────────────────────
                val hairLength = estimateHairLength(classMask, hairCount)

                // ── Glasses / Hat / Necklace ──────────────────────────────────
                val hasGlasses  = glassesCount  > 500
                val hasNecklace = necklaceCount  > 200

                // ── Clothing style from color + necklace ──────────────────────
                val clothingStyle = if (clothPixels.isEmpty())
                    ClothingStyle.CASUAL
                else
                    estimateClothingStyle(clothingColor, hasNecklace, neckCount)

                // ── Beard ──────────────────────────────────────────────────────
                val hasBeard  = detectBeard(skinPixels, crop, scaleX, scaleY)
                val faceShape = detectFaceShape(skinPixels)


                ParsingResult(
                    skinColor      = skinColor,
                    hairColor      = hairColor,
                    hairLength     = hairLength,
                    hasGlasses     = hasGlasses,
                    hasNecklace    = hasNecklace,
                    clothingColor  = clothingColor,
                    clothingStyle  = clothingStyle,
                    hairPixels     = hairCount,
                    glassesPixels  = glassesCount,
                    necklacePixels = necklaceCount,
                    hasBeard       = hasBeard,
                    eyeColor       = eyeColor,
                    faceShape      = faceShape,
                )
            } catch (e: Exception) {
                LogManager.e(TAG, "Analysis failed: ${e.message}", e)
                null
            }
        }


    // ── Beard detection ───────────────────────────────────────────────────────

    /**
     * Detects beard by analyzing edge density in the lower face region.
     * Beard hair creates high-frequency texture (many edges) on skin.
     */
    private fun detectBeard(
        skinPixels: List<Pair<Int,Int>>,
        crop: Bitmap,
        scaleX: Float,
        scaleY: Float,
    ): Boolean {
        if (skinPixels.isEmpty()) return false
        val skinTop    = skinPixels.minOf { it.second }
        val skinBottom = skinPixels.maxOf { it.second }
        val skinH      = skinBottom - skinTop
        val chinTop    = skinTop + (skinH * 0.6f).toInt()  // lower 40% = chin area

        // Extract chin region from crop
        val x1 = (INPUT_SIZE * 0.2f * scaleX).toInt().coerceIn(0, crop.width - 1)
        val y1 = (chinTop * scaleY).toInt().coerceIn(0, crop.height - 1)
        val x2 = (INPUT_SIZE * 0.8f * scaleX).toInt().coerceIn(0, crop.width - 1)
        val y2 = (skinBottom * scaleY).toInt().coerceIn(0, crop.height - 1)
        if (x1 >= x2 || y1 >= y2) return false

        val cw = x2 - x1; val ch = y2 - y1
        val pixels = IntArray(cw * ch)
        crop.getPixels(pixels, 0, cw, x1, y1, cw, ch)

        // Count edge-like transitions (high local contrast = beard stubble)
        var edgeCount = 0; var total = 0
        for (py in 1 until ch - 1) {
            for (px in 1 until cw - 1) {
                val i = py * cw + px
                val c  = pixels[i]
                val cr = pixels[i + 1]
                val cb = pixels[i + cw]
                val alpha = (c shr 24) and 0xFF
                if (alpha < 128) continue
                val luma   = (0.299f*((c shr 16) and 0xFF) + 0.587f*((c shr 8) and 0xFF) + 0.114f*(c and 0xFF))
                val lumaR  = (0.299f*((cr shr 16) and 0xFF) + 0.587f*((cr shr 8) and 0xFF) + 0.114f*(cr and 0xFF))
                val lumaB  = (0.299f*((cb shr 16) and 0xFF) + 0.587f*((cb shr 8) and 0xFF) + 0.114f*(cb and 0xFF))
                val grad   = kotlin.math.abs(luma - lumaR) + kotlin.math.abs(luma - lumaB)
                if (grad > 15f) edgeCount++
                total++
            }
        }
        val density = if (total > 0) edgeCount.toFloat() / total else 0f
        return density > 0.20f
    }

    // ── Hair length estimation ────────────────────────────────────────────────

    private fun estimateHairLength(
        classMask: IntArray,
        hairCount: Int,
    ): HairLength {
        if (hairCount < 100) return HairLength.SHORT

        val faceRows = mutableSetOf<Int>()
        val hairRows = mutableSetOf<Int>()
        for (py in 0 until INPUT_SIZE) {
            for (px in 0 until INPUT_SIZE) {
                when (classMask[py * INPUT_SIZE + px]) {
                    CLS_SKIN -> faceRows.add(py)
                    CLS_HAIR -> hairRows.add(py)
                }
            }
        }

        if (faceRows.isEmpty() || hairRows.isEmpty()) return HairLength.SHORT

        val faceTop    = faceRows.min()
        val faceBottom = faceRows.max()
        val faceHeight = faceBottom - faceTop
        val hairBottom = hairRows.max()
        val belowChin  = maxOf(0, hairBottom - faceBottom)
        val ratio      = if (faceHeight > 0) belowChin.toFloat() / faceHeight else 0f


        return when {
            ratio < 0.05f -> HairLength.SHORT
            ratio < 0.25f -> HairLength.MEDIUM
            else          -> HairLength.LONG
        }
    }

    // ── Argmax across 19 classes ──────────────────────────────────────────────

    private fun argmax19(output: Mat): IntArray {
        // output shape: [1, 19, 512, 512]
        // Flatten to [19, 512*512] and find argmax per pixel
        val numClasses = 19
        val numPixels  = INPUT_SIZE * INPUT_SIZE
        val result     = IntArray(numPixels)
        val classData  = Array(numClasses) { FloatArray(numPixels) }

        for (c in 0 until numClasses) {
            for (py in 0 until INPUT_SIZE) {
                val rowData = FloatArray(INPUT_SIZE)
                output.get(intArrayOf(0, c, py, 0), rowData)
                System.arraycopy(rowData, 0, classData[c], py * INPUT_SIZE, INPUT_SIZE)
            }
        }

        for (i in 0 until numPixels) {
            var maxVal = classData[0][i]
            var maxIdx = 0
            for (c in 1 until numClasses) {
                if (classData[c][i] > maxVal) {
                    maxVal = classData[c][i]
                    maxIdx = c
                }
            }
            result[i] = maxIdx
        }
        return result
    }

    // ── Color averaging ───────────────────────────────────────────────────────

    private fun averageColor(
        crop: Bitmap,
        pixels: List<Pair<Int,Int>>,
        scaleX: Float,
        scaleY: Float,
        maxY: Int = Int.MAX_VALUE,  // exclude pixels below this Y (beard area)
    ): Int {
        if (pixels.isEmpty()) return Color.rgb(210, 180, 140)

        val step = maxOf(1, pixels.size / 800)
        val rs = mutableListOf<Int>()
        val gs = mutableListOf<Int>()
        val bs = mutableListOf<Int>()

        for (i in pixels.indices step step) {
            val (px, py) = pixels[i]
            if (py > maxY) continue                              // skip beard area

            val bx = (px * scaleX).toInt().coerceIn(0, crop.width  - 1)
            val by = (py * scaleY).toInt().coerceIn(0, crop.height - 1)
            val argb = crop.getPixel(bx, by)
            val r = (argb shr 16) and 0xFF
            val g = (argb shr  8) and 0xFF
            val b =  argb         and 0xFF

            // Plausibility: skip clearly non-skin pixels
            val brightness = (r + g + b) / 3
            if (brightness < 25 || brightness > 245) continue   // too dark/bright
            if (b > r + 20) continue                             // too blue
            if (g > r + 30) continue                             // too green

            rs.add(r); gs.add(g); bs.add(b)
        }

        if (rs.isEmpty()) return Color.rgb(210, 180, 140)

        rs.sort(); gs.sort(); bs.sort()
        val mid = rs.size / 2
        return Color.rgb(rs[mid], gs[mid], bs[mid])
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun bitmapToBgrMat(bitmap: Bitmap): Mat {
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val mat  = Mat(h, w, CvType.CV_8UC3)
        val data = ByteArray(w * h * 3)
        for (i in pixels.indices) {
            val px = pixels[i]
            data[i * 3]     = ( px          and 0xFF).toByte() // B
            data[i * 3 + 1] = ((px shr  8)  and 0xFF).toByte() // G
            data[i * 3 + 2] = ((px shr 16)  and 0xFF).toByte() // R
        }
        mat.put(0, 0, data)
        return mat
    }





    // ── Hair texture detection ────────────────────────────────────────────────

    /**
     * Detects hair texture (straight/wavy/curly) from BiSeNet hair mask.
     *
     * Strategy: Analyze edge density and horizontal variance within hair mask.
     * - Straight hair: smooth mask edges, low horizontal variance
     * - Wavy hair:     moderate edge density, medium variance
     * - Curly hair:    high edge density, many holes/gaps in mask, high variance
     */

    // ── Face shape detection ──────────────────────────────────────────────────

    /**
     * Detects face shape from BiSeNet skin mask.
     * Measures width at different heights and computes ratios.
     *
     * OVAL:   h/w > 1.15 (long narrow face)
     * SQUARE: jaw/cheek > 0.88 (wide jaw relative to cheeks)
     * ROUND:  otherwise
     */
    private fun detectFaceShape(skinPixels: List<Pair<Int,Int>>): FaceAttributeAnalyzer.FaceShape {
        if (skinPixels.size < 20) return FaceAttributeAnalyzer.FaceShape.OVAL

        val faceTop    = skinPixels.minOf { it.second }
        val faceBottom = skinPixels.maxOf { it.second }
        val faceH      = faceBottom - faceTop
        // Build row→minX/maxX map in one pass for widthAt
        val rowMinX = HashMap<Int, Int>(faceH + 1)
        val rowMaxX = HashMap<Int, Int>(faceH + 1)
        for ((px, py) in skinPixels) {
            rowMinX[py] = minOf(rowMinX.getOrDefault(py, INPUT_SIZE), px)
            rowMaxX[py] = maxOf(rowMaxX.getOrDefault(py, 0),          px)
        }
        fun widthAt(pct: Float): Int {
            val row = (faceTop + faceH * pct).toInt().coerceIn(0, INPUT_SIZE - 1)
            val mn  = rowMinX[row] ?: return 0
            val mx  = rowMaxX[row] ?: return 0
            return if (mx > mn) mx - mn else 0
        }
        val cheekW = widthAt(0.5f).coerceAtLeast(1)
        val jawW   = widthAt(0.8f)
        val faceW  = (skinPixels.maxOf { it.first } - skinPixels.minOf { it.first }).coerceAtLeast(1)

        val ratioHW   = faceH.toFloat() / faceW.coerceAtLeast(1)
        val jawCheek  = jawW.toFloat() / cheekW


        return when {
            ratioHW  > 1.15f -> FaceAttributeAnalyzer.FaceShape.OVAL
            jawCheek > 0.88f -> FaceAttributeAnalyzer.FaceShape.SQUARE
            else             -> FaceAttributeAnalyzer.FaceShape.ROUND
        }
    }

    // ── Eye color detection ───────────────────────────────────────────────────

    /**
     * Detects eye/iris color from BiSeNet eye mask pixels.
     * Filters out very dark (pupil) and very bright (white) pixels,
     * averages remaining iris pixels, then maps to nearest LPC color.
     */
    private fun detectEyeColor(
        crop: Bitmap,
        eyePixels: List<Pair<Int,Int>>,
        scaleX: Float,
        scaleY: Float,
    ): EyeColor {
        if (eyePixels.size < 20) return EyeColor.BROWN

        var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0
        val step = maxOf(1, eyePixels.size / 200)

        for (i in eyePixels.indices step step) {
            val (px, py) = eyePixels[i]
            val bx = (px * scaleX).toInt().coerceIn(0, crop.width  - 1)
            val by = (py * scaleY).toInt().coerceIn(0, crop.height - 1)
            val argb = crop.getPixel(bx, by)
            val r = (argb shr 16) and 0xFF
            val g = (argb shr  8) and 0xFF
            val b =  argb         and 0xFF
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            // Skip pupil (very dark) and sclera (very bright)
            if (luma < 40f || luma > 200f) continue
            rSum += r; gSum += g; bSum += b; count++
        }

        if (count == 0) return EyeColor.BROWN
        val r = (rSum / count).toInt()
        val g = (gSum / count).toInt()
        val b = (bSum / count).toInt()

        // Map to nearest LPC eye color
        // Adjusted for real iris colors after filtering pupil/sclera
        // Brown covers both dark and medium brown (common in Asian eyes)
        val candidates = mapOf(
            EyeColor.BROWN to  Triple(140, 95,  65),   // medium brown (Asian/dark)
            EyeColor.BLUE  to  Triple(80,  130, 200),  // blue iris
            EyeColor.GREEN to  Triple(80,  140, 80),   // green iris
            EyeColor.GRAY  to  Triple(160, 155, 150),  // gray (lighter, less warm)
            EyeColor.AMBER to  Triple(190, 130, 50),   // amber/hazel
        )
        return candidates.minByOrNull { (_, c) ->
            val dr = r - c.first; val dg = g - c.second; val db = b - c.third
            dr*dr + dg*dg + db*db
        }?.key ?: EyeColor.BROWN
    }

    // ── Clothing style estimation ─────────────────────────────────────────────

    /**
     * Estimates clothing style from color brightness and accessories.
     * Dark clothing → FORMAL, necklace → ELEGANT/CASUAL, bright → CASUAL
     */
    private fun estimateClothingStyle(
        clothingColor: Int,
        hasNecklace: Boolean,
        neckCount: Int,
    ): ClothingStyle {
        val r = (clothingColor shr 16) and 0xFF
        val g = (clothingColor shr  8) and 0xFF
        val b =  clothingColor         and 0xFF
        val brightness = 0.299f * r + 0.587f * g + 0.114f * b

        return when {
            brightness < 80f              -> ClothingStyle.FORMAL   // dark = suit/formal
            hasNecklace && brightness > 150f -> ClothingStyle.CASUAL // necklace + bright = casual elegant
            brightness > 160f             -> ClothingStyle.CASUAL
            else                          -> ClothingStyle.SPORTY
        }
    }

    fun release() {
        net = null
    }


}