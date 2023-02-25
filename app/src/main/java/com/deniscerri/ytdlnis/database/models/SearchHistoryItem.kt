package com.deniscerri.ytdlnis.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel

@Entity(tableName = "searchHistory")
data class SearchHistoryItem(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    val query: String
)