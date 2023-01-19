package com.deniscerri.ytdlnis.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "formats")
data class Format(
    var itemId: Int,
    val format: String,
    val format_id: String,
    val ext: String,
    val filesize: Long,
    val format_note: String
){
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}