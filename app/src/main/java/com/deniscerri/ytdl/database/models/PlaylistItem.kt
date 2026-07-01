package com.deniscerri.ytdl.database.models

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.deniscerri.ytdl.database.enums.DownloadType
import kotlinx.parcelize.Parcelize

@Entity(tableName = "playlists")
@Parcelize
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var title: String,
    var type: DownloadType,
    var createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "") var thumb: String = ""
) : Parcelable
