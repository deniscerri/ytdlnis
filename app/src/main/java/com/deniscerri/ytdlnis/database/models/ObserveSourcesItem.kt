package com.deniscerri.ytdlnis.database.models

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.deniscerri.ytdlnis.database.repository.ObserveSourcesRepository
import kotlinx.parcelize.Parcelize
import java.time.Month
import java.util.Calendar
import java.util.Date

@Entity(tableName = "observeSources")
@Parcelize
data class ObserveSourcesItem(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    var name: String,
    var url: String,
    var downloadItemTemplate: DownloadItem,
    var status: ObserveSourcesRepository.SourceStatus,
    var everyNr: Int,
    var everyCategory: ObserveSourcesRepository.EveryCategory,
    val everyWeekDay: List<String>,
    val everyMonthDay: Int,
    val everyTime: Long,
    var startsTime: Long,
    val startsMonth: Month,
    @ColumnInfo(defaultValue = "0")
    var endsDate: Long,
    @ColumnInfo(defaultValue = "0")
    var endsAfterCount: Int,
    @ColumnInfo(defaultValue = "0")
    var runCount: Int,
    var retryMissingDownloads: Boolean,
    var alreadyProcessedLinks : MutableList<String>
) : Parcelable
