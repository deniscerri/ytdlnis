package com.deniscerri.ytdlnis.database.models

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import kotlinx.parcelize.Parcelize

@Entity(tableName = "downloads")
@Parcelize
data class DownloadItem(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    var url: String,
    var title: String,
    var author: String,
    var thumb: String,
    var duration: String,
    var type: DownloadViewModel.Type,
    var format: Format,
    @ColumnInfo(defaultValue = "Default")
    var container: String,
    @ColumnInfo(defaultValue = "")
    var downloadSections: String,
    var allFormats: MutableList<Format>,
    var downloadPath: String,
    var website: String,
    val downloadSize: String,
    var playlistTitle: String,
    val audioPreferences : AudioPreferences,
    val videoPreferences: VideoPreferences,
    @ColumnInfo(defaultValue = "")
    var extraCommands: String,
    var customFileNameTemplate: String,
    var SaveThumb: Boolean,
    @ColumnInfo(defaultValue = "Queued")
    var status: String,
    @ColumnInfo(defaultValue = "0")
    var downloadStartTime: Long,
    var logID: Long?,
    @ColumnInfo(defaultValue = "")
    var playlistURL: String? = "",
    @ColumnInfo(defaultValue = "")
    var playlistIndex: Int? = null
) : Parcelable