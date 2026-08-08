package com.deniscerri.ytdl.util.extractors.music

import com.deniscerri.ytdl.database.models.MusicMetadata
import com.deniscerri.ytdl.database.models.MusicSource
import com.google.gson.JsonObject

/**
 * iTunes, the fallback catalogue, queried when Deezer knows nothing about the song.
 *
 * Its search endpoint returns the full record in one response, so there is nothing left for
 * [details] to complete. It carries no label and no ISRC, those tags simply stay blank.
 */
object ItunesProvider : MusicProvider {

    private const val API = "https://itunes.apple.com"

    /** The search artwork is a thumbnail, the same url serves the full size cover. */
    private const val THUMBNAIL_SIZE = "100x100bb"
    private const val COVER_SIZE = "1200x1200bb"
    private const val YEAR_LENGTH = 4

    override val id = "itunes"
    override val name = "iTunes"

    override suspend fun search(query: String, limit: Int): List<MusicMetadata> {
        val body = MusicHttp.json("$API/search?term=${MusicHttp.encode(query)}&entity=song&limit=$limit")
            ?: return emptyList()
        return body.arr("results").map { it.asJsonObject.toMetadata() }.filter { it.isUsable }
    }

    private fun JsonObject.toMetadata() = MusicMetadata(
        title = str("trackName"),
        artist = str("artistName"),
        album = str("collectionName"),
        year = str("releaseDate").take(YEAR_LENGTH),
        //compilations name their album artist, single artist releases leave it out
        albumArtist = firstNonBlank(str("collectionArtistName"), str("artistName")),
        genre = str("primaryGenreName"),
        trackNumber = str("trackNumber"),
        trackTotal = str("trackCount"),
        discNumber = str("discNumber"),
        coverUrl = str("artworkUrl100").replace(THUMBNAIL_SIZE, COVER_SIZE),
        source = MusicSource(id)
    )
}
