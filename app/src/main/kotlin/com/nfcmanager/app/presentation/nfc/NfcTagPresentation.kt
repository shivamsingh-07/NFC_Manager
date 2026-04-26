package com.nfcmanager.app.presentation.nfc

import com.nfcmanager.app.domain.model.ActionPayload
import com.nfcmanager.app.domain.model.NfcAction
import com.nfcmanager.app.domain.model.NfcTag
import com.nfcmanager.app.domain.model.TagPayload

/** UI-facing summaries for tag + action rows in the NFC confirmation sheet. */
object NfcTagPresentation {

    fun actionTypeLabel(action: NfcAction): String = when (action.payload) {
        is ActionPayload.OpenUrl -> "Open website"
        is ActionPayload.OpenApp -> "Open app"
        is ActionPayload.ConnectWifi -> "Connect Wi-Fi"
        is ActionPayload.ConnectBluetooth -> "Connect Bluetooth"
        ActionPayload.ToggleFlashlight -> "Flashlight"
    }

    fun primaryContentSummary(tag: NfcTag): String {
        val payload = tag.primaryPayload
        return when (payload) {
            is TagPayload.Text -> payload.text
            is TagPayload.Uri -> if (payload.isSafeToOpen) payload.sanitizedUrl ?: payload.raw else payload.raw
            is TagPayload.Contact -> payload.displayName ?: "Unnamed contact"
            is TagPayload.WiFi -> "Network: ${payload.ssid}"
            is TagPayload.Bluetooth -> "Bluetooth Device: ${payload.deviceName}"
            is TagPayload.Raw -> payload.mimeType ?: "${payload.bytes.size} bytes"
            null -> "Tag contains no NDEF data"
        }
    }
}
