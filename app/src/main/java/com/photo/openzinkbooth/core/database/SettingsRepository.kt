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
import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.photo.openzinkbooth.ui.viewmodel.RemoteShutterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// One DataStore instance per process
internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "openZinkBoothSettings"
)

// ---------------------------------------------------------------------------
// SettingsData – snapshot emitted by the settings Flow
// ---------------------------------------------------------------------------
data class SettingsData(
    val dynamicColor: Boolean        = false,
    val useFrontCamera: Boolean      = true,
    val flashEnabled: Boolean        = false,
    val shutterSoundEnabled: Boolean = false,
    val storageUriString: String?    = null,
    val timerSeconds: Int            = 0,
    val paperCount: Int              = -1,
    // Last used filter/frame – restored on next capture
    val selectedFilter: String       = "ORIGINAL",
    val selectedFrame: String        = "NONE",
    // Last successfully connected printer – used for auto-connect on next launch
    val pairedPrinterAddress: String? = null,
    val pairedPrinterName: String?    = null,
    // Printer output calibration (Sprocket 200 default values)
    val calibrationEnabled: Boolean  = true,
    val calibrationVScale: Float     = 0.9524f,  // 1/1.05 – vertical compression
    val calibrationVOffset: Int      = 46,        // top padding in pixels
    // Bluetooth remote shutter
    val remoteShutterEnabled: Boolean = false,
    val remoteShutterKey: String      = "VOLUME_UP",
    val pixelArtMode: Boolean         = false,
) {
    val storageUri: Uri? get() = storageUriString?.toUri()
}

