package com.deniscerri.ytdlnis.database.models

import com.google.gson.annotations.SerializedName

data class Format(
    @SerializedName(value = "format_id", alternate = ["itag"])
    var format_id: String = "",
    @SerializedName(value = "ext", alternate = ["container"])
    var container: String = "",
    @SerializedName(value = "vcodec")
    var vcodec: String = "",
    @SerializedName(value = "acodec")
    var acodec: String = "",
    @SerializedName(value = "encoding")
    var encoding: String = "",
    @SerializedName(value = "filesize", alternate = ["clen", "filesize_approx"])
    var filesize: Long = 0,
    @SerializedName(value = "format_note", alternate = ["resolution", "audioQuality"])
    var format_note: String = ""
)