package com.deniscerri.ytdl.database.models

import com.deniscerri.ytdl.database.enums.DownloadType

data class DownloadSizeMetadata(
    val id: Long,
    val type: DownloadType,
    val format: Format,
    val allFormats: List<Format>,
    val videoPreferences: VideoPreferences
)