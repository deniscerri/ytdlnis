package com.deniscerri.ytdlnis.database.models

data class AudioPreferences(
    var embedThumb: Boolean = true,
    var splitByChapters: Boolean = false,
    var sponsorBlockFilters: ArrayList<String>
)
