package com.deniscerri.ytdl.database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "commandTemplates")
data class CommandTemplate(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    var title: String,
    var content: String,
    @ColumnInfo(defaultValue = "0")
    var useAsExtraCommand: Boolean,
    @ColumnInfo(defaultValue = "1")
    var useAsExtraCommandAudio: Boolean,
    @ColumnInfo(defaultValue = "1")
    var useAsExtraCommandVideo: Boolean,
    @ColumnInfo(defaultValue = "0")
    var useAsExtraCommandDataFetching: Boolean,
    @ColumnInfo(defaultValue = "0")
    var preferredCommandTemplate : Boolean = false,
    var urlRegex: MutableList<String> = mutableListOf()
)