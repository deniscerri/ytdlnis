package com.deniscerri.ytdl.database.models

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistItem::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlistId"),
        Index(value = ["playlistId", "url"], unique = true)
    ]
)
@Parcelize
data class PlaylistEntryItem(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var playlistId: Long,
    var url: String,
    var title: String,
    var author: String,
    @ColumnInfo(defaultValue = "") var thumb: String = "",
    @ColumnInfo(defaultValue = "") var duration: String = "",
    var position: Int = 0
) : Parcelable
