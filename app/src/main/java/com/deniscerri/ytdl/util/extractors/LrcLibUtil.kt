package com.deniscerri.ytdl.util.extractors

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

object LrcLibUtil {
    private const val API = "https://lrclib.net/api"
    private const val APPLICATION_ID = "DzQSE74gVl7mtIo7csJ2ZkJhZXvr-8v3ilv7k9SB6SM"
    private const val DURATION_TOLERANCE = 5
    private const val MAX_ATTEMPTS = 4
    private const val MAX_RETRY_DELAY_MS = 10_000L
    private val RETRY_CODES = setOf(429, 500, 502, 503, 504)
    private val lrcStamp = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")
    private val junkBrackets = Regex(
        """\s*[(\[][^)\]]*(?:official|video|audio|lyric|visuali[sz]er|\bhd\b|\b4k\b|\bmv\b|remaster)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE
    )

    data class LrcLine(val timeMs: Long, val text: String)

    data class Lyrics(
        val id: Long,
        val trackName: String,
        val artistName: String,
        val albumName: String,
        val durationSec: Int,
        val synced: List<LrcLine>,
        val plain: String
    ) {
        val isSynced get() = synced.isNotEmpty()
    }

    private fun get(path: String, params: Map<String, String?> = emptyMap()): String? {
        val query = params.filterValues { !it.isNullOrBlank() }
            .map { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
            .joinToString("&")
        val url = URL("$API/$path" + if (query.isNotEmpty()) "?$query" else "")
        var lastError: IOException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            val conn = url.openConnection() as HttpURLConnection
            var delayMs = backoffMs(attempt)
            try {
                conn.setRequestProperty("User-Agent", "YTDLnis (https://github.com/deniscerri/ytdlnis)")
                conn.setRequestProperty("Lrclib-App-Id", APPLICATION_ID)
                conn.connectTimeout = 8000
                conn.readTimeout = 10000
                val code = conn.responseCode
                when {
                    code == 404 -> return null
                    code in RETRY_CODES -> {
                        conn.getHeaderField("Retry-After")?.toDoubleOrNull()?.let {
                            delayMs = (it * 1000).toLong().coerceIn(0, MAX_RETRY_DELAY_MS)
                        }
                        lastError = IOException("HTTP $code")
                    }
                    else -> return conn.inputStream.bufferedReader().use { it.readText() }
                }
            } catch (e: IOException) {
                lastError = e
            } finally {
                conn.disconnect()
            }
            if (attempt < MAX_ATTEMPTS - 1) Thread.sleep(delayMs)
        }
        throw lastError ?: IOException("lrclib request failed")
    }

    private fun backoffMs(attempt: Int) = minOf(1000L shl attempt, MAX_RETRY_DELAY_MS)

    fun parseLrc(text: String?): List<LrcLine> {
        if (text.isNullOrBlank()) return emptyList()
        val lines = mutableListOf<LrcLine>()
        text.lines().forEach { raw ->
            val stamps = lrcStamp.findAll(raw).toList()
            if (stamps.isEmpty()) return@forEach
            val content = raw.substring(stamps.last().range.last + 1).trim()
            stamps.forEach {
                val ms = ((it.groupValues[1].toLong() * 60 + it.groupValues[2].toDouble()) * 1000).toLong()
                lines.add(LrcLine(ms, content))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun toLyrics(o: JSONObject): Lyrics? {
        if (o.optBoolean("instrumental")) return null
        val synced = parseLrc(o.optString("syncedLyrics").takeIf { !o.isNull("syncedLyrics") })
        val plain = o.optString("plainLyrics").takeIf { !o.isNull("plainLyrics") && it.isNotBlank() }
            ?: synced.joinToString("\n") { it.text }
        if (synced.isEmpty() && plain.isBlank()) return null
        return Lyrics(o.optLong("id"), o.optString("trackName"), o.optString("artistName"),
            o.optString("albumName").takeIf { !o.isNull("albumName") } ?: "",
            o.optDouble("duration", 0.0).toInt(), synced, plain)
    }

    private fun pick(results: JSONArray, durationSec: Int): Lyrics? {
        return (0 until results.length())
            .map { results.getJSONObject(it) }
            .filter { durationSec <= 0 || abs(it.optDouble("duration") - durationSec) <= DURATION_TOLERANCE }
            .mapNotNull { o -> toLyrics(o)?.let { it to abs(o.optDouble("duration") - durationSec) } }
            .minWithOrNull(compareBy({ !it.first.isSynced }, { it.second }))
            ?.first
    }

    /**
     * Mirrors the matching in the Lyrics yt-dlp plugin so the preview is the same as the download.
     * Blocking, call from IO.
     */
    fun find(track: String, artist: String, album: String, durationSec: Int): Lyrics? {
        var cleanTrack = junkBrackets.replace(track, "").trim()
        if (artist.isNotBlank() && cleanTrack.startsWith("$artist - ", ignoreCase = true)) {
            cleanTrack = cleanTrack.substring(artist.length + 3)
        }

        if (artist.isNotBlank()) {
            get("get", mapOf(
                "track_name" to cleanTrack,
                "artist_name" to artist,
                "album_name" to album,
                "duration" to durationSec.takeIf { it > 0 }?.toString()
            ))?.let { toLyrics(JSONObject(it)) }?.let { return it }
        }

        listOf(
            mapOf("track_name" to cleanTrack, "artist_name" to artist),
            mapOf("q" to "$artist $cleanTrack".trim())
        ).forEach { params ->
            val res = get("search", params)?.let { pick(JSONArray(it), durationSec) }
            if (res != null) return res
        }
        return null
    }

    fun byId(id: Long): Lyrics? = get("get/$id")?.let { toLyrics(JSONObject(it)) }

    /** Free text search used by the manual "search again" option, no duration filter. */
    fun search(query: String): List<Lyrics> {
        val arr = JSONArray(get("search", mapOf("q" to query)) ?: return emptyList())
        return (0 until arr.length()).mapNotNull { toLyrics(arr.getJSONObject(it)) }
    }
}