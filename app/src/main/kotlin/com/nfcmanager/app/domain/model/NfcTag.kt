package com.nfcmanager.app.domain.model

/**
 * A tag discovered via Reader Mode.
 *
 * Identity is captured via the hex UID and the set of supported technologies,
 * so we can surface tag capabilities in the UI independent of payload parsing.
 */
data class NfcTag(
    val uid: String,
    val technologies: List<String>,
    val maxSize: Int,
    val isWritable: Boolean,
    val canMakeReadOnly: Boolean,
    val payloads: List<TagPayload>,
    val discoveredAtEpochMillis: Long,
) {
    val primaryPayload: TagPayload? get() = payloads.firstOrNull()
}
