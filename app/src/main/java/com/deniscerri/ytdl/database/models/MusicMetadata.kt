package com.deniscerri.ytdl.database.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Music tags resolved for an audio download. Attached to [AudioPreferences] so the
 * download worker can write them to the finished file and rename it accordingly.
 */
@Parcelize
data class MusicMetadata(
    var title: String = "",
    var artist: String = "",
    var album: String = "",
    var year: String = "",
    var genre: String = "",
    var coverUrl: String = ""
) : Parcelable {

    val isUsable: Boolean
        get() = title.isNotBlank() && artist.isNotBlank()

    /** "Artist - Title", the display + filename form. */
    fun displayName(): String = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" - ")

    /** "Album • Year", used as the card subtitle. */
    fun details(): String = listOf(album, year).filter { it.isNotBlank() }.joinToString(" • ")
}
