package com.aistudio.kineticmedia.core.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)
