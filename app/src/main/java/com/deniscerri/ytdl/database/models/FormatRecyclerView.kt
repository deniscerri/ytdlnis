package com.deniscerri.ytdl.database.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

data class FormatRecyclerView(
    var label: String? = null,
    var format: Format? = null,
)