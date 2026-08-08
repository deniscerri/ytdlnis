package com.deniscerri.ytdl.util.extractors.music

import android.util.Log
import com.deniscerri.ytdl.database.models.MusicMetadata
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Resolves music tags (song, artist, album, year, genre, cover) for a video title
 * by querying Deezer first and falling back to iTunes.
 *
 * The title parsing is heuristic: a video title like
 * "Dhurata Dora ft. Soolking - Zemër (Official Video)" becomes
 * artist="Dhurata Dora", title="Zemër", which is what the APIs expect.
 */
object MusicMetadataUtil {
    private const val TAG = "MusicMetadataUtil"

    // ── Title parsing ──────────────────────────────────────────────────────────

    // Featuring inside the artist field: "Dhurata Dora ft. Soolking" → "Dhurata Dora"
    private val FEAT_IN_ARTIST = Regex("""\s+(?:ft\.?|feat\.?|featuring)\s+.+$""", RegexOption.IGNORE_CASE)

    // Featuring inside the title, both bracketed and bare (removed while cleaning)
    private val FEAT_IN_TITLE = listOf(
        Regex("""\s*[\(\[]\s*(?:ft\.?|feat\.?|featuring)\s+[^\)\]]+[\)\]]""", RegexOption.IGNORE_CASE),
        Regex("""\s+(?:ft\.?|feat\.?|featuring)\s+.+$""", RegexOption.IGNORE_CASE),
    )

    private val FEAT_BRACKET = Regex("""[\(\[]\s*(?:ft\.?|feat\.?|featuring)\s+([^\)\]]+)[\)\]]""", RegexOption.IGNORE_CASE)
    private val FEAT_BARE = Regex("""\s+(?:ft\.?|feat\.?|featuring)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val HAS_FEAT = Regex("""(?:ft\.?|feat\.?|featuring)""", RegexOption.IGNORE_CASE)

    // Bracketed noise commonly appended to video titles
    private val BRACKET_NOISE = Regex(
        """[\(\[]\s*(?:""" +
                """official\s*(?:music\s*|lyric\s*)?(?:video|audio|mv)?|""" +
                """lyrics?\s*(?:video)?|""" +
                """audio|hd|hq|4k|uhd|""" +
                """remaster(?:ed)?(?:\s+\d+)?|""" +
                """visuali[sz]er|tiktok|""" +
                """slowed(?:\s*[+&]\s*reverb)?|sped[\s-]*up|nightcore|""" +
                """radio[\s-]*edit|""" +
                """(?:piano|violin|guitar|acoustic|orchestral)\s*(?:version|cover|solo)?|""" +
                """(?:\w[\w\s]*?\s+)?cover(?:\s+by\s+[^\)]+)?|""" +
                """(?:\w[\w\s]*?\s+)?remix|""" +
                """(?:album|single|extended|clean|explicit|radio)\s*(?:version|mix|edit)?|""" +
                """non[\s-]?credits?(?:\s+(?:opening|ending|op|ed))?|""" +
                """(?:opening|ending|op|ed)\s*\d*|""" +
                """ost|version|edit|music\s+video""" +
                """)\s*[\)\]]""",
        RegexOption.IGNORE_CASE
    )

    private val ARTIST_SPLIT = Regex("""\s*,\s*|\s*&\s*|\s+[xX]\s+""")
    private val TITLE_SPLIT = Regex("""\s*[-–—|]\s*""")
    private val UNSAFE_FILENAME = Regex("""[\\/:*?"<>|]""")

    private fun parseArtistField(raw: String): List<String> =
        ARTIST_SPLIT.split(FEAT_IN_ARTIST.replace(raw, "").trim())
            .map { it.trim() }.filter { it.isNotEmpty() }

    private fun formatArtists(artists: List<String>): String = when {
        artists.size >= 3 -> artists.first()                      // 3+ artists is too noisy to match
        artists.size == 2 -> "${artists[0]} & ${artists[1]}"
        else -> artists.firstOrNull().orEmpty()
    }

    /** Strips featuring info, bracketed noise and trailing descriptions from a video title. */
    fun cleanTitle(raw: String): String {
        var s = raw
        FEAT_IN_TITLE.forEach { s = it.replace(s, "") }
        s = BRACKET_NOISE.replace(s, "")
        s = Regex("""\s*\|\s*.+$""").replace(s, "")     // trailing "| description"
        s = Regex("""//\s*.+$""").replace(s, "")        // trailing "// description"
        s = Regex("""\s{2,}""").replace(s, " ")
        return s.trim().trimEnd('-', '|', ' ', ',')
    }

