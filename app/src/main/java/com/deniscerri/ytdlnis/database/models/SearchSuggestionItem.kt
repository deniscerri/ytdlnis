package com.deniscerri.ytdlnis.database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel

data class SearchSuggestionItem(
    var text: String,
    val type: SearchSuggestionType,
)

enum class SearchSuggestionType{
    SUGGESTION, HISTORY, CLIPBOARD
}