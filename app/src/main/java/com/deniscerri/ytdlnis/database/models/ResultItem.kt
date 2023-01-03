package com.deniscerri.ytdlnis.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "results")
data class ResultItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val videoId: String,
    val url: String,
    val title: String,
    val author: String,
    val duration: String,
    val thumb: String,
    val downloadedAudio: Int,
    val downloadedVideo: Int,
    val isPlaylistItem: Int,
    val website: String,
    val downloadingAudio: Int,
    val downloadingVideo: Int,
    val playlistTitle: String
)