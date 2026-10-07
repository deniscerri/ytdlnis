package com.deniscerri.ytdl.util.extractors.newpipe

import com.deniscerri.ytdl.App
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"


class NewPipeDownloaderImpl(builder: OkHttpClient.Builder) : Downloader() {
    private var client: OkHttpClient = builder.readTimeout(30, TimeUnit.SECONDS).build()

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        var requestBody: RequestBody? = null
        if (dataToSend != null) {
            requestBody = RequestBody.create(null, dataToSend)
        }

        val requestBuilder: okhttp3.Request.Builder = okhttp3.Request.Builder()
            .method(httpMethod, requestBody).url(url)
            .addHeader("User-Agent", USER_AGENT)

        for ((headerName, headerValueList) in headers) {
            if (headerValueList.size > 1) {
                requestBuilder.removeHeader(headerName)
                for (headerValue in headerValueList) {
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }

        // Applied after the extractor headers so they can't overwrite the cookies
        val host = request.url().toHttpUrlOrNull()?.host.orEmpty()
        if (host == "youtube.com" || host.endsWith(".youtube.com")) {
            val cookies = getCookies()
            if (cookies.isNotEmpty()) {
                val existing = headers.entries
                    .firstOrNull { it.key.equals("Cookie", true) }?.value.orEmpty()
                    .flatMap { it.split(";") }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !cookies.containsKey(it.substringBefore("=")) }
                val merged = existing + cookies.map { "${it.key}=${it.value}" }
                requestBuilder.header("Cookie", merged.joinToString("; "))

                val sapisid = cookies["SAPISID"] ?: cookies["__Secure-3PAPISID"]
                if (sapisid != null && request.url().contains("/youtubei/")) {
                    val origin = "https://www.youtube.com"
                    val ts = System.currentTimeMillis() / 1000
                    val hash = MessageDigest.getInstance("SHA-1")
                        .digest("$ts $sapisid $origin".toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    requestBuilder.header("Authorization", "SAPISIDHASH ${ts}_$hash")
                    requestBuilder.header("X-Goog-AuthUser", "0")
                    requestBuilder.header("X-Origin", origin)
                    requestBuilder.header("Origin", origin)
                }
            }
        }

        val response: okhttp3.Response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val body = response.body
        val responseBodyToReturn = body.string()
        val latestUrl = response.request.url.toString()
        return Response(
            response.code, response.message, response.headers.toMultimap(),
            responseBodyToReturn, latestUrl
        )
    }

    private var cachedCookies: Map<String, String> = emptyMap()
    private var cachedStamp = -1L

    private fun getCookies(): Map<String, String> {
        val file = File(App.instance.cacheDir, "cookies.txt")
        if (!file.exists()) {
            cachedStamp = -1L
            cachedCookies = emptyMap()
            return cachedCookies
        }
        val stamp = file.lastModified() xor file.length()
        if (stamp != cachedStamp) {
            cachedCookies = extractCookies(file)
            cachedStamp = stamp
        }
        return cachedCookies
    }

    fun extractCookies(file: File, targetDomains: List<String> = listOf("youtube.com", "google.com")) : Map<String, String> {
        if (!file.exists()) return emptyMap()

        return file.useLines { lines ->
            lines
                .map { it.trim() }
                // HttpOnly cookies are exported as "#HttpOnly_<domain>", keep them
                .map { it.removePrefix("#HttpOnly_") }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { it.split("\t") }
                .filter { it.size >= 7 }
                .filter { columns ->
                    val domain = columns[0].lowercase().trimStart('.')
                    targetDomains.any { domain == it || domain.endsWith(".$it") }
                }
                .associate { it[5] to it[6] }
        }
    }
}