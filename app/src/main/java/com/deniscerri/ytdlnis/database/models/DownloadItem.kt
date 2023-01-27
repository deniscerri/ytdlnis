package com.deniscerri.ytdlnis.database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    val url: String,
    var title: String,
    var author: String,
    val thumb: String,
    val duration: String,
    var type: String,
    var format: Format,
    @ColumnInfo(defaultValue = "0")
    val removeAudio: Boolean,
    var downloadPath: String,
    val website: String,
    val downloadSize: String,
    val playlistTitle: String,
    val embedSubs: Boolean,
    val addChapters: Boolean,
    val SaveThumb: Boolean,
    @ColumnInfo(defaultValue = "Queued")
    var status: String,
    val workID: Int
)