package com.deniscerri.ytdl.database.models.observeSources

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.repository.ObserveSourcesRepository
import kotlinx.parcelize.Parcelize
import java.time.Month

@Entity(tableName = "sources")
@Parcelize
data class ObserveSourcesItem(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    var name: String,
    var url: String,
    var downloadItemTemplate: DownloadItem,

    var everyNr: Int,
    var everyCategory: ObserveSourcesRepository.EveryCategory,
    val everyTime: Long,
    var weeklyConfig: ObserveSourcesWeeklyConfig?,
    var monthlyConfig: ObserveSourcesMonthlyConfig?,

    var status: ObserveSourcesRepository.SourceStatus,
    var startsTime: Long,
    @ColumnInfo(defaultValue = "0")
    var endsDate: Long,
    @ColumnInfo(defaultValue = "0")
    var endsAfterCount: Int,
    @ColumnInfo(defaultValue = "0")
    var runCount: Int,
    @ColumnInfo(defaultValue = "0")
    var getOnlyNewUploads: Boolean,
    var retryMissingDownloads: Boolean,
    @ColumnInfo(defaultValue = "[]")
    var ignoredLinks: MutableList<String>,
    var alreadyProcessedLinks : MutableList<String>,
    @ColumnInfo(defaultValue = "0")
    var syncWithSource: Boolean
) : Parcelable
