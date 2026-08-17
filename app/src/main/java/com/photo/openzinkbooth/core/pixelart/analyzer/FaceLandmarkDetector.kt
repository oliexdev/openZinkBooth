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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN
import java.io.File
import java.io.FileOutputStream

/**
 * FaceLandmarkDetector
 *
 * Uses OpenCV FaceDetectorYN (YuNet) for face detection.
 * FaceDetectorYN is the correct OpenCV API for YuNet — do NOT use
 * Dnn.readNetFromONNX for this model.
 *
 * Output per face (15 values):
 *   x, y, w, h,
 *   re_x, re_y,   (right eye)
 *   le_x, le_y,   (left eye)
 *   nt_x, nt_y,   (nose tip)
 *   rcm_x, rcm_y, (right mouth corner)
 *   lcm_x, lcm_y, (left mouth corner)
 *   confidence
 *
 * Model: face_detection_yunet_2023mar.onnx (Apache 2.0, ~228 KB)
 * https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet
 */
class FaceLandmarkDetector(private val context: Context) {

    private var detector: FaceDetectorYN? = null

    companion object {
        private const val TAG             = "FaceLandmarkDetector"
        private const val DETECTION_MODEL = "model/face_detection.onnx"
        private const val DETECT_W        = 320
        private const val DETECT_H        = 320
        private const val SCORE_THRESHOLD = 0.6f
        private const val NMS_THRESHOLD   = 0.3f
        private const val TOP_K           = 5000
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        if (detector != null) return@withContext true
        try {
            val modelFile = copyAssetToCache(DETECTION_MODEL)
            Log.d(TAG, "Creating FaceDetectorYN from: ${modelFile.absolutePath}")
            detector = FaceDetectorYN.create(
                modelFile.absolutePath,
                "",
                Size(DETECT_W.toDouble(), DETECT_H.toDouble()),
                SCORE_THRESHOLD,
                NMS_THRESHOLD,
                TOP_K
            )
            Log.d(TAG, "FaceDetectorYN created successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Init failed: ${e.message}", e)
            false
        }
    }

    private fun copyAssetToCache(filename: String): File {
        val file = File(context.cacheDir, filename).also { it.parentFile?.mkdirs() }
        if (!file.exists() || file.length() == 0L) {
            Log.d(TAG, "Copying $filename from assets...")
            context.assets.open(filename).use { inp ->
                FileOutputStream(file).use { out -> inp.copyTo(out) }
            }
        }
        Log.d(TAG, "Model file: ${file.absolutePath} (${file.length()} bytes)")
        return file
    }

    // ── Detection ─────────────────────────────────────────────────────────────

    data class FaceResult(
        val boundingBox: RectF,
        val landmarks: List<PointF>,  // 5 key points: re, le, nose, rcm, lcm
        val confidence: Float,
    )

    suspend fun detect(source: Bitmap): List<FaceResult> = withContext(Dispatchers.IO) {
        val det = detector ?: return@withContext emptyList()

        val origW = source.width
        val origH = source.height

        try {
            // Convert Bitmap → BGR Mat
            val bgrMat = bitmapToBgrMat(source)

            // Uniform scale preserving aspect ratio — avoids non-uniform distortion
            val scaleUniform = minOf(DETECT_W.toFloat() / origW, DETECT_H.toFloat() / origH)
            val scaledW = (origW * scaleUniform).toInt()
            val scaledH = (origH * scaleUniform).toInt()

            val resized = Mat()
            Imgproc.resize(bgrMat, resized, Size(scaledW.toDouble(), scaledH.toDouble()))

            // Pad to DETECT_W × DETECT_H with black border
            val padded = Mat.zeros(DETECT_H, DETECT_W, resized.type())
            resized.copyTo(padded.submat(0, scaledH, 0, scaledW))

            det.setInputSize(Size(DETECT_W.toDouble(), DETECT_H.toDouble()))

            // Run detection
            val faces = Mat()
            det.detect(padded, faces)

            Log.d(TAG, "Faces mat: rows=${faces.rows()} cols=${faces.cols()} scale=$scaleUniform")

            // Scale back using uniform factor
            val scaleX = 1.0f / scaleUniform
            val scaleY = 1.0f / scaleUniform
            val results = mutableListOf<FaceResult>()

            for (i in 0 until faces.rows()) {
                val data = FloatArray(faces.cols())
                faces.get(i, 0, data)

                // Last column is confidence
                val conf = data[data.size - 1]
                Log.d(TAG, "Face $i raw: ${data.toList()}, conf=$conf")

                val x = data[0] * scaleX
                val y = data[1] * scaleY
                val w = data[2] * scaleX
                val h = data[3] * scaleY

                val faceRect = RectF(
                    x.coerceAtLeast(0f),
                    y.coerceAtLeast(0f),
                    (x + w).coerceAtMost(origW.toFloat()),
                    (y + h).coerceAtMost(origH.toFloat())
                )

                // Landmarks: indices 4-13 (5 points × 2 coords)
                val landmarks = mutableListOf<PointF>()
                var li = 4
                while (li + 1 <= 13 && li + 1 < data.size - 1) {
                    landmarks.add(PointF(data[li] * scaleX, data[li + 1] * scaleY))
                    li += 2
                }

                results.add(FaceResult(faceRect, landmarks, conf))
                Log.d(TAG, "Face $i: box=$faceRect conf=$conf landmarks=${landmarks.size}")
            }

            Log.d(TAG, "Total: ${results.size} face(s)")
            results

        } catch (e: Exception) {
            Log.e(TAG, "Detection failed: ${e.message}", e)
            emptyList()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun bitmapToBgrMat(bitmap: Bitmap): Mat {
        val w = bitmap.width
        val h = bitmap.height
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
        detector = null
        Log.d(TAG, "Released")
    }
}