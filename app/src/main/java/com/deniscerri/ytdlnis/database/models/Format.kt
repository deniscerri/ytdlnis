package com.deniscerri.ytdlnis.database.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Format(
    @SerializedName(value = "format_id", alternate = ["itag"])
    var format_id: String = "",
    @SerializedName(value = "ext", alternate = ["container", "format"])
    var container: String = "",
    @SerializedName(value = "vcodec")
    var vcodec: String = "",
    @SerializedName(value = "acodec")
    var acodec: String = "",
    @SerializedName(value = "encoding")
    var encoding: String = "",
    @SerializedName(value = "filesize", alternate = ["clen", "filesize_approx", "contentLength"])
    var filesize: Long = 0,
    @SerializedName(value = "format_note", alternate = ["resolution", "audioQuality", "quality"])
    var format_note: String = "",
    @SerializedName(value = "fps")
    var fps: String? = "",
    @SerializedName(value = "asr", alternate = ["audioSampleRate"])
    var asr: String? = "",
    @SerializedName(value = "url")
    var url: String? = "",
    @SerializedName(value = "language", alternate = ["audioTrackLocale"])
    val lang: String? = "",
    @SerializedName(value = "tbr", alternate = ["bitrate"])
    var tbr: String? = ""
) : Parcelable