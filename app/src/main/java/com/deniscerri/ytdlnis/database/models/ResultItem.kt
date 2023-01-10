package com.deniscerri.ytdlnis.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "results")
data class ResultItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val url: String,
    val title: String,
    val author: String,
    val duration: String,
    val thumb: String,
    val website: String,
    val playlistTitle: String
)