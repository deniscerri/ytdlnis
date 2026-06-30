package com.deniscerri.ytdl.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channelVideoCache")
data class ChannelVideoCache(
    @PrimaryKey var channelUrl: String,
    var videosJson: String,
    var timestamp: Long = 0
)
