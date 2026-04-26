package com.nfcmanager.app.presentation.write

import android.app.Activity
import android.nfc.NdefMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfcmanager.app.data.nfc.NdefBuilder
import com.nfcmanager.app.data.nfc.NfcWriteManager
import com.nfcmanager.app.domain.model.TagPayload
import com.nfcmanager.app.domain.model.WriteResult
import com.nfcmanager.app.domain.util.UrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.nfcmanager.app.data.local.SavedMessageDao
import com.nfcmanager.app.data.local.SavedMessageEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.nfcmanager.app.data.nfc.EmulationController
import com.nfcmanager.app.data.nfc.HceForegroundPolicy
import com.nfcmanager.app.presentation.util.HapticFeedback

enum class WriteKind { TEXT, URL, CONTACT, WIFI, BLUETOOTH }

data class WriteUiState(
    val kind: WriteKind = WriteKind.TEXT,
    val text: String = "",
    val url: String = "",
    val contactName: String = "",
    val contactPhone: String = "",
    val contactEmail: String = "",
    val contactOrg: String = "",
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val wifiSecurity: TagPayload.WiFi.Security = TagPayload.WiFi.Security.WPA_WPA2_PSK,
    val wifiHidden: Boolean = false,
    val bluetoothName: String = "",
    val bluetoothMac: String = "",
    val status: Status = Status.Idle,
    val isEmulating: Boolean = false,
    val isPeerConnected: Boolean = false,
    val isCreatingMessage: Boolean = false,
    val validationError: String? = null,
) {
    sealed interface Status {
        data object Idle : Status
        data object WaitingForTag : Status
        data class Done(val result: WriteResult) : Status
        data object NfcDisabled : Status
    }
}

