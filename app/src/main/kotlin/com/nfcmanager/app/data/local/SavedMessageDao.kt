package com.nfcmanager.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedMessageDao {
    @Query("SELECT * FROM saved_messages ORDER BY createdAt DESC")
    fun getAllSavedMessages(): Flow<List<SavedMessageEntity>>

    @Insert
    suspend fun insertMessage(message: SavedMessageEntity): Long

    @Delete
    suspend fun deleteMessage(message: SavedMessageEntity)
}
