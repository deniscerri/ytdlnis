package com.deniscerri.ytdl.database.models

data class YoutubePlayerClientItem(
    var playerClient: String,
    var poTokens: MutableList<YoutubePoTokenItem>,
    var enabled: Boolean = true
)

data class YoutubePoTokenItem(
    var context: String,
    var token: String
)