// ---------------------------------------------------------------------------
// SettingsRepository
// ---------------------------------------------------------------------------
class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_DYNAMIC_COLOR      = booleanPreferencesKey("dynamic_color")
        private val KEY_FRONT_CAMERA       = booleanPreferencesKey("front_camera")
        private val KEY_FLASH              = booleanPreferencesKey("flash")
        private val KEY_SHUTTER_SOUND      = booleanPreferencesKey("shutter_sound")
        private val KEY_STORAGE_URI        = stringPreferencesKey("storage_uri")
        private val KEY_TIMER              = intPreferencesKey("timer_seconds")
        private val KEY_PAPER_COUNT        = intPreferencesKey("paper_count")
        private val KEY_FILTER             = stringPreferencesKey("selected_filter")
        private val KEY_FRAME              = stringPreferencesKey("selected_frame")
        private val KEY_PAIRED_ADDRESS     = stringPreferencesKey("paired_printer_address")
        private val KEY_PAIRED_NAME        = stringPreferencesKey("paired_printer_name")
        private val KEY_CALIB_ENABLED      = booleanPreferencesKey("calib_enabled")
        private val KEY_CALIB_V_SCALE      = floatPreferencesKey("calib_v_scale")
        private val KEY_CALIB_V_OFFSET     = intPreferencesKey("calib_v_offset")
        private val KEY_REMOTE_SHUTTER     = booleanPreferencesKey("remote_shutter_enabled")
        private val KEY_REMOTE_SHUTTER_KEY = stringPreferencesKey("remote_shutter_key")
        private val KEY_PIXEL_ART_MODE     = booleanPreferencesKey("pixel_art_mode")
    }

    val settings: Flow<SettingsData> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            SettingsData(
                dynamicColor          = prefs[KEY_DYNAMIC_COLOR]   ?: false,
                useFrontCamera        = prefs[KEY_FRONT_CAMERA]    ?: true,
                flashEnabled          = prefs[KEY_FLASH]           ?: false,
                shutterSoundEnabled   = prefs[KEY_SHUTTER_SOUND]   ?: false,
                storageUriString      = prefs[KEY_STORAGE_URI],
                timerSeconds          = prefs[KEY_TIMER]           ?: 0,
                paperCount            = prefs[KEY_PAPER_COUNT]     ?: -1,
                selectedFilter        = prefs[KEY_FILTER]          ?: "ORIGINAL",
                selectedFrame         = prefs[KEY_FRAME]           ?: "NONE",
                pairedPrinterAddress  = prefs[KEY_PAIRED_ADDRESS],
                pairedPrinterName     = prefs[KEY_PAIRED_NAME],
                calibrationEnabled    = prefs[KEY_CALIB_ENABLED]   ?: true,
                calibrationVScale     = prefs[KEY_CALIB_V_SCALE]   ?: 0.9524f,
                calibrationVOffset    = prefs[KEY_CALIB_V_OFFSET]  ?: 46,
                remoteShutterEnabled  = prefs[KEY_REMOTE_SHUTTER]  ?: false,
                remoteShutterKey      = prefs[KEY_REMOTE_SHUTTER_KEY] ?: RemoteShutterKey.VOLUME_UP.name,
                pixelArtMode          = prefs[KEY_PIXEL_ART_MODE]      ?: false,
            )
        }

    suspend fun setCalibrationEnabled(enabled: Boolean) =
        context.dataStore.edit { it[KEY_CALIB_ENABLED] = enabled }

    suspend fun setCalibrationVScale(scale: Float) =
        context.dataStore.edit { it[KEY_CALIB_V_SCALE] = scale }

    suspend fun setCalibrationVOffset(offset: Int) =
        context.dataStore.edit { it[KEY_CALIB_V_OFFSET] = offset }

    suspend fun setRemoteShutterEnabled(enabled: Boolean) =
        context.dataStore.edit { it[KEY_REMOTE_SHUTTER] = enabled }

    suspend fun setRemoteShutterKey(key: String) =
        context.dataStore.edit { it[KEY_REMOTE_SHUTTER_KEY] = key }

    suspend fun setDynamicColor(enabled: Boolean) =
        context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }

    suspend fun setFrontCamera(front: Boolean) =
        context.dataStore.edit { it[KEY_FRONT_CAMERA] = front }

    suspend fun setFlash(enabled: Boolean) =
        context.dataStore.edit { it[KEY_FLASH] = enabled }

    suspend fun setShutterSound(enabled: Boolean) =
        context.dataStore.edit { it[KEY_SHUTTER_SOUND] = enabled }

    suspend fun setStorageUri(uri: Uri?) = context.dataStore.edit {
        if (uri != null) it[KEY_STORAGE_URI] = uri.toString()
        else it.remove(KEY_STORAGE_URI)
    }

    suspend fun setTimer(seconds: Int) =
        context.dataStore.edit { it[KEY_TIMER] = seconds }

    /** Called when user confirms paper refill – resets counter to full pack (10 sheets). */
    suspend fun resetPaperCount() =
        context.dataStore.edit { it[KEY_PAPER_COUNT] = 10 }

    /** Called after each successful print – decrements counter (floor at 0). */
    suspend fun decrementPaperCount() = context.dataStore.edit { prefs ->
        val current = prefs[KEY_PAPER_COUNT] ?: -1
        if (current > 0) prefs[KEY_PAPER_COUNT] = current - 1
    }

    suspend fun setPixelArtMode(enabled: Boolean) =
        context.dataStore.edit { it[KEY_PIXEL_ART_MODE] = enabled }

    suspend fun setFilter(filter: String) =
        context.dataStore.edit { it[KEY_FILTER] = filter }

    suspend fun setFrame(frame: String) =
        context.dataStore.edit { it[KEY_FRAME] = frame }

    /** Persists the BLE address and display name of a successfully connected printer. */
    suspend fun savePairedPrinter(address: String, displayName: String) =
        context.dataStore.edit {
            it[KEY_PAIRED_ADDRESS] = address
            it[KEY_PAIRED_NAME]    = displayName
        }

    /** Clears the paired printer so the next launch performs a fresh scan. */
    suspend fun clearPairedPrinter() =
        context.dataStore.edit {
            it.remove(KEY_PAIRED_ADDRESS)
            it.remove(KEY_PAIRED_NAME)
        }
}