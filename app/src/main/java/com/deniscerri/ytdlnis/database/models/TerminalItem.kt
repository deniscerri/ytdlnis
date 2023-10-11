package com.deniscerri.ytdlnis.database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel

@Entity(tableName = "terminalDownloads")
data class TerminalItem(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var command: String
)