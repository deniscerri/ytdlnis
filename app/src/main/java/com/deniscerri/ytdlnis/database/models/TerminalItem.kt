package com.deniscerri.ytdlnis.database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "terminalDownloads")
data class TerminalItem(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var command: String,
    @ColumnInfo(defaultValue = "")
    var log: String? = "",
)