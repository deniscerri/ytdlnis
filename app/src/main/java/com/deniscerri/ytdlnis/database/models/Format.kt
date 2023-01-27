package com.deniscerri.ytdlnis.database.models

import com.google.gson.annotations.SerializedName

data class Format(
    @SerializedName(value = "format_id", alternate = ["itag"])
    var format_id: String = "",
    @SerializedName(value = "ext", alternate = ["container"])
    val container: String = "",
    @SerializedName(value = "filesize", alternate = ["clen", "filesize_approx"])
    val filesize: Long = 0,
    @SerializedName(value = "format_note", alternate = ["resolution", "audioQuality"])
    var format_note: String = ""
)