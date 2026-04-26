package com.nfcmanager.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NfcActionDao {

    @Query("SELECT * FROM nfc_actions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NfcActionEntity>>

    @Query("SELECT * FROM nfc_actions WHERE uidHash = :uidHash LIMIT 1")
    suspend fun findByUidHash(uidHash: String): NfcActionEntity?

    @Query("SELECT * FROM nfc_actions WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): NfcActionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NfcActionEntity): Long

    @Query("DELETE FROM nfc_actions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM nfc_actions")
    suspend fun clear()

    /**
     * Drops rows whose typeName no longer maps to a supported [com.nfcmanager.app.domain.model.ActionType].
     *
     * Currently used to purge legacy `APP_ACTIVITY` mappings written by app
     * versions prior to the App Action / Open App merge. Without this they'd
     * stay in the DB invisibly (filtered out by `toDomain()`) but still
     * occupy uidHash slots, blocking the user from remapping the same
     * physical tag (the unique uidHash index would reject the new INSERT).
     */
    @Query("DELETE FROM nfc_actions WHERE typeName NOT IN ('OPEN_URL','OPEN_APP','CONNECT_WIFI','CONNECT_BLUETOOTH','TOGGLE_FLASHLIGHT')")
    suspend fun purgeUnsupportedTypes(): Int
}
