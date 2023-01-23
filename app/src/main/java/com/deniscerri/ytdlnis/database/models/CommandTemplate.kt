package com.deniscerri.ytdlnis.database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "commandTemplates")
data class CommandTemplate(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    val title: String,
    val content: String
)