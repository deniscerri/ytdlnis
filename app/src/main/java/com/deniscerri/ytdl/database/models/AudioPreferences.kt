package com.deniscerri.ytdl.database.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AudioPreferences(
    var embedThumb: Boolean = true,
    var cropThumb: Boolean? = null,
    var splitByChapters: Boolean = false,
    var sponsorBlockFilters: ArrayList<String> = arrayListOf(),
    var bitrate: String = "",
    /** User intent: download this item as music. Remembered for the next download. */
    var musicMode: Boolean = false,
    /** The song resolved for this item: tags written and file renamed after downloading. */
    var musicMetadata: MusicMetadata? = null
) : Parcelable
