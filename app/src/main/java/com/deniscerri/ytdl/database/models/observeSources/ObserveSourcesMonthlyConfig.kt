package com.deniscerri.ytdl.database.models.observeSources

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.Month

@Parcelize
data class ObserveSourcesMonthlyConfig(
    var everyMonthDay: Int,
    var startsMonth: Int
) : Parcelable