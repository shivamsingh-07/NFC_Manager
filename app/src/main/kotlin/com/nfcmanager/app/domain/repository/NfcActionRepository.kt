package com.nfcmanager.app.domain.repository

import com.nfcmanager.app.domain.model.NfcAction
import kotlinx.coroutines.flow.Flow

interface NfcActionRepository {
    fun observeAll(): Flow<List<NfcAction>>
    suspend fun findByUidHash(uidHash: String): NfcAction?
    suspend fun findById(id: Long): NfcAction?
    suspend fun upsert(action: NfcAction): Long
    suspend fun delete(id: Long)
    suspend fun clear()
}