@HiltViewModel
class WriteViewModel @Inject constructor(
    private val builder: NdefBuilder,
    private val writer: NfcWriteManager,
    private val urlValidator: UrlValidator,
    private val savedMessageDao: SavedMessageDao,
    private val emulation: EmulationController,
    private val hceForeground: HceForegroundPolicy,
    private val haptics: HapticFeedback,
) : ViewModel() {

    private val _state = MutableStateFlow(WriteUiState())
    val state: StateFlow<WriteUiState> = _state.asStateFlow()

    fun update(reducer: (WriteUiState) -> WriteUiState) = _state.update(reducer)

    /** Both NFC hardware present *and* enabled. Centralises the policy. */
    fun isNfcReady(): Boolean = writer.isSupported() && writer.isEnabled()

    val savedMessages: StateFlow<List<SavedMessageEntity>> = savedMessageDao.getAllSavedMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveMessage() {
        val payloadJson = JSONObject()
        val s = _state.value
        try {
            when (s.kind) {
                WriteKind.TEXT -> {
                    val text = s.text.trim()
                    if (text.isEmpty()) { fail("Text is empty"); return }
                    payloadJson.put("text", text)
                }
                WriteKind.URL -> {
                    val sanitized = urlValidator.normalizeForUserInput(s.url)
                    if (sanitized == null) { fail("Invalid URL"); return }
                    payloadJson.put("url", sanitized)
                }
                WriteKind.CONTACT -> {
                    val name = s.contactName.trim()
                    if (name.isEmpty()) { fail("Name required"); return }
                    payloadJson.put("name", name)
                    payloadJson.put("phone", s.contactPhone)
                    payloadJson.put("email", s.contactEmail)
                    payloadJson.put("org", s.contactOrg)
                }
                WriteKind.WIFI -> {
                    val ssid = s.wifiSsid.trim()
                    if (ssid.isEmpty()) { fail("SSID required"); return }
                    payloadJson.put("ssid", ssid)
                    payloadJson.put("password", s.wifiPassword)
                    payloadJson.put("security", s.wifiSecurity.name)
                    payloadJson.put("hidden", s.wifiHidden)
                }
                WriteKind.BLUETOOTH -> {
                    val name = s.bluetoothName.trim()
                    val mac = s.bluetoothMac.trim()
                    if (name.isEmpty() || mac.isEmpty()) { fail("Name and MAC required"); return }
                    payloadJson.put("name", name)
                    payloadJson.put("mac", mac)
                }
            }
            val entity = SavedMessageEntity(
                type = s.kind.name,
                payload = payloadJson.toString()
            )
            viewModelScope.launch {
                savedMessageDao.insertMessage(entity)
                // Clear the form and close creation section
                _state.update { WriteUiState(isCreatingMessage = false) }
            }
        } catch (e: Exception) {
            fail("Failed to save message")
        }
    }

    fun deleteMessage(message: SavedMessageEntity) {
        viewModelScope.launch {
            savedMessageDao.deleteMessage(message)
        }
    }

    fun loadMessageForWriting(message: SavedMessageEntity) {
        try {
            val json = JSONObject(message.payload)
            val kind = WriteKind.valueOf(message.type)
            _state.update { s ->
                val base = s.copy(kind = kind, isCreatingMessage = false)
                when (kind) {
                    WriteKind.TEXT -> base.copy(text = json.optString("text", ""))
                    WriteKind.URL -> base.copy(url = json.optString("url", ""))
                    WriteKind.CONTACT -> base.copy(
                        contactName = json.optString("name", ""),
                        contactPhone = json.optString("phone", ""),
                        contactEmail = json.optString("email", ""),
                        contactOrg = json.optString("org", "")
                    )
                    WriteKind.WIFI -> base.copy(
                        wifiSsid = json.optString("ssid", ""),
                        wifiPassword = json.optString("password", ""),
                        wifiSecurity = TagPayload.WiFi.Security.valueOf(json.optString("security", "WPA_WPA2_PSK")),
                        wifiHidden = json.optBoolean("hidden", false)
                    )
                    WriteKind.BLUETOOTH -> base.copy(
                        bluetoothName = json.optString("name", ""),
                        bluetoothMac = json.optString("mac", "")
                    )
                }
            }
        } catch (e: Exception) {
            fail("Failed to load message")
        }
    }

    fun emulateMessage(message: SavedMessageEntity) {
        val ndefMessage = buildMessageFromEntity(message) ?: return
        emulation.arm(ndefMessage)
        _state.update { it.copy(isEmulating = true) }
        observeEmulationState()
    }

    fun stopEmulation() {
        emulation.disarm()
        _state.update { it.copy(isEmulating = false) }
    }

    /**
     * Pin our HCE service as the preferred APDU responder for [activity] for
     * as long as the user is on the emulation sheet. Required on Android 14+
     * (the symptom on 16 was: sender's HCE service never receives any APDU,
     * receiver shows "Empty Tag").
     */
    fun pinHceForeground(activity: Activity) {
        hceForeground.makeForeground(activity)
    }

    fun releaseHceForeground(activity: Activity) {
        hceForeground.clearForeground(activity)
    }

    private var emulationObserver: kotlinx.coroutines.Job? = null

    private fun observeEmulationState() {
        emulationObserver?.cancel()
        emulationObserver = viewModelScope.launch {
            emulation.state.collect { s ->
                when (s) {
                    is EmulationController.State.PeerRead -> {
                        // Peer pulled the NDEF message — drop the sheet but
                        // keep the success surface for the UI to react to.
                        haptics.success()
                        _state.update { it.copy(isEmulating = false, isPeerConnected = false) }
                    }
                    EmulationController.State.Connected -> {
                        _state.update { it.copy(isPeerConnected = true) }
                    }
                    EmulationController.State.Idle -> {
                        if (_state.value.isEmulating) {
                            _state.update { it.copy(isEmulating = false, isPeerConnected = false) }
                        }
                    }
                    is EmulationController.State.Ready -> {
                        // Either the initial arm, or a recovery from a
                        // half-finished tap (Connected → Ready). Clear any
                        // stale "transferring" indicator so the UI returns
                        // to the "tap another device" prompt.
                        if (_state.value.isPeerConnected) {
                            _state.update { it.copy(isPeerConnected = false) }
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel destruction must never leave the HCE service serving the
        // last-armed payload to the next reader to tap us.
        emulation.disarm()
    }

    private fun buildMessageFromEntity(entity: SavedMessageEntity): NdefMessage? {
        return try {
            val json = JSONObject(entity.payload)
            when (WriteKind.valueOf(entity.type)) {
                WriteKind.TEXT -> builder.buildText(json.getString("text"))
                WriteKind.URL -> builder.buildUri(json.getString("url"))
                WriteKind.CONTACT -> builder.buildVCard(
                    displayName = json.optString("name", ""),
                    phone = if (json.has("phone")) json.getString("phone") else null,
                    email = if (json.has("email")) json.getString("email") else null,
                    organization = if (json.has("org")) json.getString("org") else null,
                )
                WriteKind.WIFI -> builder.buildWifi(
                    ssid = json.getString("ssid"),
                    password = if (json.has("password")) json.getString("password") else null,
                    security = TagPayload.WiFi.Security.valueOf(json.optString("security", "WPA_WPA2_PSK")),
                    hidden = json.optBoolean("hidden", false),
                )
                WriteKind.BLUETOOTH -> builder.buildBluetooth(
                    deviceName = json.getString("name"),
                    macAddress = json.getString("mac"),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private var writeJob: kotlinx.coroutines.Job? = null

    fun arm(activity: Activity) {
        val message = buildMessage() ?: return
        _state.update { it.copy(status = WriteUiState.Status.WaitingForTag, validationError = null) }
        writeJob?.cancel()
        writeJob = viewModelScope.launch {
            // Irreversible "lock tag" mode is intentionally disabled — writes
            // always leave the tag rewriteable so users can recover from
            // mistakes. Pass `makeReadOnly = false` unconditionally.
            val result = writer.awaitAndWrite(
                activity = activity,
                message = message,
                makeReadOnly = false,
            )
            _state.update { it.copy(status = WriteUiState.Status.Done(result)) }
        }
    }

    fun cancel() {
        writeJob?.cancel()
        emulation.disarm()
        _state.update { it.copy(status = WriteUiState.Status.Idle, isEmulating = false) }
    }

    private fun buildMessage(): NdefMessage? {
        val s = _state.value
        return try {
            when (s.kind) {
                WriteKind.TEXT -> {
                    val text = s.text.trim()
                    if (text.isEmpty()) return fail("Text is empty")
                    builder.buildText(text)
                }
                WriteKind.URL -> {
                    // Lenient normalization: accept `google.com`, `www.foo.io`,
                    // etc. by prepending https:// when the user omits the
                    // scheme. Still rejects unsafe schemes (javascript:, file:,
                    // data:, …) and malformed input.
                    val sanitized = urlValidator.normalizeForUserInput(s.url)
                        ?: return fail("URL is invalid or unsafe")
                    builder.buildUri(sanitized)
                }
                WriteKind.CONTACT -> {
                    val name = s.contactName.trim()
                    if (name.isEmpty()) return fail("Contact name required")
                    builder.buildVCard(
                        displayName = name,
                        phone = s.contactPhone.takeIf { it.isNotBlank() },
                        email = s.contactEmail.takeIf { it.isNotBlank() },
                        organization = s.contactOrg.takeIf { it.isNotBlank() },
                    )
                }
                WriteKind.WIFI -> {
                    val ssid = s.wifiSsid.trim()
                    if (ssid.isEmpty()) return fail("SSID required")
                    builder.buildWifi(
                        ssid = ssid,
                        password = s.wifiPassword.takeIf { it.isNotBlank() },
                        security = s.wifiSecurity,
                        hidden = s.wifiHidden,
                    )
                }
                WriteKind.BLUETOOTH -> {
                    val name = s.bluetoothName.trim()
                    val mac = s.bluetoothMac.trim()
                    if (name.isEmpty()) return fail("Device name required")
                    if (mac.isEmpty()) return fail("MAC address required")
                    builder.buildBluetooth(name, mac)
                }
            }
        } catch (t: IllegalArgumentException) {
            fail(t.message ?: "Invalid input")
        }
    }

    private fun fail(message: String): NdefMessage? {
        _state.update { it.copy(validationError = message) }
        return null
    }
}
