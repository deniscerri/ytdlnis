package com.deniscerri.ytdlnis.database.models

data class ResultItemWithFormats(
    val resultItem: ResultItem,
    var formats: ArrayList<Format>
)