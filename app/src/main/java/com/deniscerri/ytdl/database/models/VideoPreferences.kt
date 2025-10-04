package com.deniscerri.ytdl.database.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class VideoPreferences (
    var embedSubs: Boolean = true,
    var addChapters: Boolean = true,
    var splitByChapters: Boolean = false,
    var sponsorBlockFilters: ArrayList<String> = arrayListOf(),
    var writeSubs: Boolean = false,
    var writeAutoSubs: Boolean = false,
    var subsLanguages: String = "en.*,.*-orig",
    var audioFormatIDs : ArrayList<String> = arrayListOf(),
    var removeAudio: Boolean = false,
    var alsoDownloadAsAudio: Boolean = false,
    var recodeVideo: Boolean = false,
    var liveFromStart: Boolean = false,
    var waitForVideoMinutes: Int = 0,
    var compatibilityMode: Boolean = false,
    var embedThumbnail: Boolean = false,
) : Parcelable
