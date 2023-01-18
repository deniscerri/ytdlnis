package com.deniscerri.ytdlnis.database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "commandTemplates")
data class CommandTemplate(
    val title: String,
    val content: String
){
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}