package com.deniscerri.ytdlnis.database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "templateShortcuts")
data class TemplateShortcut(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    val content: String
)