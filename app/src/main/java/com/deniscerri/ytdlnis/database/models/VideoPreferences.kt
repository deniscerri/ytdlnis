package com.deniscerri.ytdlnis.database.models

data class VideoPreferences (
    var embedSubs: Boolean = true,
    var addChapters: Boolean = true,
    var splitByChapters: Boolean = false,
    var sponsorBlockFilters: ArrayList<String>,
    var writeSubs: Boolean = false,
    var subsLanguages: String = "en.*,.*-orig"
)
