package com.deniscerri.ytdl.util.extractors.music

import com.deniscerri.ytdl.database.models.MusicMetadata
import com.deniscerri.ytdl.database.models.MusicSource
import com.google.gson.JsonObject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Deezer, the primary catalogue: it covers non english releases well and needs no key.
 *
 * Its search endpoint only carries the song, the artist, the ISRC and the album cover. The
 * release year and the track numbering live on the track resource, the genre, the label and
 * the album artist on the album resource, so [details] reads both, in parallel.
 */
object DeezerProvider : MusicProvider {

    private const val API = "https://api.deezer.com"

    override val id = "deezer"

    override suspend fun search(query: String, limit: Int): List<MusicMetadata> {
        val body = MusicHttp.json("$API/search?q=${MusicHttp.encode(query)}&limit=$limit") ?: return emptyList()
        return body.arr("data").map { it.asJsonObject.toMetadata() }.filter { it.isUsable }
    }

    override suspend fun details(metadata: MusicMetadata): MusicMetadata = coroutineScope {
        val source = metadata.source ?: return@coroutineScope metadata
        val track = async { MusicHttp.json("$API/track/${source.trackId}") }
        val album = async { MusicHttp.json("$API/album/${source.albumId}") }
        metadata.completedWith(track.await(), album.await())
    }

    private fun JsonObject.toMetadata(): MusicMetadata {
        val album = obj("album")
        return MusicMetadata(
            title = str("title"),
            artist = obj("artist")?.str("name").orEmpty(),
            album = album?.str("title").orEmpty(),
            isrc = str("isrc"),
            coverUrl = album?.str("cover_xl").orEmpty(),
            source = MusicSource(id, str("id"), album?.str("id").orEmpty())
        )
    }

    /** Both resources are optional: whichever one answered contributes what it knows. */
    private fun MusicMetadata.completedWith(track: JsonObject?, album: JsonObject?) = copy(
        year = firstNonBlank(track?.str("release_date"), album?.str("release_date")).take(YEAR_LENGTH),
        albumArtist = album?.obj("artist")?.str("name").orEmpty(),
        genre = album?.obj("genres")?.arr("data")?.firstOrNull()?.asJsonObject?.str("name").orEmpty(),
        label = album?.str("label").orEmpty(),
        trackNumber = track?.str("track_position").orEmpty(),
        trackTotal = album?.str("nb_tracks").orEmpty(),
        discNumber = track?.str("disk_number").orEmpty()
    )

    /** Release dates arrive as "2001-03-12", only the year is a tag. */
    private const val YEAR_LENGTH = 4
}
