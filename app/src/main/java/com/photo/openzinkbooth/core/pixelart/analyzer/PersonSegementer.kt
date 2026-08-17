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
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.*
import org.opencv.dnn.Dnn
import org.opencv.dnn.Net
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream

/**
 * PersonSegmenter
 *
 * Uses OpenCV DNN + MediaPipe Selfie Segmentation General Model (244 KB)
 * Input:  256×256×3 RGB, normalized [0,1]
 * Output: 256×256×1 confidence mask (1.0 = person, 0.0 = background)
 *
 * Model: selfie_segmentation.tflite (Apache 2.0, ~244 KB)
 * https://storage.googleapis.com/mediapipe-assets/selfie_segmentation.tflite
 */
class PersonSegmenter(private val context: Context) {

    private var net: Net? = null

    companion object {
        private const val TAG       = "PersonSegmenter"
        private const val MODEL     = "model/selfie_segmenter.tflite"
        private const val INPUT_W   = 256
        private const val INPUT_H   = 256
        private const val THRESHOLD = 0.5f
    }

    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        if (net != null) return@withContext true
        try {
            val modelFile = File(context.cacheDir, MODEL).also { it.parentFile?.mkdirs() }
            val assetSize = context.assets.openFd(MODEL).length
            if (!modelFile.exists() || modelFile.length() != assetSize) {
                Log.d(TAG, "Copying model from assets (asset size: $assetSize bytes)...")
                context.assets.open(MODEL).use { inp ->
                    FileOutputStream(modelFile).use { out -> inp.copyTo(out) }
                }
            }
            Log.d(TAG, "Loading model: ${modelFile.absolutePath} (${modelFile.length()} bytes)")
            net = Dnn.readNetFromTFLite(modelFile.absolutePath)
            Log.d(TAG, "Model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}", e)
            false
        }
    }

    suspend fun segment(source: Bitmap): Bitmap? = withContext(Dispatchers.IO) {
        val net  = net ?: return@withContext null
        val origW = source.width
        val origH = source.height

        try {
            // 1. Bitmap → RGB Mat
            val rgbMat = bitmapToRgbMat(source)

            // 2. Resize to 256×256
            val resized = Mat()
            Imgproc.resize(rgbMat, resized, Size(INPUT_W.toDouble(), INPUT_H.toDouble()))

            // 3. Normalize [0,1] and create blob — no mean subtraction, no channel swap
            resized.convertTo(resized, CvType.CV_32FC3, 1.0 / 255.0)
            val blob = Dnn.blobFromImage(resized)

            // 4. Inference
            net.setInput(blob)
            val output = net.forward()
            Log.d(TAG, "Output dims=${output.dims()} size=${output.size()}")

            // 5. Output shape [1,1,256,256] → reshape to single 2D mask
            val mask2D = output.reshape(1, INPUT_H)

            // 6. Resize mask to original image size
            val maskResized = Mat()
            Imgproc.resize(mask2D, maskResized, Size(origW.toDouble(), origH.toDouble()))

            // 7. Apply mask: person pixels kept, background → transparent
            val pixels = IntArray(origW * origH)
            source.getPixels(pixels, 0, origW, 0, 0, origW, origH)

            val maskData = FloatArray(origW * origH)
            maskResized.get(0, 0, maskData)

            val result = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)
            for (i in pixels.indices) {
                result.setPixel(
                    i % origW, i / origW,
                    if (maskData[i] > THRESHOLD) pixels[i] else Color.TRANSPARENT
                )
            }

            Log.d(TAG, "Segmentation done: ${origW}x${origH}")
            result

        } catch (e: Exception) {
            Log.e(TAG, "Segmentation failed: ${e.message}", e)
            null
        }
    }

    private fun bitmapToRgbMat(bitmap: Bitmap): Mat {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val mat  = Mat(h, w, CvType.CV_8UC3)
        val data = ByteArray(w * h * 3)
        for (i in pixels.indices) {
            val px = pixels[i]
            data[i * 3]     = ((px shr 16) and 0xFF).toByte() // R
            data[i * 3 + 1] = ((px shr  8) and 0xFF).toByte() // G
            data[i * 3 + 2] = ( px          and 0xFF).toByte() // B
        }
        mat.put(0, 0, data)
        return mat
    }

    fun release() {
        net = null
        Log.d(TAG, "Released")
    }
}