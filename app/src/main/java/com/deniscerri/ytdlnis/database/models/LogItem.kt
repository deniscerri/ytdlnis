package com.deniscerri.ytdlnis.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "logs")
data class LogItem(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    var title: String,
    var content: String
)
