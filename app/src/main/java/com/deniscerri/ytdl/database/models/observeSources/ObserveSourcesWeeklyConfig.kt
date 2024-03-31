package com.deniscerri.ytdl.database.models.observeSources

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ObserveSourcesWeeklyConfig(
    var weekDays: List<Int>
) : Parcelable