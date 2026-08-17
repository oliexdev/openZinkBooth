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

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photo.openzinkbooth.BuildConfig
import com.photo.openzinkbooth.core.ble.PrintStatus
import com.photo.openzinkbooth.core.ble.SprocketPrinter
import com.photo.openzinkbooth.core.ble.SprocketState
import com.photo.openzinkbooth.core.database.FrameEntry
import com.photo.openzinkbooth.core.database.FrameRepository
import com.photo.openzinkbooth.core.database.SettingsRepository
import com.photo.openzinkbooth.core.utils.LogManager
import com.photo.openzinkbooth.core.utils.PhotoSaver
import com.photo.openzinkbooth.ui.components.applyFilterForPrint
import com.photo.openzinkbooth.ui.components.applyFrameForPrint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import com.photo.openzinkbooth.core.pixelart.PixelArtAnalyzer
import com.photo.openzinkbooth.core.pixelart.sprite.EquipmentRandomizer
import com.photo.openzinkbooth.core.pixelart.stats.StatCalculator
import org.opencv.android.OpenCVLoader

class ZinkBoothViewModel(application: Application) : AndroidViewModel(application) {

    val printer  = SprocketPrinter(application)
    private var pixelArtAnalyzer: PixelArtAnalyzer? = null
    private val settings = SettingsRepository(application)
    private val frameRepo = FrameRepository(application)

    private val _state = MutableStateFlow(ZinkUiState())
    val state: StateFlow<ZinkUiState> = _state.asStateFlow()

    // Devices discovered during a manual scan – cleared when scan stops.
    private val _manualScanDevices = MutableStateFlow<List<ManualScanDevice>>(emptyList())
    val manualScanDevices: StateFlow<List<ManualScanDevice>> = _manualScanDevices.asStateFlow()

    private var countdownJob: Job? = null
    private var printQueueJob: Job? = null
    private var errorResetJob: Job? = null

