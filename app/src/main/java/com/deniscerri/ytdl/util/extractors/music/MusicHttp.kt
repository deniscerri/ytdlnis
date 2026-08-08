package com.deniscerri.ytdl.util.extractors.music

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * The single network entry point of the music package, shared by every provider so they all
 * get the same client, timeouts and failure handling.
 *
 * Every call blocks and every failure is swallowed into a null: a catalogue being unreachable
 * is a normal outcome here, the lookup simply falls through to the next one.
 */
object MusicHttp {
    private const val TAG = "MusicHttp"
    private const val TIMEOUT_SECONDS = 8L
    private const val USER_AGENT = "Mozilla/5.0"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** The parsed response body, or null when the request, the response or the parsing failed. */
    fun json(url: String): JsonObject? = runCatching {
        JsonParser.parseString(get(url) ?: return null).asJsonObject
    }.getOrElse { Log.w(TAG, "Parsing failed: $url", it); null }

    fun bytes(url: String): ByteArray? = runCatching {
        call(url).use { if (it.isSuccessful) it.body.bytes() else null }
    }.getOrElse { Log.w(TAG, "Download failed: $url", it); null }

    private fun get(url: String): String? = runCatching {
        call(url).use { if (it.isSuccessful) it.body.string() else null }
    }.getOrElse { Log.w(TAG, "Request failed: $url", it); null }

    private fun call(url: String) = client
        .newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build())
        .execute()
}
