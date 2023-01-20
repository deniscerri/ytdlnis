package com.deniscerri.ytdlnis.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "formats")
data class Format(
    var itemId: Int,
    @SerializedName(value = "format", alternate = ["type"])
    val format: String,
    @SerializedName(value = "format_id", alternate = ["itag"])
    val format_id: String,
    @SerializedName(value = "ext", alternate = ["container", ""])
    val ext: String,
    @SerializedName(value = "filesize", alternate = ["clen"])
    val filesize: Long,
    @SerializedName(value = "format_note", alternate = ["resolution", "audioQuality"])
    val format_note: String
){
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}