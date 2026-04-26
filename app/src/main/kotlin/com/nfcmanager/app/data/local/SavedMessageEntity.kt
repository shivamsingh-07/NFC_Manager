package com.nfcmanager.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_messages")
data class SavedMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "TEXT", "URL", "CONTACT", "WIFI", "BLUETOOTH"
    val payload: String, // JSON payload representing the message details
    val createdAt: Long = System.currentTimeMillis()
)
