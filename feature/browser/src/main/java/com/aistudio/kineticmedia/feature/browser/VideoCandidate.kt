package com.aistudio.kineticmedia.feature.browser

data class VideoCandidate(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String = "",
    val duration: String = "0:00",
    val hostname: String = ""
)
