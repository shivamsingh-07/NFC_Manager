package com.nfcmanager.app.data.repository

import com.nfcmanager.app.data.local.NfcActionDao
import com.nfcmanager.app.data.local.toDomain
import com.nfcmanager.app.data.local.toEntity
import com.nfcmanager.app.domain.model.NfcAction
import com.nfcmanager.app.domain.repository.NfcActionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class NfcActionRepositoryImpl @Inject constructor(
    private val dao: NfcActionDao,
) : NfcActionRepository {

    override fun observeAll(): Flow<List<NfcAction>> =
        dao.observeAll().map { list -> list.mapNotNull { it.toDomain() } }

    override suspend fun findByUidHash(uidHash: String): NfcAction? =
        dao.findByUidHash(uidHash)?.toDomain()

    override suspend fun findById(id: Long): NfcAction? =
        dao.findById(id)?.toDomain()

    override suspend fun upsert(action: NfcAction): Long =
        dao.upsert(action.toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun clear() = dao.clear()
}
