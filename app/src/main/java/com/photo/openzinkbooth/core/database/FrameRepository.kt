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

package com.photo.openzinkbooth.core.database

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.photo.openzinkbooth.core.utils.LogManager
import com.photo.openzinkbooth.ui.viewmodel.FrameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

// ---------------------------------------------------------------------------
// FrameEntry – represents one item in the frame picker list.
//
// Built-in frames reference a FrameType enum value.
// Custom frames reference a PNG file in filesDir/frames/<filename>.png
// and use the filename (without extension) as the display label.
// ---------------------------------------------------------------------------

sealed class FrameEntry {
    abstract val id: String          // unique stable key
    abstract val label: String       // display name
    abstract val visible: Boolean

    data class BuiltIn(
        val frameType: FrameType,
        override val visible: Boolean = true,
    ) : FrameEntry() {
        override val id: String    get() = frameType.name
        override val label: String get() = frameType.name  // resolved to string resource in UI
    }

    data class Custom(
        val filename: String,        // e.g. "wedding" → file: frames/wedding.png
        override val visible: Boolean = true,
    ) : FrameEntry() {
        override val id: String    get() = "custom:$filename"
        override val label: String get() = filename
    }
}

// ---------------------------------------------------------------------------
// FrameRepository
// ---------------------------------------------------------------------------

private val KEY_FRAME_CONFIG = stringPreferencesKey("frame_config")

/**
 * Sanitises a user-entered frame name into a safe filename (without extension).
 * Shared by the repository and the UI so the duplicate-name check matches what
 * is actually stored on disk.
 */
fun sanitizeFrameName(name: String): String =
    name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(40)

private const val TAG = "FrameRepository"
private const val FRAMES_DIR = "frames"

// Default order of built-in frames (NONE is never in this list – it's always
// prepended by the UI and not managed here).
private val DEFAULT_BUILTIN_ORDER = listOf(
    FrameType.CLASSIC,
    FrameType.ELEGANT,
    FrameType.FILMSTRIP,
    FrameType.CONFETTI,
    FrameType.HEARTS,
)

class FrameRepository(private val context: Context) {

    // The frames directory inside the app's private storage
    private val framesDir: File
        get() = File(context.filesDir, FRAMES_DIR).also { it.mkdirs() }

    // ---------------------------------------------------------------------------
    // Flow of the current frame list (built-ins + custom, in user-defined order)
    // ---------------------------------------------------------------------------

