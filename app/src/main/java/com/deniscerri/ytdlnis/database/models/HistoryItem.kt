package com.deniscerri.ytdlnis.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val url: String,
    val title: String,
    val author: String,
    val duration: String,
    val thumb: String,
    val type: String,
    val time: String,
    val downloadPath: String,
    val website: String,
    val isQueuedDownload: Int
)