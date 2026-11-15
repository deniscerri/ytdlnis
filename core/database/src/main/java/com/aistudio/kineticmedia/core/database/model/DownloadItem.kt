package com.aistudio.kineticmedia.core.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val url: String,
    val title: String,
    val filePath: String,
    val progress: Float = 0f,
    val status: String = "QUEUED",
    val timestamp: Long = System.currentTimeMillis()
)
