package com.deniscerri.ytdlnis.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val url: String,
    val title: String,
    val author: String,
    val startTime: String,
    val endTime: String,
    val thumb: String,
    val formatId: Int,
    val formatDesc: String,
    val downloadPath: String,
    val website: String,
    val downloadSize: String,
    val progress: Float,
    val custom: Boolean,
    val customTemplateId: Int
)