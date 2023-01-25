package com.deniscerri.ytdlnis.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "formats")
data class Format(
    @PrimaryKey(autoGenerate = true)
    var id: Long = -1,
    var itemId: Long = 0,
    @SerializedName(value = "format_id", alternate = ["itag"])
    val format_id: String = "",
    @SerializedName(value = "ext", alternate = ["container"])
    val container: String = "",
    @SerializedName(value = "filesize", alternate = ["clen", "filesize_approx"])
    val filesize: Long = 0,
    @SerializedName(value = "format_note", alternate = ["resolution", "audioQuality"])
    val format_note: String = ""
)