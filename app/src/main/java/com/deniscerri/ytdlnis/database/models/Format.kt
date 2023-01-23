package com.deniscerri.ytdlnis.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "formats")
data class Format(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    var itemId: Long,
    @SerializedName(value = "format_id", alternate = ["itag"])
    val format_id: String,
    @SerializedName(value = "ext", alternate = ["container"])
    val container: String,
    @SerializedName(value = "filesize", alternate = ["clen", "filesize_approx"])
    val filesize: Long,
    @SerializedName(value = "format_note", alternate = ["resolution", "audioQuality"])
    val format_note: String,
    @SerializedName(value = "encoding", alternate = ["vcodec", "acodec", "video_ext"])
    val encoding: String
)