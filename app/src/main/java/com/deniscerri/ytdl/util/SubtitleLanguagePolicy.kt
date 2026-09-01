package com.deniscerri.ytdl.util

/** Keeps subtitle defaults narrow and identifies optional subtitle-only failures. */
object SubtitleLanguagePolicy {
    const val LEGACY_DEFAULT = "en.*,.*-orig"
    const val SAFE_DEFAULT = "en,.*-orig"

    private val failureMarkers = listOf(
        "unable to download video subtitles",
        "unable to download subtitles",
        "subtitle download failed",
    )

    /** Migrates the old translation wildcard without changing custom selections. */
    fun normalize(value: String?): String {
        val normalized = value?.trim().orEmpty()
        return when (normalized) {
            "", LEGACY_DEFAULT -> SAFE_DEFAULT
            else -> normalized
        }
    }

    fun isDownloadFailure(output: String): Boolean {
        val normalized = output.lowercase()
        return failureMarkers.any(normalized::contains)
    }
}
