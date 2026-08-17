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

package com.photo.openzinkbooth.ui.viewmodel

import android.graphics.Bitmap
import com.photo.openzinkbooth.core.pixelart.PixelArtAnalyzer
import com.photo.openzinkbooth.core.pixelart.sprite.EquipmentRandomizer
import android.net.Uri
import com.photo.openzinkbooth.R
import com.photo.openzinkbooth.core.ble.PrintStatus

// ---------------------------------------------------------------------------
// Remote shutter key options
// ---------------------------------------------------------------------------
enum class RemoteShutterKey(val keyCode: Int, val labelRes: Int) {
    VOLUME_UP(android.view.KeyEvent.KEYCODE_VOLUME_UP,         R.string.settings_remote_key_volume_up),
    VOLUME_DOWN(android.view.KeyEvent.KEYCODE_VOLUME_DOWN,     R.string.settings_remote_key_volume_down),
    CAMERA(android.view.KeyEvent.KEYCODE_CAMERA,               R.string.settings_remote_key_camera),
    MEDIA_PLAY(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, R.string.settings_remote_key_media_play);

    companion object {
        fun fromName(name: String) = entries.firstOrNull { it.name == name } ?: VOLUME_UP
    }
}

// ---------------------------------------------------------------------------
// Navigation screens
// ---------------------------------------------------------------------------
enum class Screen {
    CAMERA, PREVIEW, SETTINGS, PRINTER, PRINTER_CONFIG, ABOUT, FRAME_MANAGER,
    PIXEL_ART_PREVIEW  // Pixel Art mode preview screen
}

// ---------------------------------------------------------------------------
// Filter / Frame enums – label via string resource for i18n
// ---------------------------------------------------------------------------
enum class FilterType(val labelRes: Int) {
    ORIGINAL(R.string.filter_original),
    BW(R.string.filter_bw),
    VINTAGE(R.string.filter_vintage),
    VIVID(R.string.filter_vivid),
    FADED(R.string.filter_faded),
    COOL(R.string.filter_cool)
}

enum class FrameType(val labelRes: Int) {
    NONE(R.string.frame_none),
    CLASSIC(R.string.frame_classic),
    ELEGANT(R.string.frame_elegant),
    FILMSTRIP(R.string.frame_filmstrip),
    CONFETTI(R.string.frame_confetti),
    HEARTS(R.string.frame_hearts)
}

data class PrintJob(
    val id: String,
    val photo: Bitmap,
    val filter: FilterType,
    val frame: FrameType,
    val customFrameId: String? = null,   // non-null when a custom frame is selected
    val compositedBitmap: Bitmap? = null, // pre-composited bitmap ready to print
    val timestamp: Long = System.currentTimeMillis()
)

// ---------------------------------------------------------------------------
// Manual scan result – one entry per discovered BLE peripheral
// ---------------------------------------------------------------------------
data class ManualScanDevice(
    val peripheral: com.welie.blessed.BluetoothPeripheral,
    val model: com.photo.openzinkbooth.core.ble.SprocketModel,
    val deviceName: String,
) {
    val address: String get() = peripheral.address
}

// ---------------------------------------------------------------------------
// Printer connection state – separate from SprocketState so UI can add
// transient states (e.g. errorMessage that auto-clears).
// ---------------------------------------------------------------------------
enum class PrinterConnectionState {
    DISCONNECTED,
    SCANNING,                    // BLE scan active
    CONNECTING,                  // BLE connection in progress
    READY,                       // session established, probe completed
    NOT_FOUND,                   // scan completed without finding any printer
    BLUETOOTH_DISABLED,          // Bluetooth is turned off on the device
    BLUETOOTH_PERMISSION_DENIED, // BLUETOOTH_SCAN or BLUETOOTH_CONNECT not granted
    ERROR                        // transient; auto-reverts to DISCONNECTED after 4s
}

// ---------------------------------------------------------------------------
// Pixel Art analysis state
// ---------------------------------------------------------------------------
enum class PixelArtAnalysisState {
    IDLE,       // no analysis started
    ANALYSING,  // pipeline running
    DONE,       // analysis complete
    ERROR,      // analysis failed
}

