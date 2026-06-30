package com.deniscerri.ytdl.database.models

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(
    tableName = "channels",
    indices = [Index(value = ["url"], unique = true)]
)
@Parcelize
data class ChannelItem(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var name: String,
    var url: String,
    @ColumnInfo(defaultValue = "") var thumb: String = ""
) : Parcelable
