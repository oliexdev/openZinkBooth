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
import android.graphics.PointF
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
 * FaceAttributeAnalyzer
 *
 * Uses InsightFace buffalo_l genderage.onnx to detect gender and age.
 * Input:  [1, 3, 96, 96] BGR, mean=0, std=1 (MXNet model)
 * Output: [1, 3] → argmax(pred[:2]) = gender (0=F,1=M), pred[2]*100 = age
 *
 * Preprocessing matches InsightFace attribute.py face_align.transform():
 *   scale = GA_SIZE / (max(w,h) * 1.5)
 *   center crop with scale+translate, no rotation
 */
class FaceAttributeAnalyzer(private val context: Context) {

    private var genderAgeNet: Net? = null

    companion object {
        private const val TAG              = "FaceAttributeAnalyzer"
        private const val GENDER_AGE_MODEL = "model/genderage.onnx"
        private const val GA_SIZE          = 96
    }

    data class FaceAttributes(
        val gender:   Gender,
        val ageGroup: AgeGroup,
        val ageYears: Int,
    )

    enum class Gender   { MALE, FEMALE }
    enum class AgeGroup { CHILD, TEEN, ADULT, SENIOR }
    enum class FaceShape { OVAL, ROUND, SQUARE }

    // ── Init ──────────────────────────────────────────────────────────────────

    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (genderAgeNet == null) genderAgeNet = loadModel(GENDER_AGE_MODEL)
            genderAgeNet != null
        } catch (e: Exception) {
            LogManager.e(TAG, "Init failed: ${e.message}", e)
            false
        }
    }

    private fun loadModel(filename: String): Net {
        val file = File(context.cacheDir, filename).also { it.parentFile?.mkdirs() }
        if (!file.exists() || file.length() == 0L) {
            LogManager.d(TAG, "Copying $filename from assets...")
            context.assets.open(filename).use { inp ->
                FileOutputStream(file).use { out -> inp.copyTo(out) }
            }
        }
        LogManager.d(TAG, "Loading $filename (${file.length()} bytes)")
        return Dnn.readNetFromONNX(file.absolutePath)
    }

    // ── Main analysis ─────────────────────────────────────────────────────────

    suspend fun analyze(
        source:   Bitmap,
        faceRect: RectF,
    ): FaceAttributes = withContext(Dispatchers.IO) {
        val alignedCrop = cropForGenderAge(source, faceRect)
        val (gender, ageYears) = detectGenderAge(alignedCrop)
        val ageGroup = when {
            ageYears < 13 -> AgeGroup.CHILD
            ageYears < 20 -> AgeGroup.TEEN
            ageYears < 60 -> AgeGroup.ADULT
            else          -> AgeGroup.SENIOR
        }
        FaceAttributes(gender, ageGroup, ageYears)
    }

    // ── Face crop (InsightFace face_align.transform) ───────────────────────────

    /**
     * Crops face using InsightFace attribute.py preprocessing:
     *   scale = GA_SIZE / (max(w,h) * 1.5)
     *   warpAffine with scale+translate to center, rotation=0
     */
    private fun cropForGenderAge(source: Bitmap, faceRect: RectF): Bitmap {
        val w  = faceRect.width()
        val h  = faceRect.height()
        val cx = faceRect.centerX()
        val cy = faceRect.centerY()
        val scale = GA_SIZE.toFloat() / (maxOf(w, h) * 1.5f)
        val tx = GA_SIZE / 2.0 - cx * scale
        val ty = GA_SIZE / 2.0 - cy * scale

        val M = Mat(2, 3, CvType.CV_64FC1)
        M.put(0, 0, scale.toDouble(), 0.0, tx)
        M.put(1, 0, 0.0, scale.toDouble(), ty)

        val srcMat  = bitmapToBgrMat(source)
        val aligned = Mat()
        Imgproc.warpAffine(srcMat, aligned, M,
            Size(GA_SIZE.toDouble(), GA_SIZE.toDouble()))

        val result = Bitmap.createBitmap(GA_SIZE, GA_SIZE, Bitmap.Config.ARGB_8888)
        val data   = ByteArray(GA_SIZE * GA_SIZE * 3)
        aligned.get(0, 0, data)
        val pixels = IntArray(GA_SIZE * GA_SIZE)
        for (i in pixels.indices) {
            val b = data[i * 3].toInt()     and 0xFF
            val g = data[i * 3 + 1].toInt() and 0xFF
            val r = data[i * 3 + 2].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        result.setPixels(pixels, 0, GA_SIZE, 0, 0, GA_SIZE, GA_SIZE)
        return result
    }

    // ── Gender + Age ──────────────────────────────────────────────────────────

    private fun detectGenderAge(faceCrop: Bitmap): Pair<Gender, Int> {
        val net = genderAgeNet ?: return Pair(Gender.MALE, 25)
        return try {
            val resized = Mat()
            val bgrMat  = bitmapToBgrMat(faceCrop)
            Imgproc.resize(bgrMat, resized, Size(GA_SIZE.toDouble(), GA_SIZE.toDouble()))

            // MXNet model: mean=0, std=1, swapRB=false (BGR input)
            val blob = Dnn.blobFromImage(
                resized, 1.0,
                Size(GA_SIZE.toDouble(), GA_SIZE.toDouble()),
                Scalar(0.0, 0.0, 0.0),
                false, false
            )
            net.setInput(blob)
            val out  = net.forward()
            val data = FloatArray(3)
            out.get(0, 0, data)

            // argmax(pred[:2]): index 0=Female, index 1=Male
            val gender   = if (data[1] > data[0]) Gender.MALE else Gender.FEMALE
            val ageYears = Math.round(data[2] * 100f).toInt().coerceIn(1, 100)
            Pair(gender, ageYears)
        } catch (e: Exception) {
            LogManager.e(TAG, "GenderAge failed: ${e.message}")
            Pair(Gender.MALE, 25)
        }
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

    fun release() {
        genderAgeNet = null
    }

}