    val frames: Flow<List<FrameEntry>> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }
        .map { prefs ->
            val json = prefs[KEY_FRAME_CONFIG]
            if (json.isNullOrBlank()) defaultFrameList()
            else parseFrameList(json)
        }

    // ---------------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------------

    private suspend fun save(entries: List<FrameEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
            when (entry) {
                is FrameEntry.BuiltIn -> {
                    obj.put("type", "builtin")
                    obj.put("id", entry.frameType.name)
                    obj.put("visible", entry.visible)
                }
                is FrameEntry.Custom -> {
                    obj.put("type", "custom")
                    obj.put("filename", entry.filename)
                    obj.put("visible", entry.visible)
                }
            }
            array.put(obj)
        }
        context.dataStore.edit { it[KEY_FRAME_CONFIG] = array.toString() }
    }

    private fun parseFrameList(json: String): List<FrameEntry> {
        return try {
            val array = JSONArray(json)
            val result = mutableListOf<FrameEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                when (obj.getString("type")) {
                    "builtin" -> {
                        val ft = FrameType.entries.firstOrNull { it.name == obj.getString("id") }
                            ?: continue
                        result.add(FrameEntry.BuiltIn(ft, obj.optBoolean("visible", true)))
                    }
                    "custom" -> {
                        val filename = obj.getString("filename")
                        // Only include if the file still exists
                        if (File(framesDir, "$filename.png").exists()) {
                            result.add(FrameEntry.Custom(filename, obj.optBoolean("visible", true)))
                        }
                    }
                }
            }
            // Append any new built-in frames that aren't in the saved config yet
            val existingBuiltInIds = result.filterIsInstance<FrameEntry.BuiltIn>()
                .map { it.frameType }.toSet()
            DEFAULT_BUILTIN_ORDER.forEach { ft ->
                if (ft !in existingBuiltInIds) result.add(FrameEntry.BuiltIn(ft))
            }
            result
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to parse frame config: ${e.message}", e)
            defaultFrameList()
        }
    }

    private fun defaultFrameList(): List<FrameEntry> =
        DEFAULT_BUILTIN_ORDER.map { FrameEntry.BuiltIn(it) }

    // ---------------------------------------------------------------------------
    // CRUD operations
    // ---------------------------------------------------------------------------

    /** Reorders and saves the full frame list. */
    suspend fun saveOrder(entries: List<FrameEntry>) = save(entries)

    /** Toggles the visibility of a frame entry. */
    suspend fun setVisible(entryId: String, visible: Boolean) {
        val current = currentList()
        save(current.map { e ->
            when {
                e.id == entryId && e is FrameEntry.BuiltIn -> e.copy(visible = visible)
                e.id == entryId && e is FrameEntry.Custom  -> e.copy(visible = visible)
                else -> e
            }
        })
    }

    /**
     * Imports a PNG from the given [uri] into filesDir/frames/<name>.png.
     * [name] becomes the display label (and filename without extension).
     * Returns the new [FrameEntry.Custom] or null on failure.
     */
    suspend fun addCustomFrame(uri: Uri, name: String): FrameEntry.Custom? =
        withContext(Dispatchers.IO) {
            try {
                // Duplicate names are rejected in the UI (FrameManagerScreen), so a
                // collision here would be a programming error; we just overwrite.
                val sanitized = sanitizeFrameName(name)
                val file = File(framesDir, "$sanitized.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null

                val entry = FrameEntry.Custom(sanitized)
                val current = currentList()
                save(current + entry)
                LogManager.d(TAG, "Added custom frame: $sanitized")
                entry
            } catch (e: Exception) {
                LogManager.e(TAG, "Failed to add custom frame: ${e.message}", e)
                null
            }
        }

    /**
     * Renames a custom frame (renames the file and updates the config).
     */
    suspend fun renameCustomFrame(oldFilename: String, newName: String) =
        withContext(Dispatchers.IO) {
            // Duplicate names are rejected in the UI (FrameManagerScreen).
            val sanitized = sanitizeFrameName(newName)
            val oldFile = File(framesDir, "$oldFilename.png")
            val newFile = File(framesDir, "$sanitized.png")
            if (oldFile.exists()) oldFile.renameTo(newFile)

            val current = currentList()
            save(current.map { e ->
                if (e is FrameEntry.Custom && e.filename == oldFilename)
                    e.copy(filename = sanitized)
                else e
            })
        }

    /**
     * Deletes a custom frame file and removes it from the config.
     */
    suspend fun deleteCustomFrame(filename: String) = withContext(Dispatchers.IO) {
        File(framesDir, "$filename.png").delete()
        val current = currentList()
        save(current.filter { !(it is FrameEntry.Custom && it.filename == filename) })
    }

    /**
     * Loads the Bitmap for a custom frame, or null if the file is missing.
     */
    fun loadCustomBitmap(filename: String): Bitmap? {
        val file = File(framesDir, "$filename.png")
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    // ---------------------------------------------------------------------------
    // Helper – read current snapshot without subscribing to the Flow
    // ---------------------------------------------------------------------------

    private suspend fun currentList(): List<FrameEntry> =
        withContext(Dispatchers.IO) {
            try {
                val prefs = context.dataStore.data.first()
                val json = prefs[KEY_FRAME_CONFIG]
                if (json.isNullOrBlank()) defaultFrameList() else parseFrameList(json)
            } catch (e: Exception) {
                defaultFrameList()
            }
        }
}