// ---------------------------------------------------------------------------
// ZinkUiState – complete UI state snapshot
// ---------------------------------------------------------------------------
data class ZinkUiState(
    val screen: Screen             = Screen.CAMERA,
    val drawerOpen: Boolean        = false,

    // ── Capture ──────────────────────────────────────────────────────────────
    val capturedPhoto: Bitmap?     = null,
    val selectedFilter: FilterType = FilterType.ORIGINAL,
    val selectedFrame: FrameType   = FrameType.NONE,
    // Non-null when a custom frame is selected (overrides selectedFrame)
    val selectedCustomFrameId: String? = null,
    val timerSeconds: Int          = 0,
    val countdown: Int?            = null,

    // ── Print ─────────────────────────────────────────────────────────────────
    val printQueue: List<PrintJob> = emptyList(),
    val currentPrintProgress: Int  = 0,   // 0–100
    // Non-null while a print job error is being shown in the PrintBar.
    // Auto-clears after 4s via ZinkBoothViewModel.startPrintQueueIfIdle().
    val printError: String?        = null,

    // ── Printer connection ────────────────────────────────────────────────────
    val printerConnectionState: PrinterConnectionState = PrinterConnectionState.DISCONNECTED,
    val printerModelName: String   = "",          // populated from settings or after connect
    val printerPrintWidth: Int     = 640,         // native print width in pixels
    val printerPrintHeight: Int    = 1002,         // native print height in pixels
    val printerError: String?      = null,        // shown while state == ERROR

    // ── Printer status (from StatusSnapshot after connect) ────────────────────
    // batteryLevel: 0–100, null = unknown
    val batteryLevel: Int?         = null,
    // paperEmpty: true when printer reports any out-of-paper condition
    val paperEmpty: Boolean        = false,
    // printStatus: last known printer status, used to drive the connection pill
    val printStatus: PrintStatus = PrintStatus.UNKNOWN,
    // paperCount: remaining sheets, -1 = never refilled so don't show count
    val paperCount: Int            = -1,

    // ── Paper modal ───────────────────────────────────────────────────────────
    val paperModalVisible: Boolean = false,

    // ── Settings (persisted via DataStore, loaded on init) ────────────────────
    val dynamicColor: Boolean        = false,
    val useFrontCamera: Boolean      = true,
    val flashEnabled: Boolean        = false,
    val shutterSoundEnabled: Boolean = false,
    val storageUri: Uri?             = null,
    val debugDryRun: Boolean         = false,  // DEBUG: skip actual printing, preview only
    // Printer output calibration
    val calibrationEnabled: Boolean  = true,
    val calibrationVScale: Float     = 0.9524f,
    val calibrationVOffset: Int      = 46,
    // Bluetooth remote shutter
    val remoteShutterEnabled: Boolean = false,
    val remoteShutterKey: RemoteShutterKey = RemoteShutterKey.VOLUME_UP,

    // ── Frames ────────────────────────────────────────────────────────────────
    // Ordered list of all frames (built-in + custom). NONE is always prepended
    // by the UI and not stored here. Populated from FrameRepository on init.
    val frameEntries: List<com.photo.openzinkbooth.core.database.FrameEntry> = emptyList(),

    // ── Preview source ────────────────────────────────────────────────────────
    // True when PreviewScreen was opened via the photo picker (changes title)
    val previewFromPicker: Boolean   = false,

    // ── Pixel Art Mode ────────────────────────────────────────────────────────
    val pixelArtMode: Boolean                        = false,
    val pixelArtAnalysisState: PixelArtAnalysisState = PixelArtAnalysisState.IDLE,
    val pixelArtProgressText:  String                = "Initialising…",
    val pixelArtResult: PixelArtAnalyzer.PixelArtResult? = null,
    val pixelArtEquipped: Boolean                    = false,
    val pixelArtEquipment: List<EquipmentRandomizer.Equipment> = emptyList(),
    val pixelArtEquipmentSet: EquipmentRandomizer.EquipmentSet = EquipmentRandomizer.EquipmentSet.NONE,
) {
    // Convenience helpers used by TopBar components
    val printerConnected: Boolean
        get() = printerConnectionState == PrinterConnectionState.READY

    val printerScanning: Boolean
        get() = printerConnectionState == PrinterConnectionState.SCANNING

    val printerConnecting: Boolean
        get() = printerConnectionState == PrinterConnectionState.CONNECTING

    val printerNotFound: Boolean
        get() = printerConnectionState == PrinterConnectionState.NOT_FOUND

    val printerBluetoothDisabled: Boolean
        get() = printerConnectionState == PrinterConnectionState.BLUETOOTH_DISABLED

    val printerPermissionDenied: Boolean
        get() = printerConnectionState == PrinterConnectionState.BLUETOOTH_PERMISSION_DENIED

    // Show paper count only when user has confirmed at least one refill
    val showPaperCount: Boolean
        get() = paperCount >= 0
}