package com.nfcmanager.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nfcmanager.app.domain.model.ActionPayload
import com.nfcmanager.app.domain.model.ActionType
import com.nfcmanager.app.domain.model.NfcAction

/**
 * Storage representation for an action mapping. Payloads are flattened into
 * a single `payload` column (a short, scheme-prefixed string) so we avoid
 * pulling in a JSON dependency just for a handful of concrete shapes.
 *
 * Encoding format (by [typeName]):
 *   OPEN_URL           -> "<url>"
 *   OPEN_APP           -> "<packageName>|<label-or-empty>"
 *   CONNECT_WIFI       -> "<ssid>|<password-or-empty>|<security>"
 *   CONNECT_BLUETOOTH  -> "<deviceName>|<macAddress>"
 *   TOGGLE_FLASHLIGHT  -> ""
 *
 * Forward-compat: rows written by previous app versions that experimented
 * with a 4-segment OPEN_APP shape (carrying a serialized shortcut Intent)
 * still decode cleanly — the trailing segments are simply ignored. The
 * unsupported `APP_ACTIVITY` typeName from even older versions is dropped
 * on read; [NfcActionDao.purgeUnsupportedTypes] also deletes it from disk
 * at app startup so it can't squat unique uidHash slots.
 *
 * The unique index on `uidHash` guarantees at most one action per tag, which
 * matches the product rule of "prevent or warn on duplicate mapping".
 */
@Entity(
    tableName = "nfc_actions",
    indices = [Index(value = ["uidHash"], unique = true)],
)
data class NfcActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "uidHash") val uidHash: String,
    @ColumnInfo(name = "techSignature") val techSignature: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "typeName") val typeName: String,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "requireConfirmation") val requireConfirmation: Boolean,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
)

fun NfcActionEntity.toDomain(): NfcAction? {
    val type = runCatching { ActionType.valueOf(typeName) }.getOrNull() ?: return null

    val payloadModel: ActionPayload = when (type) {
        ActionType.OPEN_URL -> ActionPayload.OpenUrl(payload)
        ActionType.OPEN_APP -> {
            val parts = payload.split('|', limit = 2)
            ActionPayload.OpenApp(
                packageName = parts.getOrNull(0).orEmpty(),
                label = parts.getOrNull(1)?.takeIf { it.isNotBlank() },
            )
        }
        ActionType.CONNECT_WIFI -> {
            val parts = payload.split('|', limit = 3)
            ActionPayload.ConnectWifi(
                ssid = parts.getOrNull(0).orEmpty(),
                password = parts.getOrNull(1)?.takeIf { it.isNotBlank() },
                security = parts.getOrNull(2).orEmpty()
            )
        }
        ActionType.CONNECT_BLUETOOTH -> {
            val parts = payload.split('|', limit = 2)
            ActionPayload.ConnectBluetooth(
                deviceName = parts.getOrNull(0).orEmpty(),
                macAddress = parts.getOrNull(1).orEmpty()
            )
        }
        ActionType.TOGGLE_FLASHLIGHT -> ActionPayload.ToggleFlashlight
    }
    return NfcAction(
        id = id,
        tagUidHash = uidHash,
        techSignature = techSignature,
        label = label,
        payload = payloadModel,
        requireConfirmation = requireConfirmation,
        createdAt = createdAt,
    )
}

fun NfcAction.toEntity(): NfcActionEntity {
    val encoded = when (val p = payload) {
        is ActionPayload.OpenUrl -> p.url
        is ActionPayload.OpenApp -> "${p.packageName}|${p.label.orEmpty()}"
        is ActionPayload.ConnectWifi -> "${p.ssid}|${p.password.orEmpty()}|${p.security}"
        is ActionPayload.ConnectBluetooth -> "${p.deviceName}|${p.macAddress}"
        ActionPayload.ToggleFlashlight -> ""
    }
    return NfcActionEntity(
        id = id,
        uidHash = tagUidHash,
        techSignature = techSignature,
        label = label,
        typeName = type.name,
        payload = encoded,
        requireConfirmation = requireConfirmation,
        createdAt = createdAt,
    )
}
