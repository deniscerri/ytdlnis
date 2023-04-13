package com.deniscerri.ytdlnis.database.models

import com.google.gson.annotations.SerializedName

data class ChapterItem(
    @SerializedName(value = "start_time")
    var start_time: Long,
    @SerializedName(value = "end_time")
    var end_time: Long,
    @SerializedName(value = "title")
    var title: String,
)