    /** Removes the " - Topic" suffix auto-generated channels carry. */
    fun cleanUploader(raw: String): String =
        raw.replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "").trim()

    /**
     * Featuring artists mentioned in the title portion only, so that
     * "Dhurata Dora ft. Soolking - Zemër" doesn't report Soolking (a main artist there).
     */
    fun extractFeaturing(rawVideoTitle: String): String? {
        val parts = rawVideoTitle.split(Regex("""\s*[-–]\s*"""), limit = 2)
        val searchIn = if (parts.size == 2) parts[1].trim() else rawVideoTitle

        FEAT_BRACKET.find(searchIn)?.groupValues?.get(1)?.trim()?.trimEnd('.', ' ')?.ifBlank { null }
            ?.let { return it }
        FEAT_BARE.find(searchIn)?.groupValues?.get(1)?.trim()?.trimEnd('.', ' ')?.ifBlank { null }
            ?.let { return it }
        return null
    }

    /** Appends "(feat. X)" from the original video title if the API result dropped it. */
    fun enrichWithFeaturing(metadata: MusicMetadata, rawVideoTitle: String): MusicMetadata {
        if (HAS_FEAT.containsMatchIn(metadata.title)) return metadata
        val feat = extractFeaturing(rawVideoTitle) ?: return metadata
        return metadata.copy(title = "${metadata.title} (feat. $feat)")
    }

    /**
     * Ordered (artist, title) candidates for a video title.
     * The reversed candidate covers "Song - Artist1 & Artist2" style titles.
     */
    fun buildSearchQueries(rawTitle: String, uploader: String = ""): List<Pair<String, String>> {
        val parts = rawTitle.split(TITLE_SPLIT, limit = 2)
        if (parts.size != 2) {
            // No separator: the uploader is the best artist guess (music channels, " - Topic")
            return listOf(formatArtists(parseArtistField(cleanUploader(uploader))) to cleanTitle(rawTitle))
        }

        val left = parts[0].trim()
        val right = parts[1].trim()
        val queries = mutableListOf(formatArtists(parseArtistField(left)) to cleanTitle(right))

        val leftHasSeparators = ARTIST_SPLIT.containsMatchIn(left)
        val rightHasSeparators = ARTIST_SPLIT.containsMatchIn(right)
        if (rightHasSeparators && !leftHasSeparators && !right.contains('(')) {
            queries.add(formatArtists(parseArtistField(cleanTitle(right))) to cleanTitle(left))
        }
        return queries
    }

    /** Filesystem-safe "Artist - Title.ext". */
    fun buildFileName(metadata: MusicMetadata, extension: String): String {
        val name = UNSAFE_FILENAME.replace(metadata.displayName(), "_")
            .replace(Regex("""\s{2,}"""), " ").trim()
        return if (extension.isBlank()) name else "$name.$extension"
    }

    // ── Networking ─────────────────────────────────────────────────────────────

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    internal fun httpGet(url: String): String? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        client.newCall(request).execute().use { res ->
            if (res.isSuccessful) res.body?.string() else null
        }
    }.getOrElse { Log.w(TAG, "Request failed: $url", it); null }

    internal fun downloadBytes(url: String): ByteArray? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        client.newCall(request).execute().use { res ->
            if (res.isSuccessful) res.body?.bytes() else null
        }
    }.getOrElse { Log.w(TAG, "Download failed: $url", it); null }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun JsonObject.str(key: String): String =
        runCatching { get(key)?.takeIf { !it.isJsonNull }?.asString.orEmpty() }.getOrDefault("")

    private fun JsonObject.obj(key: String): JsonObject? =
        runCatching { getAsJsonObject(key) }.getOrNull()

    // ── Providers ──────────────────────────────────────────────────────────────

    private fun fetchFromDeezer(query: String, limit: Int): List<MusicMetadata> = runCatching {
        val raw = httpGet("https://api.deezer.com/search?q=${encode(query)}&limit=$limit") ?: return emptyList()
        JsonParser.parseString(raw).asJsonObject.getAsJsonArray("data").map { element ->
            val track = element.asJsonObject
            val album = track.obj("album")
            MusicMetadata(
                title = track.str("title"),
                artist = track.obj("artist")?.str("name").orEmpty(),
                album = album?.str("title").orEmpty(),
                year = album?.str("release_date")?.take(4).orEmpty(),
                coverUrl = album?.str("cover_xl").orEmpty()
            )
        }.filter { it.isUsable }
    }.getOrElse { Log.w(TAG, "Deezer parsing failed", it); emptyList() }

    private fun fetchFromItunes(query: String, limit: Int): List<MusicMetadata> = runCatching {
        val raw = httpGet("https://itunes.apple.com/search?term=${encode(query)}&entity=song&limit=$limit")
            ?: return emptyList()
        JsonParser.parseString(raw).asJsonObject.getAsJsonArray("results").map { element ->
            val track = element.asJsonObject
            MusicMetadata(
                title = track.str("trackName"),
                artist = track.str("artistName"),
                album = track.str("collectionName"),
                year = track.str("releaseDate").take(4),
                genre = track.str("primaryGenreName"),
                coverUrl = track.str("artworkUrl100").replace("100x100bb", "1200x1200bb")
            )
        }.filter { it.isUsable }
    }.getOrElse { Log.w(TAG, "iTunes parsing failed", it); emptyList() }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Explicit search, used by the manual "artist + song" lookup. */
    suspend fun search(artist: String, title: String, limit: Int = MATCH_LIMIT): List<MusicMetadata> =
        withContext(Dispatchers.IO) {
            val query = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
            if (query.isBlank()) return@withContext emptyList()
            fetchFromDeezer(query, limit).ifEmpty { fetchFromItunes(query, limit) }
        }

    /**
     * Automatic lookup from the fetched video info. Tries every parsed candidate and
     * enriches the results with featuring artists mentioned in the original title.
     */
    suspend fun searchFromVideo(videoTitle: String, uploader: String, limit: Int = MATCH_LIMIT): List<MusicMetadata> =
        withContext(Dispatchers.IO) {
            buildSearchQueries(videoTitle, uploader)
                .firstNotNullOfOrNull { (artist, title) ->
                    search(artist, title, limit).ifEmpty { null }
                }
                ?.map { enrichWithFeaturing(it, videoTitle) }
                ?: emptyList()
        }

    const val MATCH_LIMIT = 8
}
