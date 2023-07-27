package com.deniscerri.ytdlnis.database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "results")
data class ResultItem(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    var url: String,
    var title: String,
    var author: String,
    val duration: String,
    val thumb: String,
    val website: String,
    var playlistTitle: String,
    var formats: ArrayList<Format>,
    @ColumnInfo(defaultValue = "")
    var urls: String,
    var chapters: ArrayList<ChapterItem>?,
    var creationTime: Long = System.currentTimeMillis() / 1000,
)