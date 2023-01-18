package com.deniscerri.ytdlnis.database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class CommandTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val title: String,
    val content: String
)