package com.nfcmanager.app.presentation.actions

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfcmanager.app.data.nfc.NfcReaderManager
import com.nfcmanager.app.data.nfc.NfcTagCaptureManager
import com.nfcmanager.app.domain.model.ActionPayload
import com.nfcmanager.app.domain.model.ActionType
import com.nfcmanager.app.domain.model.NfcAction
import com.nfcmanager.app.domain.model.TagPayload
import com.nfcmanager.app.domain.repository.NfcActionRepository
import com.nfcmanager.app.domain.util.BluetoothDeviceProvider
import com.nfcmanager.app.domain.util.InstalledAppsProvider
import com.nfcmanager.app.domain.util.PackageValidator
import com.nfcmanager.app.domain.util.UrlValidator
import com.nfcmanager.app.presentation.scan.ScanSheetController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CreateActionViewModel @Inject constructor(
    private val repository: NfcActionRepository,
    private val urlValidator: UrlValidator,
    private val packageValidator: PackageValidator,
    private val installedApps: InstalledAppsProvider,
    private val bluetoothDevices: BluetoothDeviceProvider,
    private val tagCapture: NfcTagCaptureManager,
    private val readerManager: NfcReaderManager,
    private val scanSheet: ScanSheetController,
) : ViewModel() {

    enum class Step { ChooseType, Configure, Confirm }

    data class UiState(
        val step: Step = Step.ChooseType,
        val selectedType: ActionType? = null,
        
        // Website
        val urlInput: String = "",
        val urlError: String? = null,

        // App
        val apps: List<InstalledAppsProvider.AppEntry> = emptyList(),
        val selectedApp: InstalledAppsProvider.AppEntry? = null,

        // WiFi
        val wifiSsid: String = "",
        val wifiPassword: String = "",
        val wifiSecurity: TagPayload.WiFi.Security = TagPayload.WiFi.Security.WPA_WPA2_PSK,
        
        // Bluetooth
        val pairedDevices: List<BluetoothDeviceProvider.DeviceEntry> = emptyList(),
        val selectedBluetoothDevice: BluetoothDeviceProvider.DeviceEntry? = null,
        
        // General
        val label: String = "",
        val tagUidHash: String? = null,
        val techSignature: String = "",
        val scanning: Boolean = false,
        val scanError: String? = null,
        val duplicateWarning: Boolean = false,
        val saveError: String? = null,
        val finished: Boolean = false,
    ) {
        val canAdvanceFromConfigure: Boolean
            get() = when (selectedType) {
                ActionType.OPEN_URL -> urlInput.isNotBlank() && urlError == null
                ActionType.OPEN_APP -> selectedApp != null
                ActionType.CONNECT_WIFI -> wifiSsid.isNotBlank()
                ActionType.CONNECT_BLUETOOTH -> selectedBluetoothDevice != null
                ActionType.TOGGLE_FLASHLIGHT -> true
                null -> false
            }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var sheetWatchJob: Job? = null

    fun selectType(type: ActionType) {
        _state.update {
            it.copy(
                selectedType = type,
                step = Step.Configure,
                apps = if (type == ActionType.OPEN_APP) installedApps.list() else it.apps,
                // Reset any half-finished app pick so revisiting OPEN_APP
                // always starts on the app list, never on a stale selection
                // from a previous run.
                selectedApp = if (type == ActionType.OPEN_APP) null else it.selectedApp,
                pairedDevices = if (type == ActionType.CONNECT_BLUETOOTH) bluetoothDevices.getPairedDevices() else it.pairedDevices
            )
        }
    }

    fun back() {
        _state.update { 
            when (it.step) {
                Step.Confirm -> it.copy(step = Step.Configure)
                Step.Configure -> it.copy(step = Step.ChooseType, selectedType = null)
                Step.ChooseType -> it
            }
        }
    }

    // Website logic
    fun updateUrl(value: String) {
        val trimmed = value.trim()
        val normalized = if (trimmed.isEmpty()) null else urlValidator.normalizeForUserInput(trimmed)
        val err = when {
            trimmed.isEmpty() -> null
            normalized == null -> "Invalid or unsafe URL"
            else -> null
        }
        _state.update { it.copy(urlInput = value, urlError = err) }
    }

    fun selectApp(entry: InstalledAppsProvider.AppEntry) {
        _state.update { it.copy(selectedApp = entry) }
    }

    // WiFi logic
    fun updateWifiSsid(value: String) = _state.update { it.copy(wifiSsid = value) }
    fun updateWifiPassword(value: String) = _state.update { it.copy(wifiPassword = value) }
    fun updateWifiSecurity(value: TagPayload.WiFi.Security) = _state.update { it.copy(wifiSecurity = value) }

    // Bluetooth logic
    fun selectBluetoothDevice(device: BluetoothDeviceProvider.DeviceEntry) = _state.update { it.copy(selectedBluetoothDevice = device) }

    fun updateLabel(value: String) {
        val capitalized = capitalizeFirstChar(value)
        _state.update { it.copy(label = capitalized.take(60)) }
    }

    private fun capitalizeFirstChar(value: String): String {
        if (value.isEmpty()) return value
        val first = value[0]
        if (!first.isLetter() || first.isUpperCase()) return value
        return first.uppercaseChar().toString() + value.substring(1)
    }

    fun beginScan(activity: Activity) {
        if (_state.value.scanning) return
        scanJob?.cancel()
        sheetWatchJob?.cancel()

        readerManager.setTagCaptureInProgress(true)
        readerManager.disable(activity)

        _state.update { it.copy(scanning = true, scanError = null) }
        scanSheet.openCapture()

        sheetWatchJob = viewModelScope.launch {
            var armed = false
            scanSheet.state.collect { sheetState ->
                if (!armed) {
                    if (sheetState.visible) armed = true
                    return@collect
                }
                if (!sheetState.visible && _state.value.scanning) {
                    scanJob?.cancel()
                    cleanupCaptureSession(activity)
                    _state.update { it.copy(scanning = false) }
                    sheetWatchJob?.cancel()
                }
            }
        }

        scanJob = viewModelScope.launch {
            try {
                val identity = tagCapture.awaitNextTag(activity)
                val existing = repository.findByUidHash(identity.uidHash)
                _state.update {
                    it.copy(
                        scanning = false,
                        tagUidHash = identity.uidHash,
                        techSignature = identity.techSignature,
                        duplicateWarning = existing != null,
                        step = Step.Confirm,
                    )
                }
                scanSheet.reportCaptureSuccess(identity)
                scanSheet.close()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.update { it.copy(scanning = false, scanError = t.message ?: "Scan failed") }
                scanSheet.reportCaptureError(t.message ?: "Scan failed")
            } finally {
                cleanupCaptureSession(activity)
                sheetWatchJob?.cancel()
            }
        }
    }

    private fun cleanupCaptureSession(activity: Activity) {
        readerManager.setTagCaptureInProgress(false)
        readerManager.enable(activity)
    }

    fun save() {
        val s = _state.value
        val type = s.selectedType ?: return
        val payload = buildPayload(type, s) ?: return
        val uidHash = s.tagUidHash ?: return
        val label = s.label.ifBlank { defaultLabel(payload) }

        viewModelScope.launch {
            try {
                repository.upsert(
                    NfcAction(
                        id = 0L,
                        tagUidHash = uidHash,
                        techSignature = s.techSignature,
                        label = label,
                        payload = payload,
                        requireConfirmation = false,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                _state.update { it.copy(finished = true) }
            } catch (t: Throwable) {
                _state.update { it.copy(saveError = t.message ?: "Save failed") }
            }
        }
    }

    private fun buildPayload(type: ActionType, s: UiState): ActionPayload? {
        return when (type) {
            ActionType.OPEN_URL -> {
                val sanitized = urlValidator.normalizeForUserInput(s.urlInput) ?: return null
                ActionPayload.OpenUrl(sanitized)
            }
            ActionType.OPEN_APP -> {
                val app = s.selectedApp ?: return null
                ActionPayload.OpenApp(packageName = app.packageName, label = app.label)
            }
            ActionType.CONNECT_WIFI -> {
                ActionPayload.ConnectWifi(s.wifiSsid, s.wifiPassword.takeIf { it.isNotBlank() }, s.wifiSecurity.name)
            }
            ActionType.CONNECT_BLUETOOTH -> {
                val device = s.selectedBluetoothDevice ?: return null
                ActionPayload.ConnectBluetooth(device.name, device.address)
            }
            ActionType.TOGGLE_FLASHLIGHT -> ActionPayload.ToggleFlashlight
        }
    }

    private fun defaultLabel(payload: ActionPayload): String = when (payload) {
        is ActionPayload.OpenUrl -> urlValidator.domainOf(payload.url) ?: "Open website"
        is ActionPayload.OpenApp -> payload.label ?: "Open app"
        is ActionPayload.ConnectWifi -> "Connect to ${payload.ssid}"
        is ActionPayload.ConnectBluetooth -> "Connect to ${payload.deviceName}"
        ActionPayload.ToggleFlashlight -> "Flashlight"
    }

    override fun onCleared() {
        scanJob?.cancel()
        sheetWatchJob?.cancel()
        super.onCleared()
    }
}
