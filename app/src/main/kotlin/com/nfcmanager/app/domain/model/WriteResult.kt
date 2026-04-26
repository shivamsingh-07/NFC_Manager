package com.nfcmanager.app.domain.model

sealed interface WriteResult {
    data object Success : WriteResult
    data class Failure(val reason: Reason, val cause: Throwable? = null) : WriteResult

    enum class Reason {
        TAG_LOST,
        READ_ONLY,
        INSUFFICIENT_CAPACITY,
        UNSUPPORTED_TAG,
        MALFORMED_PAYLOAD,
        IO_ERROR,
        UNKNOWN,
    }
}
