package com.aistudio.kineticmedia.core.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val url: String,
    val title: String,
    val isIncognito: Boolean = false,
    val lastAccessed: Long = System.currentTimeMillis()
)
