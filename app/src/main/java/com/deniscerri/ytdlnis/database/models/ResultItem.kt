package com.deniscerri.ytdlnis.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.deniscerri.ytdlnis.database.Converters
import java.util.*

@Entity(tableName = "results")
data class ResultItem(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    val url: String,
    var title: String,
    var author: String,
    val duration: String,
    val thumb: String,
    val website: String,
    var playlistTitle: String,
    val formats: ArrayList<Format>,
    var creationTime: Long = System.currentTimeMillis() / 1000,
)