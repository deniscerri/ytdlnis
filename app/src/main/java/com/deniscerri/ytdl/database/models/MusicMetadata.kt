package com.deniscerri.ytdl.database.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Where a [MusicMetadata] was resolved from, so the tags its catalogue only exposes on a
 * detail endpoint can be fetched later, for the single result the user keeps.
 */
@Parcelize
data class MusicSource(
    val provider: String = "",
    val trackId: String = "",
    val albumId: String = ""
) : Parcelable

/**
 * Music tags resolved for an audio download. Attached to [AudioPreferences] so the
 * download worker can write them to the finished file and rename it accordingly.
 *
 * The first four fields identify the song and are always on screen, the ones after them are
 * the extended tags the card keeps folded away until asked. Every field is optional beyond
 * the title and the artist: catalogues differ in what they know, and a blank tag is simply
 * one that never gets written.
 */
@Parcelize
data class MusicMetadata(
    var title: String = "",
    var artist: String = "",
    var album: String = "",
    var year: String = "",
    var albumArtist: String = "",
    var genre: String = "",
    var label: String = "",
    var trackNumber: String = "",
    var trackTotal: String = "",
    var discNumber: String = "",
    var isrc: String = "",
    var coverUrl: String = "",
    var source: MusicSource? = null
) : Parcelable {

    val isUsable: Boolean
        get() = title.isNotBlank() && artist.isNotBlank()

    /** "Artist - Title", the display + filename form. */
    fun displayName(): String = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" - ")

    /** "Album • Year", used as the card subtitle. */
    fun details(): String = listOf(album, year).filter { it.isNotBlank() }.joinToString(" • ")
}
