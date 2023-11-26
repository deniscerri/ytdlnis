package com.deniscerri.ytdlnis.database.models

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "results")
@Parcelize
data class ResultItem(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    var url: String,
    var title: String,
    var author: String,
    val duration: String,
    val thumb: String,
    val website: String,
    var playlistTitle: String,
    var formats: MutableList<Format>,
    @ColumnInfo(defaultValue = "")
    var urls: String,
    var chapters: MutableList<ChapterItem>?,
    @ColumnInfo(defaultValue = "")
    var playlistURL: String? = "",
    @ColumnInfo(defaultValue = "")
    var playlistIndex: Int? = null,
    var creationTime: Long = System.currentTimeMillis() / 1000,
) : Parcelable