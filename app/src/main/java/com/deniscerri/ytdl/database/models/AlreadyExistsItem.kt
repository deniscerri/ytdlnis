package com.deniscerri.ytdl.database.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
data class AlreadyExistsItem(
    var downloadItem: DownloadItem,
    var historyID: Long? = null
) : Parcelable
