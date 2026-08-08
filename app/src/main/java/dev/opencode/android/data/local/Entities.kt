package dev.opencode.android.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val projectID: String? = null,
    val workspaceID: String? = null,
    val directory: String? = null,
    val path: String? = null,
    val title: String? = null,
    val agent: String? = null,
    val model: String? = null,
    val cost: Double? = null,
    val summaryJson: String? = null,
    val tokensJson: String? = null,
    val timeCreated: Long? = null,
    val timeUpdated: Long? = null,
    val cachedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["sessionId"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val timeCreated: Long? = null,
    val model: String? = null,
    val agent: String? = null,
    val errorJson: String? = null,
    val json: String,
    val seq: Long = System.currentTimeMillis(),
)