    init {
        // 0. Init OpenCV (required for PixelArt analyzers)
        if (!OpenCVLoader.initLocal()) {
            LogManager.e("ViewModel", "OpenCV init failed!")
        }

        // 1. Load persisted settings
        viewModelScope.launch {
            settings.settings.collect { s ->
                _state.update {
                    it.copy(
                        dynamicColor         = s.dynamicColor,
                        useFrontCamera       = s.useFrontCamera,
                        flashEnabled         = s.flashEnabled,
                        shutterSoundEnabled  = s.shutterSoundEnabled,
                        storageUri           = s.storageUri,
                        timerSeconds         = s.timerSeconds,
                        paperCount           = s.paperCount,
                        selectedFilter       = FilterType.entries
                            .firstOrNull { f -> f.name == s.selectedFilter }
                            ?: FilterType.ORIGINAL,
                        selectedFrame        = FrameType.entries
                            .firstOrNull { f -> f.name == s.selectedFrame }
                            ?: FrameType.NONE,
                        printerModelName     = s.pairedPrinterName ?: "",
                        calibrationEnabled   = s.calibrationEnabled,
                        calibrationVScale    = s.calibrationVScale,
                        calibrationVOffset   = s.calibrationVOffset,
                        remoteShutterEnabled = s.remoteShutterEnabled,
                        remoteShutterKey     = RemoteShutterKey.fromName(s.remoteShutterKey),
                        pixelArtMode         = s.pixelArtMode,
                    )
                }
                // Push calibration values to printer immediately
                printer.setCalibration(
                    enabled = s.calibrationEnabled,
                    vScale  = s.calibrationVScale,
                    vOffset = s.calibrationVOffset,
                )
            }
        }

        // 2. Mirror SprocketPrinter.state → ZinkUiState
        viewModelScope.launch {
            printer.state.collect { s ->
                // A pending Error→Disconnected reset must not clobber a connection
                // that recovered within the 4s window (e.g. a successful reconnect).
                // Cancel it as soon as any non-Error state arrives; the Error branch
                // below reschedules it when needed.
                if (s !is SprocketState.Error) {
                    errorResetJob?.cancel()
                    errorResetJob = null
                }
                when (s) {
                    is SprocketState.Disconnected ->
                        _state.update {
                            it.copy(
                                printerConnectionState = PrinterConnectionState.DISCONNECTED,
                                batteryLevel           = null,
                            )
                        }

                    is SprocketState.Scanning ->
                        _state.update {
                            it.copy(printerConnectionState = PrinterConnectionState.SCANNING)
                        }

                    is SprocketState.Connecting ->
                        _state.update {
                            it.copy(printerConnectionState = PrinterConnectionState.CONNECTING)
                        }

                    is SprocketState.NotFound ->
                        _state.update {
                            it.copy(printerConnectionState = PrinterConnectionState.NOT_FOUND)
                        }

                    is SprocketState.Ready -> {
                        _state.update {
                            it.copy(
                                printerConnectionState = PrinterConnectionState.READY,
                                printerModelName       = s.model.displayName,
                                printerPrintWidth      = s.model.width,
                                printerPrintHeight     = s.model.height,
                                printerError           = null,
                            )
                        }
                        printer.connectedAddress?.let { address ->
                            viewModelScope.launch {
                                settings.savePairedPrinter(address, s.model.displayName)
                            }
                        }
                        probeStatusAfterConnect()
                    }

                    is SprocketState.Printing ->
                        _state.update {
                            it.copy(
                                printerConnectionState = PrinterConnectionState.READY,
                                currentPrintProgress   = s.progressPercent,
                            )
                        }

                    is SprocketState.Error -> {
                        _state.update {
                            it.copy(
                                printerConnectionState = PrinterConnectionState.ERROR,
                                printerError           = s.message,
                            )
                        }
                        errorResetJob?.cancel()
                        errorResetJob = viewModelScope.launch {
                            delay(4_000)
                            _state.update {
                                it.copy(
                                    printerConnectionState = PrinterConnectionState.DISCONNECTED,
                                    printerError           = null,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. In debug mode: forward BLE log lines to LogManager
        if (BuildConfig.DEBUG) {
            viewModelScope.launch {
                var lastSize = 0
                printer.rxLog.collect { lines ->
                    lines.drop(lastSize).forEach { line -> LogManager.d("BLE", line) }
                    lastSize = lines.size
                }
            }
        }

        // 4. BT permission + enabled check is deferred to MainActivity which calls
        //    onPermissionsResult() after the runtime permission request completes.
        //    We just set DISCONNECTED so the UI has a valid initial state.

        // 5. Load frame configuration
        viewModelScope.launch {
            frameRepo.frames.collect { entries ->
                _state.update { it.copy(frameEntries = entries) }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Printer – probe after connect
    // ---------------------------------------------------------------------------
    private fun probeStatusAfterConnect() {
        viewModelScope.launch {
            val snapshot = printer.sendProbe() ?: return@launch
            _state.update {
                it.copy(
                    batteryLevel = snapshot.batteryLevel,
                    printStatus  = snapshot.printStatus,
                    paperEmpty   = snapshot.printStatus ==
                            PrintStatus.OUT_OF_PAPER ||
                            snapshot.printStatus ==
                            PrintStatus.NO_SUPPLIES_DETECTED ||
                            snapshot.printStatus ==
                            PrintStatus.OUT_OF_SUPPLIES,
                )
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------------
    fun navigateTo(screen: Screen) =
        _state.update { it.copy(screen = screen, drawerOpen = false) }

    fun openDrawer()  = _state.update { it.copy(drawerOpen = true)  }
    fun closeDrawer() = _state.update { it.copy(drawerOpen = false) }

    fun navigateBack() {
        _state.update { s ->
            when {
                // PRINTER, FRAME_MANAGER and PRINTER_CONFIG are sub-pages of SETTINGS
                s.screen == Screen.PRINTER ||
                        s.screen == Screen.FRAME_MANAGER ||
                        s.screen == Screen.PRINTER_CONFIG ->
                    s.copy(screen = Screen.SETTINGS)
                s.screen == Screen.PIXEL_ART_PREVIEW ->
                    s.copy(screen = Screen.CAMERA, pixelArtResult = null,
                        pixelArtAnalysisState = PixelArtAnalysisState.IDLE,
                        pixelArtEquipped     = false,
                        pixelArtEquipment    = emptyList(),
                        pixelArtEquipmentSet = EquipmentRandomizer.EquipmentSet.NONE)
                s.screen != Screen.CAMERA ->
                    s.copy(screen = Screen.CAMERA, drawerOpen = false, previewFromPicker = false)
                else -> s
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Camera
    // ---------------------------------------------------------------------------
    fun setTimer(seconds: Int) {
        _state.update { it.copy(timerSeconds = seconds) }
        viewModelScope.launch { settings.setTimer(seconds) }
    }

    // Stores the camera capture lambda so the remote shutter can trigger it
    // without needing a reference to the Composable scope.
    private var _lastTriggerCapture: (() -> Unit)? = null

    /** Called as soon as the camera is ready to register the capture lambda. */
    fun registerTriggerCapture(takePicture: () -> Unit) {
        _lastTriggerCapture = takePicture
    }

    fun onCapturePressed(takePicture: () -> Unit) {
        _lastTriggerCapture = takePicture  // always keep latest reference
        val timer = _state.value.timerSeconds
        if (timer == 0) { takePicture(); return }
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (i in timer downTo 1) {
                _state.update { it.copy(countdown = i) }
                delay(1_000)
            }
            _state.update { it.copy(countdown = null) }
            takePicture()
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        _state.update { it.copy(countdown = null) }
    }

    fun onPhotoCaptured(bitmap: Bitmap) {
        // Play shutter sound if enabled
        if (_state.value.shutterSoundEnabled) {
            try {
                android.media.MediaActionSound().play(android.media.MediaActionSound.SHUTTER_CLICK)
            } catch (e: Exception) {
                LogManager.w("ViewModel", "Shutter sound failed: ${e.message}")
            }
        }
        val targetScreen = if (_state.value.pixelArtMode) Screen.PIXEL_ART_PREVIEW else Screen.PREVIEW
        _state.update {
            it.copy(
                capturedPhoto      = bitmap,
                screen             = targetScreen,
                previewFromPicker  = false,
                // Reset pixel art state for new photo
                pixelArtResult     = null,
                pixelArtAnalysisState = if (_state.value.pixelArtMode) PixelArtAnalysisState.IDLE else it.pixelArtAnalysisState,
                pixelArtEquipped   = false,
                pixelArtEquipment  = emptyList(),
                pixelArtEquipmentSet = EquipmentRandomizer.EquipmentSet.NONE,
            )
        }
        // Auto-start analysis in pixel art mode
        if (_state.value.pixelArtMode) {
            startPixelArtAnalysis(bitmap)
        }

        // Save original (unfiltered) photo only when user has set a storage location
        val storageUri = _state.value.storageUri ?: return
        viewModelScope.launch {
            val saved = PhotoSaver.save(
                context    = getApplication(),
                bitmap     = bitmap,
                storageUri = storageUri
            )
            if (saved == null) {
                LogManager.e("ViewModel", "Photo save failed")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Photo picker – loads an external image into PreviewScreen
    // ---------------------------------------------------------------------------

    /**
     * Called when the user picks a photo via the SAF picker.
     * Decodes the Uri into a Bitmap and opens the PreviewScreen.
     */
    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?.use { BitmapFactory.decodeStream(it) }
                } catch (e: Exception) {
                    LogManager.e("ViewModel", "Failed to decode picked photo: ${e.message}", e)
                    null
                }
            } ?: return@launch

            _state.update {
                it.copy(
                    capturedPhoto     = bitmap,
                    screen            = Screen.PREVIEW,
                    previewFromPicker = true,
                    // Reset filter/frame for imported photos
                    selectedFilter    = FilterType.ORIGINAL,
                    selectedFrame     = FrameType.NONE,
                )
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Preview
    // ---------------------------------------------------------------------------
    fun selectFilter(filter: FilterType) {
        _state.update { it.copy(selectedFilter = filter) }
        viewModelScope.launch { settings.setFilter(filter.name) }
    }

    fun selectFrame(frame: FrameType) {
        _state.update { it.copy(selectedFrame = frame, selectedCustomFrameId = null) }
        viewModelScope.launch { settings.setFrame(frame.name) }
    }

    fun selectCustomFrame(entryId: String) {
        _state.update { it.copy(selectedCustomFrameId = entryId, selectedFrame = FrameType.NONE) }
    }

    // ---------------------------------------------------------------------------
    // Print queue
    // ---------------------------------------------------------------------------

    /**
     * Enqueues a print job from the PreviewScreen (camera capture or picker).
     */
    fun enqueuePrintFromPreview(compositedBitmap: Bitmap) {
        val photo    = _state.value.capturedPhoto ?: return
        val filter   = _state.value.selectedFilter
        val frame    = _state.value.selectedFrame
        val customId = _state.value.selectedCustomFrameId

        // compositedBitmap is already rendered at printer resolution
        // (printerPrintWidth × printerPrintHeight) by PreviewScreen.
        // No further scaling needed.
        val job = PrintJob(
            id               = UUID.randomUUID().toString(),
            photo            = photo,
            filter           = filter,
            frame            = frame,
            customFrameId    = customId,
            compositedBitmap = compositedBitmap,
        )
        _state.update {
            it.copy(
                printQueue        = it.printQueue + job,
                screen            = Screen.CAMERA,
                previewFromPicker = false,
            )
        }
        startPrintQueueIfIdle()
    }

    private fun startPrintQueueIfIdle() {
        if (printQueueJob?.isActive == true) return
        printQueueJob = viewModelScope.launch {
            while (_state.value.printQueue.isNotEmpty()) {
                val job = _state.value.printQueue.first()

                // If a pre-composited bitmap is available (set by enqueuePrintFromPreview),
                // use it directly — it is already scaled to printer resolution and
                // pixel-identical to what the user saw in the preview.
                val composited = job.compositedBitmap ?: run {
                    val filtered = applyFilterForPrint(job.photo, job.filter)
                    val model  = printer.detectedModel
                    val printW = model.width
                    val printH = model.height
                    if (job.customFrameId != null) {
                        val filename = job.customFrameId.removePrefix("custom:")
                        val frameBitmap = frameRepo.loadCustomBitmap(filename)
                        if (frameBitmap != null)
                            com.photo.openzinkbooth.ui.components.applyCustomFrameForPrint(filtered, frameBitmap, printW, printH)
                        else
                            applyFrameForPrint(filtered, job.frame, printW, printH)
                    } else {
                        applyFrameForPrint(filtered, job.frame, printW, printH)
                    }
                }

                try {
                    if (_state.value.debugDryRun) {
                        LogManager.d("ViewModel", "DRY RUN – rendering bitmap for preview, not sent to printer")
                        printer.prepareImageForPreview(composited)
                    } else {
                        printer.print(composited)
                    }
                    // Job succeeded — remove from queue and decrement paper count.
                    _state.update { it.copy(printQueue = it.printQueue.drop(1)) }
                    settings.decrementPaperCount()
                } catch (e: Exception) {
                    // Job failed — remove it from the queue so a single broken
                    // job cannot stall the remaining ones indefinitely.
                    LogManager.e("ViewModel", "Print job ${job.id} failed: ${e.message}")
                    _state.update { it.copy(
                        printQueue = it.printQueue.drop(1),
                        // Show the error message in the PrintBar for 4s, then auto-clear.
                        printError = e.message ?: "Unknown error"
                    ) }
                    delay(4_000)
                    _state.update { it.copy(printError = null) }
                    // Give the printer a moment to recover before the next job.
                    delay(1_000)
                }
            }
            // All jobs done — reset progress indicator.
            _state.update { it.copy(currentPrintProgress = 0) }
        }
    }

    // ---------------------------------------------------------------------------
    // Paper modal
    // ---------------------------------------------------------------------------
    fun showPaperModal()  = _state.update { it.copy(paperModalVisible = true)  }
    fun hidePaperModal()  = _state.update { it.copy(paperModalVisible = false) }

    fun confirmPaperRefill() {
        viewModelScope.launch { settings.resetPaperCount() }
        _state.update { it.copy(paperEmpty = false, paperModalVisible = false) }
    }

    // ---------------------------------------------------------------------------
    // Bluetooth state
    // ---------------------------------------------------------------------------

    /** True when Bluetooth is enabled on the device. */
    private fun isBluetoothEnabled(): Boolean =
        getApplication<Application>()
            .getSystemService(android.bluetooth.BluetoothManager::class.java)
            ?.adapter?.isEnabled == true

    /**
     * Called from MainActivity after the BT permission request completes.
     * [granted] = true when both BLUETOOTH_SCAN and BLUETOOTH_CONNECT are granted.
     */
    fun onPermissionsResult(granted: Boolean) {
        if (!granted) {
            _state.update {
                it.copy(printerConnectionState = PrinterConnectionState.BLUETOOTH_PERMISSION_DENIED)
            }
            return
        }
        if (isBluetoothEnabled()) printer.autoScan()
        else _state.update {
            it.copy(printerConnectionState = PrinterConnectionState.BLUETOOTH_DISABLED)
        }
    }

    /** Called when the user enables Bluetooth via the system dialog. */
    fun onBluetoothEnabled() {
        if (_state.value.printerConnectionState == PrinterConnectionState.BLUETOOTH_DISABLED) {
            printer.autoScan()
        }
    }

    /**
     * Returns false and sets BLUETOOTH_PERMISSION_DENIED if permissions are missing,
     * or BLUETOOTH_DISABLED if BT is off. Callers abort early on false.
     */
    private fun requireBluetooth(): Boolean {
        val hasPermission = listOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT
        ).all { perm ->
            androidx.core.content.ContextCompat.checkSelfPermission(
                getApplication(), perm
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (!hasPermission) {
            _state.update {
                it.copy(printerConnectionState = PrinterConnectionState.BLUETOOTH_PERMISSION_DENIED)
            }
            return false
        }
        if (!isBluetoothEnabled()) {
            _state.update {
                it.copy(printerConnectionState = PrinterConnectionState.BLUETOOTH_DISABLED)
            }
            return false
        }
        return true
    }

    fun reconnectPrinter() {
        if (!requireBluetooth()) return
        printer.disconnect()
        printer.autoScan()
    }

    fun disconnectPrinter() {
        printer.disconnect()
        // If disconnecting from the config screen, navigate back to settings
        if (_state.value.screen == Screen.PRINTER_CONFIG) {
            _state.update { it.copy(screen = Screen.SETTINGS) }
        }
    }

    // ---------------------------------------------------------------------------
    // Printer config (RFCOMM)
    // ---------------------------------------------------------------------------

    val lastPrintBitmap = printer.lastPrintBitmap

    val printerIdentity = printer.identity
    val printerConfig   = printer.config

    fun applyPrinterConfig(config: com.photo.openzinkbooth.core.ble.PrinterConfig) {
        viewModelScope.launch {
            try { printer.applyConfig(config) }
            catch (e: Exception) { LogManager.e("ViewModel", "applyConfig failed: ${e.message}") }
        }
    }

    fun refreshPrinterIdentity() {
        viewModelScope.launch {
            try { printer.refreshIdentity() }
            catch (e: Exception) { LogManager.e("ViewModel", "refreshIdentity failed: ${e.message}") }
        }
    }

    fun refreshPrinterConfig() {
        viewModelScope.launch {
            try { printer.refreshConfig() }
            catch (e: Exception) { LogManager.e("ViewModel", "refreshConfig failed: ${e.message}") }
        }
    }

    fun toggleDebugDryRun() = _state.update { it.copy(debugDryRun = !it.debugDryRun) }

    /**
     * Called when the Bluetooth remote shutter button is pressed.
     * Follows the same path as tapping the on-screen shutter button.
     */
    fun onRemoteShutterPressed() {
        val triggerCapture = _lastTriggerCapture ?: return
        onCapturePressed(triggerCapture)
    }

    /**
     * Send a calibration test image directly to the printer, bypassing the
     * normal photo + frame pipeline. Honours debugDryRun: if enabled, runs
     * prepareImage to populate lastPrintBitmap but does NOT actually print.
     */
    /** Loads an image from assets and triggers the normal photo workflow. */
    fun loadTestImage(filename: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val bmp = getApplication<android.app.Application>()
                    .assets.open(filename)
                    .use { android.graphics.BitmapFactory.decodeStream(it) }
                if (bmp != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onPhotoCaptured(bmp)
                    }
                }
            } catch (e: Exception) {
                LogManager.e("ViewModel", "Failed to load test image: ${e.message}")
            }
        }
    }

    fun printTestImage(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                if (_state.value.debugDryRun) {
                    LogManager.d("ViewModel", "DRY RUN – test image rendered, not sent to printer")
                    printer.prepareImageForPreview(bitmap)
                } else {
                    if (!_state.value.printerConnected) {
                        LogManager.w("ViewModel", "printTestImage: printer not ready")
                        return@launch
                    }
                    printer.print(bitmap)
                }
            } catch (e: Exception) {
                LogManager.e("ViewModel", "Test print failed: ${e.message}")
            }
        }
    }

    fun startManualScan() {
        if (!requireBluetooth()) return
        _manualScanDevices.value = emptyList()
        printer.startManualScan { peripheral, model, deviceName ->
            val entry = ManualScanDevice(peripheral, model, deviceName)
            if (_manualScanDevices.value.none { it.address == entry.address }) {
                _manualScanDevices.value = _manualScanDevices.value + entry
            }
        }
    }

    fun stopManualScan() {
        printer.stopScan()
        _manualScanDevices.value = emptyList()
    }

    fun connectManualDevice(device: ManualScanDevice) {
        printer.connectToDevice(device.peripheral, device.model)
        _manualScanDevices.value = emptyList()
        _state.update { it.copy(screen = Screen.SETTINGS) }
    }

    // ---------------------------------------------------------------------------
    // Frame Manager
    // ---------------------------------------------------------------------------

    fun setFrameVisible(entryId: String, visible: Boolean) {
        viewModelScope.launch { frameRepo.setVisible(entryId, visible) }
    }

    fun saveFrameOrder(entries: List<FrameEntry>) {
        viewModelScope.launch { frameRepo.saveOrder(entries) }
    }

    fun addCustomFrame(uri: android.net.Uri, name: String) {
        viewModelScope.launch { frameRepo.addCustomFrame(uri, name) }
    }

    fun renameCustomFrame(oldFilename: String, newName: String) {
        viewModelScope.launch { frameRepo.renameCustomFrame(oldFilename, newName) }
    }

    fun deleteCustomFrame(filename: String) {
        viewModelScope.launch { frameRepo.deleteCustomFrame(filename) }
    }

    fun loadCustomFrameBitmap(filename: String): Bitmap? =
        frameRepo.loadCustomBitmap(filename)

    // ---------------------------------------------------------------------------
    // Settings
    // ---------------------------------------------------------------------------
    fun setDynamicColor(enabled: Boolean)  = viewModelScope.launch { settings.setDynamicColor(enabled) }
    fun setFrontCamera(front: Boolean)     = viewModelScope.launch { settings.setFrontCamera(front) }
    fun setFlash(enabled: Boolean)         = viewModelScope.launch { settings.setFlash(enabled) }
    fun setShutterSound(enabled: Boolean)  = viewModelScope.launch { settings.setShutterSound(enabled) }

    fun setCalibrationEnabled(enabled: Boolean) = viewModelScope.launch { settings.setCalibrationEnabled(enabled) }
    fun setCalibrationVScale(scale: Float)       = viewModelScope.launch { settings.setCalibrationVScale(scale) }
    fun setCalibrationVOffset(offset: Int)       = viewModelScope.launch { settings.setCalibrationVOffset(offset) }

    fun setRemoteShutterEnabled(enabled: Boolean) = viewModelScope.launch { settings.setRemoteShutterEnabled(enabled) }
    fun setRemoteShutterKey(key: RemoteShutterKey) =
        viewModelScope.launch { settings.setRemoteShutterKey(key.name) }
    fun setStorageUri(uri: Uri?)           = viewModelScope.launch { settings.setStorageUri(uri) }

    // ---------------------------------------------------------------------------
    // Pixel Art Mode
    // ---------------------------------------------------------------------------

    fun setPixelArtMode(enabled: Boolean) {
        _state.update { it.copy(pixelArtMode = enabled) }
        viewModelScope.launch { settings.setPixelArtMode(enabled) }
        if (enabled) {
            // Create analyzer and preload all models in background immediately
            val analyzer = pixelArtAnalyzer ?: PixelArtAnalyzer(getApplication()).also {
                pixelArtAnalyzer = it
            }
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                LogManager.d("PixelArt", "Preloading models in background…")
                analyzer.preloadModels()
                LogManager.d("PixelArt", "Models preloaded and ready")
            }
        } else {
            // Release analyzer resources when mode is turned off
            pixelArtAnalyzer?.release()
            pixelArtAnalyzer = null
        }
    }

    fun startPixelArtAnalysis(photo: Bitmap) {
        val analyzer = pixelArtAnalyzer ?: PixelArtAnalyzer(getApplication()).also {
            pixelArtAnalyzer = it
        }
        // Downscale to max 1080px — YuNet detects at 640px, BiSeNet at 512px crop
        // so no quality is lost while saving significant RAM and processing time
        val input = if (photo.width > 1080 || photo.height > 1080) {
            val scale = 1080f / maxOf(photo.width, photo.height)
            Bitmap.createScaledBitmap(
                photo,
                (photo.width  * scale).toInt(),
                (photo.height * scale).toInt(),
                true
            )
        } else photo
        viewModelScope.launch {
            _state.update { it.copy(pixelArtAnalysisState = PixelArtAnalysisState.ANALYSING) }
            try {
                val result = analyzer.analyze(input) { progressKey ->
                    LogManager.d("PixelArt", progressKey)
                    val ctx = getApplication<android.app.Application>()
                    val text = when {
                        progressKey.startsWith("pixelart_progress_analyse:") -> {
                            val n = progressKey.substringAfter(":").toIntOrNull() ?: 1
                            ctx.resources.getQuantityString(
                                com.photo.openzinkbooth.R.plurals.pixelart_progress_analyse, n, n)
                        }
                        else -> try {
                            val resId = ctx.resources.getIdentifier(
                                progressKey, "string", ctx.packageName)
                            if (resId != 0) ctx.getString(resId) else progressKey
                        } catch (e: Exception) { progressKey }
                    }
                    _state.update { it.copy(pixelArtProgressText = text) }
                }
                _state.update {
                    it.copy(
                        pixelArtResult        = result,
                        pixelArtAnalysisState = PixelArtAnalysisState.DONE,
                    )
                }
            } catch (e: Exception) {
                LogManager.e("PixelArt", "Analysis failed: ${e.message}", e)
                _state.update { it.copy(pixelArtAnalysisState = PixelArtAnalysisState.ERROR) }
            }
        }
    }

    /** Apply a specific equipment set to all persons, or NONE to clear. */
    fun selectEquipmentSet(set: EquipmentRandomizer.EquipmentSet) {
        val result   = _state.value.pixelArtResult ?: return
        val analyzer = pixelArtAnalyzer ?: return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val isNone = set == EquipmentRandomizer.EquipmentSet.NONE
            val equipmentNullable = result.allFeatures.map { features ->
                if (isNone) null else EquipmentRandomizer.buildForSet(set, features)
            }
            val equipmentForState = equipmentNullable.filterNotNull()
            val newStats   = result.allFeatures.map { StatCalculator.calculate(it, set) }
            val newSprites = analyzer.generateSprites(result.allFeatures, equipmentNullable)
            val newCanvas  = analyzer.buildSpriteCanvas(
                result.originalPhoto, result.allFeatures, newSprites
            )
            val newResult = result.copy(spriteCanvas = newCanvas, stats = newStats)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _state.update {
                    it.copy(
                        pixelArtEquipped     = !isNone,
                        pixelArtEquipment    = equipmentForState,
                        pixelArtEquipmentSet = set,
                        pixelArtResult       = newResult,
                    )
                }
            }
        }
    }

    /** Randomize equipment — each person gets a random class independently. */
    fun randomizeEquipment() {
        val result   = _state.value.pixelArtResult ?: return
        val analyzer = pixelArtAnalyzer ?: return

        val classes = EquipmentRandomizer.EquipmentSet.values()
            .filter { it != EquipmentRandomizer.EquipmentSet.NONE }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val perPersonSets = result.allFeatures.map { classes.random() }
            val equipment = result.allFeatures.mapIndexed { i, features ->
                EquipmentRandomizer.buildForSet(perPersonSets[i], features)
            }
            val newStats = result.allFeatures.mapIndexed { i, f ->
                StatCalculator.calculate(f, perPersonSets[i])
            }
            val dominantSet = perPersonSets.groupBy { it }.maxByOrNull { it.value.size }?.key
                ?: perPersonSets.first()
            val newSprites = analyzer.generateSprites(result.allFeatures, equipment)
            val newCanvas  = analyzer.buildSpriteCanvas(
                result.originalPhoto, result.allFeatures, newSprites
            )
            val avgStats = if (newStats.size == 1) newStats else listOf(
                PixelArtAnalyzer.CharacterStats(
                    hp  = newStats.map { it.hp  }.average().toInt(),
                    str = newStats.map { it.str }.average().toInt(),
                    def = newStats.map { it.def }.average().toInt(),
                    mag = newStats.map { it.mag }.average().toInt(),
                    spd = newStats.map { it.spd }.average().toInt(),
                )
            )
            val newResult = result.copy(spriteCanvas = newCanvas, stats = avgStats)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _state.update {
                    it.copy(
                        pixelArtEquipped     = true,
                        pixelArtEquipment    = equipment,
                        pixelArtEquipmentSet = dominantSet,
                        pixelArtResult       = newResult,
                    )
                }
            }
        }
    }

    /** Enqueues a print job from the PixelArt preview screen. */
    fun enqueuePrintFromPixelArt(compositedBitmap: Bitmap) {
        val photo = _state.value.capturedPhoto ?: return
        val job = PrintJob(
            id               = UUID.randomUUID().toString(),
            photo            = photo,
            filter           = FilterType.ORIGINAL,
            frame            = FrameType.NONE,
            compositedBitmap = compositedBitmap,
        )
        _state.update {
            it.copy(
                printQueue            = it.printQueue + job,
                screen                = Screen.CAMERA,
                pixelArtResult        = null,
                pixelArtAnalysisState = PixelArtAnalysisState.IDLE,
                pixelArtEquipped      = false,
                pixelArtEquipment     = emptyList(),
                pixelArtEquipmentSet  = EquipmentRandomizer.EquipmentSet.NONE,
            )
        }
        startPrintQueueIfIdle()
    }

    override fun onCleared() {
        printer.disconnect()
        pixelArtAnalyzer?.release()
        super.onCleared()
    }
}