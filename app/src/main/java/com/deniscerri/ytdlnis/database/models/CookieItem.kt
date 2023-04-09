package com.deniscerri.ytdlnis.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cookies")
data class CookieItem(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    var url: String,
    var content: String
)
