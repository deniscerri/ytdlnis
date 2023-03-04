package com.deniscerri.ytdlnis.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "templateShortcuts")
data class TemplateShortcut(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    val content: String
)