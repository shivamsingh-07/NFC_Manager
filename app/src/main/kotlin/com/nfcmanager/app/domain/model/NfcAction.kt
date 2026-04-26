package com.nfcmanager.app.domain.model

/**
 * What kind of effect should happen when a mapped tag is scanned.
 *
 * The enum is persisted by [name] — adding new values is additive-safe,
 * renaming is a breaking change.
 */
enum class ActionType {
    OPEN_URL,
    OPEN_APP,
    CONNECT_WIFI,
    CONNECT_BLUETOOTH,
    TOGGLE_FLASHLIGHT,
}

/**
 * Payload variants for each [ActionType]. Each branch only holds the fields
 * it strictly needs — we avoid stuffing raw free-form strings into a single
 * column so serialisation/validation is explicit and total.
 */
sealed interface ActionPayload {
    data class OpenUrl(val url: String) : ActionPayload

    /**
     * Launches an installed app via its default launcher activity.
     *
     * @param packageName Target app's package id; revalidated by the
     *   executor on every trigger because the app might have been
     *   uninstalled between save and tap.
     * @param label Cached app label captured at save time. Used purely
     *   for list/subtitle rendering — never fed back into intent
     *   resolution.
     */
    data class OpenApp(
        val packageName: String,
        val label: String?,
    ) : ActionPayload
    data class ConnectWifi(
        val ssid: String,
        val password: String?,
        val security: String,
    ) : ActionPayload
    data class ConnectBluetooth(
        val deviceName: String,
        val macAddress: String,
    ) : ActionPayload
    data object ToggleFlashlight : ActionPayload

    val type: ActionType
        get() = when (this) {
            is OpenUrl -> ActionType.OPEN_URL
            is OpenApp -> ActionType.OPEN_APP
            is ConnectWifi -> ActionType.CONNECT_WIFI
            is ConnectBluetooth -> ActionType.CONNECT_BLUETOOTH
            is ToggleFlashlight -> ActionType.TOGGLE_FLASHLIGHT
        }
}

/**
 * A user-configured mapping between a specific (hashed) tag UID and an
 * executable action.
 *
 * @param id Room-assigned row id (0 means "unsaved").
 * @param tagUidHash Hex-encoded SHA-256 hash of salt+UID — never the raw UID.
 * @param techSignature Comma-joined, sorted list of the tag's tech classes at
 *   enrolment time. Used as a lightweight spoof check when we see the same
 *   hash later.
 * @param label User-facing label (e.g. "Desk card").
 * @param payload Concrete action to execute. Serialised into type+payload
 *   columns at the persistence boundary.
 * @param requireConfirmation If true, we post a confirmation notification
 *   instead of executing silently. Sensitive actions default to true.
 */
data class NfcAction(
    val id: Long,
    val tagUidHash: String,
    val techSignature: String,
    val label: String,
    val payload: ActionPayload,
    val requireConfirmation: Boolean,
    val createdAt: Long,
) {
    val type: ActionType get() = payload.type
}
