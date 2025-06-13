package com.deniscerri.ytdl.util.extractors

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.anggrayudi.storage.extension.count
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.dao.CommandTemplateDao
import com.deniscerri.ytdl.database.models.ChapterItem
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.models.YoutubeGeneratePoTokenItem
import com.deniscerri.ytdl.database.models.YoutubePlayerClientItem
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.util.Extensions.getIntByAny
import com.deniscerri.ytdl.util.Extensions.getStringByAny
import com.deniscerri.ytdl.util.Extensions.isURL
import com.deniscerri.ytdl.util.Extensions.isYoutubeURL
import com.deniscerri.ytdl.util.Extensions.isYoutubeWatchVideosURL
import com.deniscerri.ytdl.util.Extensions.toStringDuration
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.FormatUtil
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Type
import java.util.ArrayList
import java.util.Locale
import java.util.StringJoiner
import java.util.UUID
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

val TWITCH_VOD_REGEX = "https?://(?:www\\.|m\\.)?twitch\\.tv/videos/(\\d+)".toRegex()

class YTDLPUtil(private val context: Context, private val commandTemplateDao: CommandTemplateDao) {
    private var sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val formatUtil = FormatUtil(context)
    private val handler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Constantes (tirées du script JS)
    private val GQL_URL = "https://gql.twitch.tv/gql"
    private val CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
    private val RESOLUTIONS = mapOf(
        "chunked" to mapOf("res" to "1920x1080", "fps" to 60, "bitrate" to 6000), // Source quality, often 1080p60
        "1080p60" to mapOf("res" to "1920x1080", "fps" to 60, "bitrate" to 6000),
        "1080p30" to mapOf("res" to "1920x1080", "fps" to 30, "bitrate" to 4500),
        "720p60" to mapOf("res" to "1280x720", "fps" to 60, "bitrate" to 4500),
        "720p30" to mapOf("res" to "1280x720", "fps" to 30, "bitrate" to 3000),
        "480p30" to mapOf("res" to "854x480", "fps" to 30, "bitrate" to 1500),
        "360p30" to mapOf("res" to "640x360", "fps" to 30, "bitrate" to 1000),
        "160p30" to mapOf("res" to "284x160", "fps" to 30, "bitrate" to 500)
    )
    private val QUALITIES_ORDER = listOf(
        "chunked", "1080p60", "1080p30", "720p60", "720p30", "480p30", "360p30", "160p30"
    )

    private fun fetchTwitchGQL(queryBody: JSONObject): JSONObject? {
        return try {
            val requestBody = queryBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(GQL_URL)
                .post(requestBody)
                .header("Client-ID", CLIENT_ID)
                .header("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("TwitchDownloader", "Erreur lors de la requête GraphQL: ${response.code} - ${response.message}")
                return null
            }
            response.body?.string()?.let { JSONObject(it) }

        } catch (e: IOException) {
            Log.e("TwitchDownloader", "Erreur réseau lors de la requête GraphQL: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e("TwitchDownloader", "Erreur inattendue lors du traitement GraphQL: ${e.message}")
            null
        }
    }

    private fun getVodMetadata(vodId: String): JSONObject? {
        val query = JSONObject().apply {
            put("operationName", "VideoMetadata")
            put("variables", JSONObject().apply {
                put("channelLogin", "")
                put("videoID", vodId)
            })
            put("extensions", JSONObject().apply {
                put("persistedQuery", JSONObject().apply {
                    put("version", 1)
                    put("sha256Hash", "0921ba77e7396a766ff2c90694c8568e3c71c758af657509e145d51a1c7f32d1")
                })
            })
        }
        Log.d("TwitchDownloader", "Récupération des métadonnées pour VOD ID: $vodId...")
        var data = fetchTwitchGQL(query)
        if (data != null && data.optJSONObject("data")?.optJSONObject("video") != null) {
            Log.d("TwitchDownloader", "Métadonnées GraphQL récupérées.")
            return data.getJSONObject("data").getJSONObject("video")
        } else {
            Log.w("TwitchDownloader", "Impossible de récupérer les métadonnées GraphQL complètes pour VOD $vodId.")
            val gqlErrors = data?.optJSONArray("errors")?.toString() ?: "Aucune donnée reçue"
            Log.d("TwitchDownloader", "Réponse GraphQL brute: $data")
            Log.d("TwitchDownloader", "Erreurs GraphQL: $gqlErrors")
            Log.i("TwitchDownloader", "Tentative avec une requête GraphQL de secours pour VOD $vodId...")
            val queryFallback = JSONObject().apply {
                put("query", "query { video(id: \"$vodId\") { title, lengthSeconds, previewThumbnailURL, broadcastType, createdAt, seekPreviewsURL, owner { login, displayName } } }")
            }
            val dataFallback = fetchTwitchGQL(queryFallback)
            if (dataFallback != null && dataFallback.optJSONObject("data")?.optJSONObject("video") != null) {
                Log.d("TwitchDownloader", "Métadonnées de secours récupérées.")
                return dataFallback.getJSONObject("data").getJSONObject("video")
            } else {
                Log.e("TwitchDownloader", "Échec de la récupération des métadonnées, même avec la requête de secours pour VOD $vodId.")
                return null
            }
        }
    }

    private fun checkUrlValidity(url: String): Boolean {
        return try {
            val headers = okhttp3.Headers.Builder()
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build()
            val request = Request.Builder()
                .url(url)
                .head()
                .headers(headers)
                .build()
            val response = httpClient.newCall(request).execute()
            val isValid = response.code == 200
            Log.d("TwitchDownloader", "  -> Test URL $url - Status: ${response.code}")
            isValid
        } catch (e: IOException) {
            Log.d("TwitchDownloader", "  -> Test URL $url - Erreur: ${e.message}")
            false
        }
    }

    private fun findAllM3u8UrlsAndBest(vodId: String, metadata: JSONObject?, desiredQuality: String?): Pair<String?, List<Pair<String, String>>> {
        if (metadata == null || !metadata.has("seekPreviewsURL") || metadata.getString("seekPreviewsURL").isNullOrBlank()) {
            Log.w("TwitchDownloader", "Métadonnées ou seekPreviewsURL manquantes pour trouver l'URL M3U8.")
            return Pair(null, emptyList())
        }

        try {
            val seekPreviewsUrl = metadata.getString("seekPreviewsURL")
            val parsedUrl = seekPreviewsUrl.toHttpUrlOrNull() ?: return Pair(null, emptyList())
            val domain = parsedUrl.host
            val paths = parsedUrl.pathSegments

            var storyboardIndex = -1
            for (i in paths.indices) {
                if (paths[i].contains("storyboards")) {
                    storyboardIndex = i
                    break
                }
            }
            if (storyboardIndex <= 0) {
                Log.e("TwitchDownloader", "Impossible de trouver l'ID spécial (avant 'storyboards') dans seekPreviewsURL: $seekPreviewsUrl")
                return Pair(null, emptyList())
            }
            val vodSpecialId = paths[storyboardIndex - 1]

            val channelLogin = metadata.optJSONObject("owner")?.optString("login", "inconnu") ?: "inconnu"
            val broadcastType = metadata.optString("broadcastType", "").lowercase(Locale.ROOT)
            val createdAtStr = metadata.optString("createdAt", "")
            var daysDifference = Double.POSITIVE_INFINITY
            if (createdAtStr.isNotBlank()) {
                try {
                    val createdAt = OffsetDateTime.parse(createdAtStr)
                    val now = OffsetDateTime.now(createdAt.offset)
                    val timeDifference = Duration.between(createdAt, now)
                    daysDifference = timeDifference.seconds / (3600.0 * 24.0)
                } catch (e: DateTimeParseException) {
                    Log.w("TwitchDownloader", "Format de date invalide: $createdAtStr")
                }
            }

            Log.i("TwitchDownloader", "Recherche des URLs M3U8 pour VOD $vodId (Type: $broadcastType, Age: ${String.format(Locale.ROOT, "%.1f", daysDifference)} jours).")
            if (desiredQuality != null) {
                Log.i("TwitchDownloader", "Qualité désirée : $desiredQuality")
            }

            val qualitiesToCheck = QUALITIES_ORDER.toMutableList()
            
            val foundQualities = mutableListOf<Pair<String, String>>()
            val foundResolutions = mutableSetOf<String>()


            for (resKey in qualitiesToCheck) {
                val resInfo = RESOLUTIONS[resKey] ?: continue
                val resolution = resInfo["res"] as? String ?: ""

                if (resolution.isNotEmpty() && foundResolutions.contains(resolution)) {
                    continue
                }
                
                val urlsToTest = mutableListOf<String>()

                val standardUrl = "https://$domain/$vodSpecialId/$resKey/index-dvr.m3u8"
                urlsToTest.add(standardUrl)

                if (broadcastType == "highlight") {
                    val highlightUrl = "https://$domain/$vodSpecialId/$resKey/highlight-$vodId.m3u8"
                    urlsToTest.add(0, highlightUrl)
                } else if (broadcastType == "upload" && daysDifference > 7) {
                    val oldUploadUrl = "https://$domain/$channelLogin/$vodId/$vodSpecialId/$resKey/index-dvr.m3u8"
                    urlsToTest.add(0, oldUploadUrl)
                }

                for (urlToTest in urlsToTest) {
                    Log.d("TwitchDownloader", "Test qualité '$resKey' avec URL: $urlToTest")
                    if (checkUrlValidity(urlToTest)) {
                        Log.i("TwitchDownloader", "URL M3U8 trouvée pour qualité '$resKey': $urlToTest")
                        foundQualities.add(resKey to urlToTest)
                        if (resolution.isNotEmpty()) {
                            foundResolutions.add(resolution)
                        }
                        break 
                    }
                }
            }

            if(foundQualities.isEmpty()){
                 Log.w("TwitchDownloader", "Aucune URL M3U8 valide trouvée via la méthode de l'extension pour VOD $vodId.")
                return Pair(null, emptyList())
            }

            var bestUrl = foundQualities.firstOrNull { it.first == desiredQuality }?.second
            if (bestUrl == null) {
                bestUrl = foundQualities.firstOrNull()?.second
            }
            
            return Pair(bestUrl, foundQualities)

        } catch (e: Exception) {
            Log.e("TwitchDownloader", "Erreur inattendue lors de la recherche de l'URL M3U8 pour VOD $vodId: ${e.message}", e)
            return Pair(null, emptyList())
        }
    }

    private fun YoutubeDLRequest.applyDefaultOptionsForFetchingData(url: String?) {
        addOption("--skip-download")
        addOption("--quiet")
        addOption("--ignore-errors")
        addOption("--no-warnings")
        addOption("-R", "1")
        addOption("--compat-options", "manifest-filesize-approx")
        val socketTimeout = sharedPreferences.getString("socket_timeout", "5")!!.ifEmpty { "5" }
        addOption("--socket-timeout", socketTimeout)

        if (url != null && TWITCH_VOD_REGEX.matches(url)) {
            addOption("--force-generic-extractor")
        }

        if (sharedPreferences.getBoolean("force_ipv4", false)){
            addOption("-4")
        }

        if (sharedPreferences.getBoolean("use_cookies", false)){
            FileUtil.getCookieFile(context){
                addOption("--cookies", it)
            }

            val useHeader = sharedPreferences.getBoolean("use_header", false)
            val header = sharedPreferences.getString("useragent_header", "")
            if (useHeader && !header.isNullOrBlank()){
                addOption("--add-header","User-Agent:${header}")
            }
        }

        var extraCommands = commandTemplateDao.getAllTemplatesAsDataFetchingExtraCommands()

        if (url != null) {
            extraCommands = extraCommands.filter { it.urlRegex.any { u -> Regex(u).containsMatchIn(url) } }
        }else{
            extraCommands = extraCommands.filter { it.urlRegex.isEmpty() }
        }

        if (extraCommands.isNotEmpty()){
            //addCommands(extraCommands.split(" ", "\t", "\n"))
            addConfig(extraCommands.joinToString(" ") { it.content })
        }
    }

    @SuppressLint("RestrictedApi")
    fun getFromYTDL(query: String, singleItem: Boolean = false): ArrayList<ResultItem> {
        val searchEngine = sharedPreferences.getString("search_engine", "ytsearch")
        var request: YoutubeDLRequest

        val twitchVodMatch = TWITCH_VOD_REGEX.find(query)
        if (twitchVodMatch != null) {
            val vodId = twitchVodMatch.groupValues[1]
            Log.i("TwitchDownloader", "Twitch VOD URL détectée. VOD ID: $vodId")
            val metadata = getVodMetadata(vodId)
            if (metadata != null) {
                Log.d("TwitchDownloader", "Full metadata received: ${metadata.toString(2)}")
                val (bestUrl, allUrls) = findAllM3u8UrlsAndBest(vodId, metadata, null)
                if (bestUrl != null) {
                    Log.i("TwitchDownloader", "URL M3U8 directe trouvée pour Twitch VOD: $bestUrl")

                    val title = metadata.optString("title", "Twitch VOD $vodId")
                    val author = metadata.optJSONObject("owner")?.optString("displayName") ?: metadata.optJSONObject("owner")?.optString("login") ?: "Unknown Streamer"
                    var thumb = metadata.optString("previewThumbnailURL", "")
                    Log.d("TwitchDownloader", "Original thumbnail URL from metadata: '$thumb'")
                    if (thumb.contains("{width}") && thumb.contains("{height}")) {
                        thumb = thumb.replace("{width}", "640").replace("{height}", "360")
                        Log.d("TwitchDownloader", "Formatted thumbnail URL: '$thumb'")
                    }
                    val durationInSeconds = metadata.optInt("lengthSeconds", -1)
                    val duration = if(durationInSeconds != -1) durationInSeconds.toStringDuration(Locale.US) else "N/A"

                    val formats = ArrayList<Format>()
                    allUrls.forEach { (quality, url) ->
                        val resInfo = RESOLUTIONS[quality]
                        val note = if (quality == "chunked") "Source (Best)" else quality
                        val bitrate = resInfo?.get("bitrate") as? Int ?: 0
                        val estimatedSize = if (durationInSeconds > 0 && bitrate > 0) {
                            (bitrate.toLong() * 1000 / 8) * durationInSeconds.toLong()
                        } else {
                            0
                        }

                        val format = Format(
                            url = url,
                            container = "mp4",
                            format_note = note,
                            format_id = quality,
                            vcodec = "avc1",
                            acodec = "mp4a",
                            filesize = estimatedSize,
                            fps = resInfo?.get("fps")?.toString()
                        )
                        formats.add(format)
                    }

                    val resultItem = ResultItem(
                        id = 0,
                        url = bestUrl,
                        title = title,
                        author = author,
                        duration = duration,
                        thumb = thumb,
                        website = "Twitch",
                        formats = formats,
                        chapters = arrayListOf(),
                        urls = bestUrl,
                        playlistTitle = "",
                        playlistURL = "",
                        playlistIndex = null
                    )
                    return arrayListOf(resultItem)
                }
            }
            Log.w("TwitchDownloader", "Falling back to yt-dlp for Twitch VOD $vodId (direct M3u8 not found or metadata missing).")
            request = YoutubeDLRequest(query)
        } else if (query.contains("http")) {
            if (query.isYoutubeWatchVideosURL()) {
                request = YoutubeDLRequest(emptyList())
                val config = File(context.cacheDir.absolutePath + "/config" + System.currentTimeMillis() + "##url.txt")
                config.writeText(query)
                request.addOption("--config", config.absolutePath)
            } else {
                request = YoutubeDLRequest(query)
            }
        } else {
            request = YoutubeDLRequest(emptyList())
            when (searchEngine) {
                "ytsearchmusic" -> {
                    request.addOption("--default-search", "https://music.youtube.com/search?q=")
                    request.addOption("ytsearch25:\"${query}\"")
                }
                else -> {
                    request.addOption("${searchEngine}25:\"${query}\"")
                }
            }
        }

        if (searchEngine == "ytsearch" || query.isYoutubeURL()) {
            request.setYoutubeExtractorArgs(query)
        }

        request.addOption("--flat-playlist")
        request.addOption(if (singleItem) "-J" else "-j")
        request.applyDefaultOptionsForFetchingData(if (query.isURL()) query else null)

        println(parseYTDLRequestString(request))
        val youtubeDLResponse = YoutubeDL.getInstance().execute(request)
        val results: List<String?> = try {
            val lineSeparator = System.getProperty("line.separator")
            youtubeDLResponse.out.split(lineSeparator!!)
        } catch (e: Exception) {
            listOf(youtubeDLResponse.out)
        }.filter { it.isNotBlank() }.apply {
            if (this.isEmpty()) throw Exception("Command Used: \n yt-dlp ${parseYTDLRequestString(request)}")
        }

        return parseYTDLPListResults(results, query)
    }

    private fun parseYTDLPListResults(results: List<String?>, query: String = ""): ArrayList<ResultItem> {
        val items = arrayListOf<ResultItem>()

        for (result in results) {
            if (result.isNullOrBlank()) continue
            val jsonObject = JSONObject(result)
            val title = jsonObject.getStringByAny("alt_title", "title", "webpage_url_basename")
            if (title == "[Private video]" || title == "[Deleted video]") continue

            var playlistTitle = jsonObject.getStringByAny("playlist_title")
            var playlistURL: String? = ""
            var playlistIndex: Int? = null

            if(playlistTitle.removeSurrounding("\"") == query) playlistTitle = ""

            if (playlistTitle.isNotBlank()){
                playlistURL = jsonObject.getStringByAny("playlist_webpage_url").ifEmpty { query }
                kotlin.runCatching { playlistIndex = jsonObject.getInt("playlist_index") }
            }
            val url = if (jsonObject.has("url") && results.size > 1){
                jsonObject.getString("url")
            }else{
                if (Patterns.WEB_URL.matcher(query).matches() && playlistURL?.isEmpty() == true){
                    query
                }else{
                    jsonObject.getStringByAny("webpage_url", "original_url", "url", )
                    jsonObject.getString("webpage_url")
                }
            }

            val authorTags = mutableListOf("uploader", "channel", "playlist_uploader", "uploader_id")
            if (url.isYoutubeURL()) {
                authorTags.addAll(0, listOf("artists", "artist"))
            }
            var author = jsonObject.getStringByAny(*authorTags.map { it }.toTypedArray()).removeSuffix(" - Topic")
            var duration = jsonObject.getIntByAny("duration").toString()
            if (duration != "-1"){
                duration = jsonObject.getInt("duration").toStringDuration(Locale.US)
            }

            var thumb: String? = ""
            if (jsonObject.has("thumbnail")) {
                thumb = jsonObject.getString("thumbnail")
            } else if (jsonObject.has("thumbnails")) {
                val thumbs = jsonObject.getJSONArray("thumbnails")
                if (thumbs.length() > 0){
                    thumb = thumbs.getJSONObject(thumbs.length() - 1).getString("url")
                }
            }

            if(author.isEmpty()){
                runCatching {
                    val firstEntry = jsonObject.getJSONArray("entries").getJSONObject(0)
                    author = firstEntry.getStringByAny("uploader", "channel", "playlist_uploader", "uploader_id")
                    val thumbs = firstEntry.getJSONArray("thumbnails")
                    if (thumbs.length() > 0){
                        thumb = thumbs.getJSONObject(thumbs.length() - 1).getString("url")
                    }
                }
            }

            var website = jsonObject.getStringByAny("extractor_key", "extractor","ie_key")
            if (website == "Generic" || website == "HTML5MediaEmbed") website = jsonObject.getStringByAny("webpage_url_domain")

            val formatsInJSON = if (jsonObject.has("formats") && jsonObject.get("formats") is JSONArray) jsonObject.getJSONArray("formats") else null
            val formats : ArrayList<Format> = parseYTDLFormats(formatsInJSON)

            val chaptersInJSON = if (jsonObject.has("chapters") && jsonObject.get("chapters") is JSONArray) jsonObject.getJSONArray("chapters") else null
            val listType: Type = object : TypeToken<List<ChapterItem>>() {}.type
            var chapters : ArrayList<ChapterItem> = arrayListOf()

            if (chaptersInJSON != null){
                chapters = Gson().fromJson(chaptersInJSON.toString(), listType)
            }

            var urls = "";
            if(jsonObject.has("requested_formats")) {
                val requestedFormats = jsonObject.getJSONArray("requested_formats")
                val urlList = mutableListOf<String>()
                val length = requestedFormats.length()-1
                for (i in length downTo 0) {
                    urlList.add(requestedFormats.getJSONObject(i).getString("url"))
                }

                urls = urlList.joinToString("\n")
            }

            val type = jsonObject.getStringByAny("_type")
            if (type == "playlist" && playlistTitle.isEmpty()) {
                playlistTitle = title
            }

            val res = ResultItem(0,
                url,
                title,
                author,
                duration,
                thumb!!,
                website,
                playlistTitle,
                formats,
                urls,
                chapters,
                playlistURL,
                playlistIndex
            )

            items.add(res)
        }

        return items
    }

    fun getYoutubeWatchLater() : ArrayList<ResultItem> {
        val request = YoutubeDLRequest(listOf())
        request.setYoutubeExtractorArgs(null)
        request.addOption( "-j")
        request.addOption("--flat-playlist")
        request.applyDefaultOptionsForFetchingData(null)
        request.addOption(":ytwatchlater")
        val youtubeDLResponse = YoutubeDL.getInstance().execute(request)
        val results: List<String?> = try {
            val lineSeparator = System.getProperty("line.separator")
            youtubeDLResponse.out.split(lineSeparator!!)
        } catch (e: Exception) {
            listOf(youtubeDLResponse.out)
        }.filter { it.isNotBlank() }.apply {
            if (this.isEmpty()) return arrayListOf()
        }
        return parseYTDLPListResults(results)
    }

    fun getYoutubeRecommendations() : ArrayList<ResultItem> {
        val request = YoutubeDLRequest(listOf())
        request.setYoutubeExtractorArgs(null)
        request.addOption( "-j")
        request.addOption("--flat-playlist")
        request.applyDefaultOptionsForFetchingData(null)
        request.addOption(":ytrec")
        val youtubeDLResponse = YoutubeDL.getInstance().execute(request)
        val results: List<String?> = try {
            val lineSeparator = System.getProperty("line.separator")
            youtubeDLResponse.out.split(lineSeparator!!)
        } catch (e: Exception) {
            listOf(youtubeDLResponse.out)
        }.filter { it.isNotBlank() }.apply {
            if (this.isEmpty()) return arrayListOf()
        }
        return parseYTDLPListResults(results)
    }

    fun getYoutubeLikedVideos() : ArrayList<ResultItem> {
        val request = YoutubeDLRequest(listOf())
        request.setYoutubeExtractorArgs(null)
        request.addOption( "-j")
        request.addOption("--flat-playlist")
        request.applyDefaultOptionsForFetchingData(null)
        request.addOption(":ytfav")
        val youtubeDLResponse = YoutubeDL.getInstance().execute(request)
        val results: List<String?> = try {
            val lineSeparator = System.getProperty("line.separator")
            youtubeDLResponse.out.split(lineSeparator!!)
        } catch (e: Exception) {
            listOf(youtubeDLResponse.out)
        }.filter { it.isNotBlank() }.apply {
            if (this.isEmpty()) return arrayListOf()
        }
        return parseYTDLPListResults(results)
    }

    fun getYoutubeWatchHistory() : ArrayList<ResultItem> {
        val request = YoutubeDLRequest(listOf())
        request.setYoutubeExtractorArgs(null)
        request.addOption( "-j")
        request.addOption("--flat-playlist")
        request.applyDefaultOptionsForFetchingData(null)
        request.addOption(":ythis")
        val youtubeDLResponse = YoutubeDL.getInstance().execute(request)
        val results: List<String?> = try {
            val lineSeparator = System.getProperty("line.separator")
            youtubeDLResponse.out.split(lineSeparator!!)
        } catch (e: Exception) {
            listOf(youtubeDLResponse.out)
        }.filter { it.isNotBlank() }.apply {
            if (this.isEmpty()) return arrayListOf()
        }
        return parseYTDLPListResults(results)
    }

    suspend fun getFormatsForAll(urls: List<String>, progress: (progress: ResultViewModel.MultipleFormatProgress) -> Unit) : Result<MutableList<MutableList<Format>>>  {
        val formatCollection = mutableListOf<MutableList<Format>>()

        val urlsFile = File(context.cacheDir, "urls.txt")
        urlsFile.delete()
        withContext(Dispatchers.IO) {
            urlsFile.createNewFile()
        }
        urls.forEach {
            urlsFile.appendText(it+"\n")
        }

        try {
            val request = YoutubeDLRequest(emptyList())
            request.addOption("--print", "formats")
            request.addOption("-a", urlsFile.absolutePath)
            request.applyDefaultOptionsForFetchingData(urls.firstOrNull { it.isURL() })
            if (urls.all { it.isYoutubeURL() }) {
                request.setYoutubeExtractorArgs(urls[0])
            }

            val txt = parseYTDLRequestString(request)
            println(txt)

            var urlIdx = 0
            YoutubeDL.getInstance().execute(request){ progress, _, line ->
                try{
                    if (line.isNotBlank()){
                        val url = urls[urlIdx]
                        println(line)
                        println(url)

                        if (line.contains("unavailable")) {
                            progress(ResultViewModel.MultipleFormatProgress(url, listOf(), true, line))
                        }else{
                            val formatsJSON = JSONArray(line)
                            val formats = parseYTDLFormats(formatsJSON)

                            formatCollection.add(formats)
                            progress(ResultViewModel.MultipleFormatProgress(url, formats))
                        }
                    }

                }catch (e: Exception){
                    Log.e("GET MULTIPLE FORMATS", e.toString())
                }
                urlIdx++
            }
        } catch (e: Exception) {
            e.message?.split(System.lineSeparator())?.apply {
                this.forEach { line ->
                    println(line)
                    if (line.contains("unavailable")) {
                        kotlin.runCatching {
                            val id = Regex("""\[.*?\] (\w+):""").find(line)!!.groupValues[1]
                            val url = urls.first { it.contains(id) }
                            progress(
                                ResultViewModel.MultipleFormatProgress(
                                    url,
                                    listOf(),
                                    true,
                                    line
                                )
                            )
                            delay(500)
                        }
                    }

                }
            }

            handler.post {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            }
            e.printStackTrace()
            return Result.failure(e)
        } finally {
            urlsFile.delete()
        }

        return Result.success(formatCollection)
    }


    fun getFormats(url: String) : List<Format> {
        val request = YoutubeDLRequest(url)
        request.addOption("--print", "%(formats)s")
        request.addOption("--print", "%(duration)s")
        request.applyDefaultOptionsForFetchingData(url)
        if (url.isYoutubeURL()) {
            request.setYoutubeExtractorArgs(url)
        }

        val res = YoutubeDL.getInstance().execute(request)
        val results: Array<String?> = try {
            res.out.split(System.lineSeparator()).toTypedArray()
        } catch (e: Exception) {
            arrayOf(res.out)
        }
        val json = results[0]
        val jsonArray = kotlin.runCatching { JSONArray(json) }.getOrElse { JSONArray() }

        return parseYTDLFormats(jsonArray)
    }

    private fun parseYTDLFormats(formatsInJSON: JSONArray?) : ArrayList<Format> {
        val formats = arrayListOf<Format>()

        if (formatsInJSON != null) {
            for (f in formatsInJSON.length() - 1 downTo 0){
                val format = formatsInJSON.getJSONObject(f)
                kotlin.runCatching {
                    if (format.get("filesize").toString() == "None") {
                        format.remove("filesize")
                    }
                }

                kotlin.runCatching {
                    if (format.get("filesize_approx").toString() == "None") {
                        format.remove("filesize_approx")
                    }
                }

                kotlin.runCatching {
                    if(format.get("format_note").toString() == "null"){
                        format.remove("format_note")
                    }
                }

                val formatProper = Gson().fromJson(format.toString(), Format::class.java)
                if (formatProper.format_note == null) formatProper.format_note = ""

                val resolution = format.getString("resolution")
                if (format.has("format_note")){
                    if (!formatProper!!.format_note.contains("audio only", true)) {
                        formatProper.format_note = format.getString("format_note")
                    }else{
                        if (!formatProper.format_note.endsWith("audio", true)){
                            formatProper.format_note = format.getString("format_note").uppercase().removeSuffix("AUDIO").trim() + " AUDIO"
                        }
                    }

                    if (!resolution.isNullOrBlank() && resolution != "audio only") {
                        formatProper.format_note = "${formatProper.format_note} (${resolution})"
                    }
                }

                if (formatProper.format_note.contains("storyboard", ignoreCase = true)) continue

                formatProper.container = format.getString("ext")
                if (formatProper.tbr == "None") formatProper.tbr = ""
                if (!formatProper.tbr.isNullOrBlank()){
                    formatProper.tbr += "k"
                }

                if(formatProper.vcodec.isNullOrEmpty() || formatProper.vcodec == "null"){
                    if(formatProper.acodec.isNullOrEmpty() || formatProper.acodec == "null"){
                        formatProper.vcodec = format.getStringByAny("video_ext", "ext").ifEmpty { "unknown" }
                    }
                }

                formats.add(formatProper)
            }
        }
        return formats
    }


    fun getStreamingUrlAndChapters(url: String) : Result<Pair<List<String>, List<ChapterItem>?>> {
        try {
            val request = YoutubeDLRequest(url)
            //request.addOption("--get-url")
            request.addOption("--print", "%(.{urls,chapters})s")
            request.addOption("-S", "res:720,+proto:m3u8")
            request.applyDefaultOptionsForFetchingData(url)
            if (url.isYoutubeURL()) {
                request.setYoutubeExtractorArgs(url)
            }

            val youtubeDLResponse = YoutubeDL.getInstance().execute(request)
            val json = JSONObject(youtubeDLResponse.out)
            val urls = if (json.has("urls")) {
                json.getString("urls").split("\n")
            }else{
                listOf()
            }

            val chapters = if (json.has("chapters")) {
                val arr = json.getJSONArray("chapters")
                val list = mutableListOf<ChapterItem>()
                for (i in 0 until arr.length()) {
                    list.add(
                        Gson().fromJson(arr.getJSONObject(i).toString(), ChapterItem::class.java)
                    )
                }

                list
            }else{
                listOf()
            }

            return Result.success(Pair(urls, chapters))

        } catch (e: Exception) {
            return Result.failure(e)
        }
    }


    fun parseYTDLRequestString(request : YoutubeDLRequest) : String {
        val arr = request.buildCommand().toMutableList()
        for (i in arr.indices) {
            if (!arr[i].startsWith("-")) {
                arr[i] = "\"${arr[i]}\""
            }
        }

        var final = java.lang.String.join(" ", arr).replace("\"\"", "\" \"")
        val ppas = "--config(-locations)? \"(.*?)\"".toRegex().findAll(final)
        ppas.forEach {res ->
            val path = "\"(.*?)\"".toRegex().find(res.value)?.value?.replace("\"", "")
            val newVal = runCatching {
                File(path ?: "").readText()
            }.onFailure {
                res.value
            }.getOrDefault("")

            final = final.replace(res.value, newVal)

        }

        return final
    }

    private fun MutableList<String>.addOption(vararg options: String) {
        options.forEach {
            this.add(it)
        }
    }
    @OptIn(ExperimentalStdlibApi::class)
    @SuppressLint("RestrictedApi")
    fun buildYoutubeDLRequest(downloadItem: DownloadItem) : YoutubeDLRequest {
        val useItemURL = sharedPreferences.getBoolean("use_itemurl_instead_playlisturl", false)
        var isPlaylistItem = false

        val request = StringJoiner(" ")

        val ytDlRequest = if (downloadItem.url.endsWith(".txt")) {
            YoutubeDLRequest(listOf()).apply {
                request.addOption("-a", downloadItem.url)
            }
        }else if (downloadItem.playlistURL.isNullOrBlank() || downloadItem.playlistTitle.isBlank() || useItemURL){
            if (downloadItem.url.isBlank()) {
                YoutubeDLRequest(listOf())
            }else{
                YoutubeDLRequest(downloadItem.url)
            }
        }else{
            isPlaylistItem = true
            YoutubeDLRequest(downloadItem.playlistURL!!).apply {
                if(downloadItem.playlistIndex == null){
                    val matchPortion = downloadItem.url.split("/").last().split("=").last().split("&").first()
                    request.addOption("--match-filter", "id~='${matchPortion}'")
                }else{
                    request.addOption("-I", "${downloadItem.playlistIndex!!}:${downloadItem.playlistIndex}")
                }
            }
        }

        request.addOption("--newline")

        val metadataCommands = StringJoiner(" ")

        if (downloadItem.playlistIndex != null && useItemURL) {
            metadataCommands.addOption("--parse-metadata", " ${downloadItem.playlistIndex}: %(playlist_index)s")
        }

        val type = downloadItem.type

        val downDir : File
        val canWrite = File(FileUtil.formatPath(downloadItem.downloadPath)).canWrite()
        val writtenPath = type == DownloadViewModel.Type.command && downloadItem.format.format_note.contains("-P ")

        if (writtenPath || (!sharedPreferences.getBoolean("cache_downloads", true) && canWrite)){
            downDir = File(FileUtil.formatPath(downloadItem.downloadPath))
            request.addOption("--no-quiet")
            request.addOption("--no-simulate")
            request.addOption("--print", "after_move:'%(filepath,_filename)s'")
        }else{
            val cacheDir = FileUtil.getCachePath(context)
            downDir = File(cacheDir, downloadItem.id.toString())
            downDir.delete()
            downDir.mkdirs()
        }

        val aria2 = sharedPreferences.getBoolean("aria2", false)
        if (aria2) {
            request.addOption("--downloader", "libaria2c.so")
            //request.addOption("--external-downloader-args", "aria2c:\"--summary-interval=1\"")
            request.addOption("--external-downloader-args", "aria2c:\"--check-certificate=false\"")
        }

        val concurrentFragments = sharedPreferences.getInt("concurrent_fragments", 1)
        if (concurrentFragments > 1) request.addOption("-N", concurrentFragments)

        val retries = sharedPreferences.getString("retries", "")!!
        val fragmentRetries = sharedPreferences.getString("fragment_retries", "")!!

        if(retries.isNotEmpty()) request.addOption("--retries", retries)
        if(fragmentRetries.isNotEmpty()) request.addOption("--fragment-retries", fragmentRetries)

        val limitRate = sharedPreferences.getString("limit_rate", "")!!
        if (limitRate.isNotBlank()) request.addOption("-r", limitRate)

        val bufferSize = sharedPreferences.getString("buffer_size", "")!!
        if (bufferSize.isNotBlank()){
            request.addOption("--buffer-size", bufferSize)
            request.addOption("--no-resize-buffer")
        }

        val socketTimeout = sharedPreferences.getString("socket_timeout", "")!!
        if (socketTimeout.isNotBlank()) {
            request.addOption("--socket-timeout", socketTimeout)
        }

        val sponsorblockURL = sharedPreferences.getString("sponsorblock_url", "")!!
        if (sponsorblockURL.isNotBlank()) request.addOption("--sponsorblock-api", sponsorblockURL)

        if (sharedPreferences.getBoolean("restrict_filenames", true)) request.addOption("--restrict-filenames")
        if (sharedPreferences.getBoolean("force_ipv4", false)){
            request.addOption("-4")
        }
        if (sharedPreferences.getBoolean("use_cookies", false)){
            FileUtil.getCookieFile(context){
                request.addOption("--cookies", it)
            }
        }

        if (sharedPreferences.getBoolean("no_check_certificates", false)) {
            request.addOption("--no-check-certificates")
        }

        val proxy = sharedPreferences.getString("proxy", "")
        if (proxy!!.isNotBlank()){
            request.addOption("--proxy", proxy)
        }

        val keepCache = sharedPreferences.getBoolean("keep_cache", false)
        if(keepCache){
            request.addOption("--keep-fragments")
        }

        val embedMetadata = sharedPreferences.getBoolean("embed_metadata", true)
        val thumbnailFormat = sharedPreferences.getString("thumbnail_format", "jpg")
        var filenameTemplate = downloadItem.customFileNameTemplate

        if(downloadItem.type != DownloadViewModel.Type.command){
            if (sharedPreferences.getBoolean("no_part", false)){
                request.addOption("--no-part")
            }

            request.addOption("--trim-filenames",  254 - downDir.absolutePath.length)

            if (downloadItem.SaveThumb) {
                request.addOption("--write-thumbnail")
                request.addOption("--convert-thumbnails", thumbnailFormat!!)
            }
            if (!sharedPreferences.getBoolean("mtime", false)){
                request.addOption("--no-mtime")
            }else{
                request.addOption("--mtime")
            }

            val sponsorBlockFilters : ArrayList<String> = when(downloadItem.type) {
                DownloadViewModel.Type.audio -> {
                    downloadItem.audioPreferences.sponsorBlockFilters
                }
                //video
                else -> {
                    downloadItem.videoPreferences.sponsorBlockFilters
                }
            }


            if (sharedPreferences.getBoolean("use_sponsorblock", true)){
                if (sponsorBlockFilters.isNotEmpty()) {
                    val filters = java.lang.String.join(",", sponsorBlockFilters.filter { it.isNotBlank() })
                    if (filters.isNotBlank()) {
                        request.addOption("--sponsorblock-remove", filters)
                        if (sharedPreferences.getBoolean("force_keyframes", false)){
                            request.addOption("--force-keyframes-at-cuts")
                        }
                    }
                }
            }

            if(downloadItem.title.isNotBlank()){
                metadataCommands.addOption("--replace-in-metadata", "title", ".+", downloadItem.title)
                metadataCommands.addOption("--parse-metadata", "%(title)s:%(meta_title)s")
            }


            if (downloadItem.author.isNotBlank()){
                metadataCommands.addOption("--replace-in-metadata", "uploader", ".+", downloadItem.author)
                metadataCommands.addOption("--parse-metadata", "%(uploader)s:%(artist)s")
            }

            if (downloadItem.downloadSections.isNotBlank()){
                downloadItem.downloadSections.split(";").forEach {
                    if (it.isBlank()) return@forEach
                    request.addOption("--download-sections", "*${it.split(" ")[0]}")

                    if (sharedPreferences.getBoolean("force_keyframes", false) && !request.toString().contains("--force-keyframes-at-cuts")){
                        request.addOption("--force-keyframes-at-cuts")
                    }
                }

                if (downloadItem.downloadSections.split(";").size > 1){
                    filenameTemplate = "%(autonumber)d. $filenameTemplate [%(section_start>%H∶%M∶%S)s]"
                }
            }

            if (sharedPreferences.getBoolean("use_audio_quality", false)){
                request.addOption("--audio-quality", sharedPreferences.getInt("audio_quality", 0).toString())
            }

            if (sharedPreferences.getBoolean("write_description", false)){
                request.addOption("--write-description")
            }

            if (downloadItem.url.isYoutubeURL()) {
                ytDlRequest.setYoutubeExtractorArgs(downloadItem.url)
            }

            //TODO REVIEW TO ADD THIS AGAIN LATER?
//            if (!sharedPreferences.getBoolean("disable_write_info_json", false)) {
//                val cachePath = "${FileUtil.getCachePath(context)}infojsons"
//                val infoJsonName = MessageDigest.getInstance("MD5").digest(downloadItem.url.toByteArray()).toHexString()
//
//                val infoJsonFile = File(cachePath).walkTopDown().firstOrNull { it.name == "${infoJsonName}.info.json" }
//                //ignore info file if its older than 5 hours. puny measure to prevent expired formats in some cases
//                if (infoJsonFile == null || System.currentTimeMillis() - infoJsonFile.lastModified() > (1000 * 60 * 60 * 5)) {
//                    request.addCommands(listOf("--print-to-file", "video:%()#j", "${cachePath}/${infoJsonName}.info.json"))
//                }else {
//                    request.addOption("--load-info-json", infoJsonFile.absolutePath)
//                }
//            }
        }

        if (sharedPreferences.getString("prevent_duplicate_downloads", "")!! == "download_archive"){
            request.addOption("--download-archive", FileUtil.getDownloadArchivePath(context))
        }

        val preferredAudioCodec = sharedPreferences.getString("audio_codec", "")!!
        val aCodecPrefIndex = context.getStringArray(R.array.audio_codec_values).indexOf(preferredAudioCodec)
        val aCodecPref = runCatching { context.getStringArray(R.array.audio_codec_values_ytdlp)[aCodecPrefIndex] }.getOrElse { "" }

        when(type){
            DownloadViewModel.Type.audio -> {
                val supportedContainers = context.resources.getStringArray(R.array.audio_containers)
                val preferredLanguage = sharedPreferences.getString("audio_language","")!!
                var abrSort = ""

                var audioQualityId : String = downloadItem.format.format_id
                if (audioQualityId.isBlank() || listOf("0", context.getString(R.string.best_quality), "ba", "best", "", context.getString(R.string.worst_quality), "wa", "worst").contains(audioQualityId)){
                    audioQualityId = "ba/b"
                }else if(audioQualityId.contains("kbps_ytdlnisgeneric")){
                    abrSort = audioQualityId.split("kbps")[0]
                    audioQualityId = ""
                }else{
                    audioQualityId += "/ba/b"
                }

                if ((audioQualityId.isBlank() || audioQualityId == "ba/b") && preferredLanguage.isNotBlank()) {
                    audioQualityId = "ba[language^=$preferredLanguage]/ba/b"
                }

                if (audioQualityId.isNotBlank()) {
                    if (audioQualityId.matches(".*-[0-9]+.*".toRegex())) {
                        audioQualityId = if(!downloadItem.format.lang.isNullOrBlank() && downloadItem.format.lang != "None"){
                            "ba[format_id~='^(${audioQualityId.split("-")[0]})'][language^=${downloadItem.format.lang}]/ba/b"
                        }else{
                            "$audioQualityId/${audioQualityId.split("-")[0]}"
                        }
                    }

                    request.addOption("-f", audioQualityId)
                }

                request.addOption("-x")
                val ext = downloadItem.container

                val formatSorting = mutableListOf<String>()
                val formatImportance = formatUtil.getAudioFormatImportance()
                val useCustomFormatSorting = sharedPreferences.getBoolean("use_format_sorting", false)
                if (useCustomFormatSorting) {
                    formatSorting.add("hasaud")
                }

                for(order in formatImportance) {
                    when(order) {
                        "smallsize" -> {
                            formatSorting.add("+size")
                        }
                        "file_size" -> {
                            formatSorting.add("size")
                        }
//                        "language" -> {
//                            if (preferredLanguage.isNotBlank()) {
//                                formatSorting.add("lang:${preferredLanguage}")
//                            }
//                        }
                        "codec" -> {
                            if (aCodecPref.isNotBlank()){
                                formatSorting.add("acodec:$aCodecPref")
                            }
                        }
                        "container" -> {
                            if(ext.isNotBlank()){
                                if(!ext.matches("(webm)|(Default)|(${context.getString(R.string.defaultValue)})".toRegex()) && supportedContainers.contains(ext)){
                                    request.addOption("--audio-format", ext)
                                    formatSorting.add("aext:$ext")
                                }
                            }
                        }
                    }
                }

                if (downloadItem.format.format_id == context.resources.getString(R.string.worst_quality) || downloadItem.format.format_id == "wa" || downloadItem.format.format_id == "worst") {
                    formatSorting.remove("size")
                    formatSorting.remove("+size")
                    formatSorting.addAll(0,listOf("+br", "+res", "+fps"))
                }

                if (abrSort.isNotBlank()){
                    formatSorting.add(0, "abr:${abrSort}")
                }

                if(formatSorting.isNotEmpty()) {
                    request.addOption("-S", formatSorting.joinToString(","))
                }

                request.addOption("-P", downDir.absolutePath)


                val useArtistTags = if (downloadItem.url.isYoutubeURL()) "artists,artist," else ""
                if (downloadItem.author.isBlank()) {
                    metadataCommands.addOption("--parse-metadata", """%(${useArtistTags}uploader,channel,creator|null)l:^(?P<uploader>.*?)(?:(?= - Topic)|$)""")
                }

                if (downloadItem.audioPreferences.splitByChapters && downloadItem.downloadSections.isBlank()){
                    request.addOption("--split-chapters")
                    request.addOption("-o", "chapter:%(section_title)s.%(ext)s")
                }else{

                    if (embedMetadata){
                        metadataCommands.addOption("--embed-metadata")

                        val emptyAuthor = downloadItem.author.isEmpty()
                        val usePlaylistMetadata = sharedPreferences.getBoolean("playlist_as_album", true)

                        if (emptyAuthor) {
                            if (usePlaylistMetadata) {
                                metadataCommands.addOption("--parse-metadata", "%(playlist_uploader,artist,uploader|)s:^(?P<first_artist>.*?)(?:(?=,\\s+)|$)")
                            }else{
                                metadataCommands.addOption("--parse-metadata", "%(artist,uploader|)s:^(?P<first_artist>.*?)(?:(?=,\\s+)|$)")
                            }
                        }else{
                            if (usePlaylistMetadata) {
                                metadataCommands.addOption("--parse-metadata", "%(playlist_uploader,artist|)s:^(?P<first_artist>.*?)(?:(?=,\\s+)|$)")
                            }else{
                                metadataCommands.addOption("--parse-metadata", "%(artist|)s:^(?P<first_artist>.*?)(?:(?=,\\s+)|$)")
                            }
                        }

                        if (usePlaylistMetadata) {
                            metadataCommands.addOption("--parse-metadata", "%(album,playlist_title,playlist|)s:%(meta_album)s")
                        }


                        metadataCommands.addOption("--parse-metadata", "%(album_artist,first_artist|)s:%(album_artist)s")
                        metadataCommands.addOption("--parse-metadata", "description:(?:.+?Released\\ on\\s*:\\s*(?P<dscrptn_year>\\d{4}))?")
                        metadataCommands.addOption("--parse-metadata", "%(dscrptn_year,release_year,release_date>%Y,upload_date>%Y)s:(?P<meta_date>\\d+)")

                        if (isPlaylistItem) {
                            metadataCommands.addOption("--parse-metadata", "%(track_number,playlist_index)d:(?P<track_number>\\d+)")
                        }
                    }

                    val cropThumb = downloadItem.audioPreferences.cropThumb ?: sharedPreferences.getBoolean("crop_thumbnail", true)
                    if (downloadItem.audioPreferences.embedThumb){
                        metadataCommands.addOption("--embed-thumbnail")
                        if (!request.toString().contains("--convert-thumbnails")) metadataCommands.addOption("--convert-thumbnails", thumbnailFormat!!)

                        val thumbnailConfig = StringBuilder("")
                        val cropConfig = """-vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"""""
                        if (thumbnailFormat == "jpg")  thumbnailConfig.append("""--ppa "ThumbnailsConvertor:-qmin 1 -q:v 1"""")
                        if (cropThumb){
                            if (thumbnailFormat == "jpg") {
                                thumbnailConfig.deleteCharAt(thumbnailConfig.length - 1)
                                thumbnailConfig.append(""" $cropConfig""")
                            }
                            else thumbnailConfig.append("""--ppa "ThumbnailsConvertor:$cropConfig""")
                        }

                        if (thumbnailConfig.isNotBlank()){
                            request.addOption(thumbnailConfig.toString())
                        }

                    }

                    if (filenameTemplate.isNotBlank()){
                        request.addOption("-o", "${filenameTemplate.removeSuffix(".%(ext)s")}.%(ext)s")
                    }
                }

            }
            DownloadViewModel.Type.video -> {
                val useArtistTags = if (downloadItem.url.isYoutubeURL()) "artists,artist," else ""
                if (downloadItem.author.isBlank()) {
                    metadataCommands.addOption("--parse-metadata", """%(${useArtistTags}uploader,channel,creator|null)l:^(?P<uploader>.*?)(?:(?= - Topic)|$)""")
                }

                val supportedContainers = context.resources.getStringArray(R.array.video_containers)

                if (downloadItem.videoPreferences.addChapters) {
                    if (sharedPreferences.getBoolean("use_sponsorblock", true)){
                        request.addOption("--sponsorblock-mark", "all")
                    }
                    request.addOption("--embed-chapters")
                }


                var cont = ""
                val outputContainer = downloadItem.container
                if(
                    outputContainer.isNotEmpty() &&
                    outputContainer != "Default" &&
                    outputContainer != context.getString(R.string.defaultValue) &&
                    supportedContainers.contains(outputContainer)
                ){
                    cont = outputContainer

                    val cantRecode = listOf("avi")
                    if (downloadItem.videoPreferences.recodeVideo && !cantRecode.contains(cont)) {
                        request.addOption("--recode-video", outputContainer.lowercase())
                    }else{
                        request.addOption("--merge-output-format", outputContainer.lowercase())
                    }

                    if (!listOf("webm", "avi", "flv").contains(outputContainer.lowercase())) {
                        val embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false)
                        if (embedThumb) {
                            metadataCommands.addOption("--embed-thumbnail")
                            if (!request.toString().contains("--convert-thumbnails")) request.addOption("--convert-thumbnails", thumbnailFormat!!)
                        }
                    }
                }
                var acont = sharedPreferences.getString("audio_format", "")!!
                if (acont == "Default") acont = ""

                //format logic
                var videoF = downloadItem.format.format_id
                var altAudioF = ""
                var audioF = downloadItem.videoPreferences.audioFormatIDs.map { f ->
                    val format = downloadItem.allFormats.find { it.format_id == f }
                    format?.run {
                        if (this.format_id.matches(".*-[0-9]+".toRegex())) {
                            if (!this.lang.isNullOrBlank() && this.lang != "None") {
                                "ba[format_id~='^(${this.format_id.split("-")[0]})'][language^=${this.lang}]"
                            } else {
                                altAudioF = this.format_id.split("-")[0]
                                this.format_id
                            }
                        } else this.format_id
                    } ?: f
                }.joinToString("+").ifBlank { "ba" }
                val preferredAudioLanguage = sharedPreferences.getString("audio_language", "")!!
                if (downloadItem.videoPreferences.removeAudio) audioF = ""

                var abrSort = ""
                if(audioF.contains("kbps_ytdlnisgeneric")){
                    abrSort = audioF.split("kbps")[0]
                    audioF = ""
                }

                val f = StringBuilder()

                val preferredCodec = sharedPreferences.getString("video_codec", "")
                val preferredQuality = sharedPreferences.getString("video_quality", "best")
                val vCodecPrefIndex = context.getStringArray(R.array.video_codec_values).indexOf(preferredCodec)
                val vCodecPref = context.getStringArray(R.array.video_codec_values_ytdlp)[vCodecPrefIndex]

                val defaultFormats = context.resources.getStringArray(R.array.video_formats_values)
                val usingGenericFormat = defaultFormats.contains(videoF) || downloadItem.allFormats.isEmpty() || downloadItem.allFormats == formatUtil.getGenericVideoFormats(context.resources)
                var hasGenericResulutionFormat = ""
                if (!usingGenericFormat){
                    // with audio removed
                    if (audioF.isBlank()){
                        f.append("$videoF/bv/b")
                    }else{
                        //with audio
                        f.append("$videoF+$audioF/")
                        if (altAudioF.isNotBlank()){
                            f.append("$videoF+$altAudioF/")
                        }
                        if (!f.contains("$videoF+ba")) {
                            if (preferredAudioLanguage.isNotEmpty()) {
                                f.append("$videoF+ba[language^=$preferredAudioLanguage]/")
                            }else {
                                f.append("$videoF+ba/")
                            }
                        }
                        f.append("$videoF/b")


                        if (audioF.count("+") > 0){
                            request.addOption("--audio-multistreams")
                        }
                    }
                }else{
                    if (videoF == context.resources.getString(R.string.best_quality) || videoF == "best" || videoF == context.resources.getString(R.string.worst_quality) || videoF == "worst") {
                        videoF = "bv"
                    }else if (defaultFormats.contains(videoF)) {
                        hasGenericResulutionFormat = videoF.split("_")[0].dropLast(1)
                        videoF = "bv"
                    }

                    val preferredFormatIDs = sharedPreferences.getString("format_id", "")
                        .toString()
                        .split(",")
                        .filter { it.isNotEmpty() }
                        .ifEmpty { listOf(videoF) }.toMutableList()
                    if (!preferredFormatIDs.contains(videoF)){
                        preferredFormatIDs.add(0, videoF)
                    }
                    // ^ [videoF, preferredID1, preferredID2...]

                    val preferredAudioFormatIDs = sharedPreferences.getString("format_id_audio", "")
                        .toString()
                        .split(",")
                        .filter { it.isNotBlank() }
                        .ifEmpty {
                            val list = mutableListOf<String>()
                            if (preferredAudioLanguage.isNotEmpty() && !downloadItem.videoPreferences.removeAudio) list.add("ba[language^=$preferredAudioLanguage]")
                            list.add(audioF)
                            list
                        }.apply {

                        }.toMutableList()
                    if (!preferredAudioFormatIDs.contains(audioF) && audioF != "ba"){
                        preferredAudioFormatIDs.add(0, audioF)
                    }
                    // ^ [audioF, preferredAID1, preferredAID2, ....]
                    if (preferredAudioFormatIDs.any{it.contains("+")}){
                        request.addOption("--audio-multistreams")
                    }


                    preferredFormatIDs.forEach { v ->
                        preferredAudioFormatIDs.filter { it.isNotBlank() }.forEach { a ->
                            f.append("$v+$a/")
                        }
                        //build format with just videoformatid and audio if remove audio is not checked
                        if (!downloadItem.videoPreferences.removeAudio){
                            //build format with audio with preferred language
                            if (preferredAudioLanguage.isNotBlank()){
                                val al = if (v == "wv"){
                                    "$v+wa[language^=$preferredAudioLanguage]/"
                                }else{
                                    "$v+ba[language^=$preferredAudioLanguage]/"
                                }
                                if (!f.contains(al)) f.append(al)
                            }
                            //build format with best audio
                            if (!f.contains("$v+ba/") && !f.contains("wa")) f.append("$v+ba/")
                            //build format with standalone video
                            if (v == "wv"){
                                f.append("w/")
                            }else{
                                if (v != "bv") f.append("$v/")
                            }
                        }
                    }

                    if(!f.endsWith("b/")){
                        //last fallback
                        f.append("b")
                    }

                }

                val formatImportance = formatUtil.getVideoFormatImportance().toMutableList()
                val formatSorting = mutableListOf<String>()

                for(order in formatImportance) {
                    when(order) {
                        "smallsize" -> {
                            formatSorting.add("+size")
                        }
                        "filesize" -> {
                            formatSorting.add("size")
                        }
                        "no_audio" -> {
                            formatSorting.add("+hasaud")
                        }
                        "codec" -> {
                            if (vCodecPref.isNotBlank()) formatSorting.add("vcodec:$vCodecPref")
                            if (aCodecPref.isNotBlank()) formatSorting.add("acodec:$aCodecPref")
                        }
                        "resolution" -> {
                            if (hasGenericResulutionFormat.isNotBlank()) {
                                formatSorting.add("res:${hasGenericResulutionFormat}")
                            }
                        }
                        "container" -> {
                            if (cont.isNotBlank()) formatSorting.add("vext:$cont")
                            if (acont.isNotBlank()) formatSorting.add("aext:$acont")
                        }
                    }
                }

                if (downloadItem.format.format_id == context.resources.getString(R.string.worst_quality) || downloadItem.format.format_id == "worst") {
                    formatSorting.remove("+size")
                    formatSorting.remove("size")
                    formatSorting.addAll(0, listOf("+br","+res","+fps"))
                }

                if (abrSort.isNotBlank()) {
                    formatSorting.add("abr:${abrSort}")
                }

//                if (preferredLanguage.isNotBlank()) {
//                    formatSorting.add("lang:${preferredLanguage}")
//                }

                if (formatSorting.isNotEmpty()) {
                    request.addOption("-S", formatSorting.joinToString(","))
                }

                request.addOption("-f", f.toString().replace("/$".toRegex(), ""))

                if (downloadItem.videoPreferences.writeSubs){
                    request.addOption("--write-subs")
                }

                if(downloadItem.videoPreferences.writeAutoSubs){
                    request.addOption("--write-auto-subs")
                }

                if (downloadItem.videoPreferences.embedSubs) {
                    if (sharedPreferences.getBoolean("write_subs_when_embed_subs", false) && !downloadItem.videoPreferences.writeSubs && !downloadItem.videoPreferences.writeAutoSubs) {
                        request.addOption("--write-subs")
                        request.addOption("--write-auto-subs")
                        request.addOption("--compat-options", "no-keep-subs")
                    }

                    request.addOption("--embed-subs")
                }

                if (downloadItem.videoPreferences.embedSubs || downloadItem.videoPreferences.writeSubs || downloadItem.videoPreferences.writeAutoSubs){
                    val subFormat = sharedPreferences.getString("sub_format", "srt")
                    if(subFormat!!.isNotBlank()){
                        request.addOption("--sub-format", "${subFormat}/best")
                        request.addOption("--convert-subtitles", subFormat)
                    }
                    request.addOption("--sub-langs", downloadItem.videoPreferences.subsLanguages.ifEmpty { "en.*,.*-orig" })
                }



                if (downloadItem.videoPreferences.removeAudio){
                    request.addOption("--use-postprocessor", "FFmpegCopyStream")
                    request.addOption("--ppa", "CopyStream:-c copy -an")

                }

                request.addOption("-P", downDir.absolutePath)

                if (downloadItem.videoPreferences.splitByChapters  && downloadItem.downloadSections.isBlank()){
                    request.addOption("--split-chapters")
                    request.addOption("-o", "chapter:%(section_number)d - %(section_title)s.%(ext)s")
                }else{
                    if (filenameTemplate.isNotBlank()){
                        request.addOption("-o", "${filenameTemplate.removeSuffix(".%(ext)s")}.%(ext)s")
                    }
                }

                if (downloadItem.videoPreferences.liveFromStart) {
                    request.addOption("--live-from-start")
                }

                downloadItem.videoPreferences.waitForVideoMinutes.apply {
                    if (this > 0) {
                        request.addOption("--wait-for-video", this * 60)
                    }
                }

            }
            DownloadViewModel.Type.command -> {
                if (!writtenPath) {
                    request.addOption("-P", downDir.absolutePath)
                }

                request.addOption(downloadItem.format.format_note)
            }

            else -> {}
        }

        request.merge(metadataCommands)

        if (downloadItem.extraCommands.isNotBlank() && downloadItem.type != DownloadViewModel.Type.command){
            // check for cache dir as extra command and add it as an actual option to prevent --no-cache-dir in youtubedl_android
            val cacheDirArg = """(--cache-dir (".*"))""".toRegex().find(downloadItem.extraCommands)
            if (cacheDirArg != null) {
                ytDlRequest.addOption("--cache-dir", cacheDirArg.groupValues.last().replace("\"", ""))
                downloadItem.extraCommands.replace(cacheDirArg.value, "")
            }
            request.addOption(downloadItem.extraCommands)
        }

        val cache = File(FileUtil.getCachePath(context))
        cache.mkdirs()
        val conf = File(cache.absolutePath + "/${System.currentTimeMillis()}${UUID.randomUUID()}.txt")
        conf.createNewFile()
        conf.writeText(request.toString())
        val tmp = mutableListOf<String>()
        tmp.addOption("--config-locations", conf.absolutePath)
        ytDlRequest.addCommands(tmp)
        return ytDlRequest
    }

    fun getVersion(context: Context, channel: String) : String {
        if (listOf("stable", "nightly", "master").contains(channel)) {
            return YoutubeDL.version(context) ?: ""
        }

        val req = YoutubeDLRequest(emptyList())
        req.addOption("--version")
        return YoutubeDL.getInstance().execute(req).out.trim()
    }

    private fun YoutubeDLRequest.setYoutubeExtractorArgs(url: String?) {
        val extractorArgs = mutableListOf<String>()
        val playerClients = mutableSetOf<String>()
        val poTokens = mutableListOf<String>()

        val configuredPlayerClientsRaw = sharedPreferences.getString("youtube_player_clients", "[]")!!.ifEmpty { "[]" }
        kotlin.runCatching {
            val configuredPlayerClients = Gson().fromJson(configuredPlayerClientsRaw, Array<YoutubePlayerClientItem>::class.java).toMutableList()

            for (value in configuredPlayerClients) {
                if (value.enabled) {
                    if (!value.useOnlyPoToken) {
                        playerClients.add(value.playerClient)
                    }

                    var canUsePoToken = true
                    if (value.urlRegex.isNotEmpty() && url != null) {
                        canUsePoToken = value.urlRegex.any { url.matches(it.toRegex()) }
                    }

                    if (canUsePoToken) {
                        value.poTokens.forEach { pt ->
                            poTokens.add("${value.playerClient}.${pt.context}+${pt.token}")
                        }
                    }
                }
            }
        }

        val dataSyncID = sharedPreferences.getString("youtube_data_sync_id", "")!!
        if (dataSyncID.isNotBlank()) {
            extractorArgs.add("player_skip=webpage,configs")
            extractorArgs.add("data_sync_id=${dataSyncID}")
        }

        val generatedPoTokensRaw = sharedPreferences.getString("youtube_generated_po_tokens", "[]")!!.ifEmpty { "[]" }
        kotlin.runCatching {
            val generatedPoTokens = Gson().fromJson(generatedPoTokensRaw,Array<YoutubeGeneratePoTokenItem>::class.java).toMutableList()
            if (generatedPoTokens.isNotEmpty()) {
                for (value in generatedPoTokens) {
                    if (value.enabled) {
                        for (cl in value.clients) {
                            playerClients.add(cl)
                            for (pt in value.poTokens) {
                                if (pt.token.isNotBlank()) {
                                    poTokens.add("${cl}.${pt.context}+${pt.token}")
                                }
                            }
                        }

                        if (dataSyncID.isBlank() && value.useVisitorData) {
                            extractorArgs.add("player_skip=webpage,configs")
                            extractorArgs.add("visitor_data=${value.visitorData}")
                        }

                    }
                }
            }
        }

        if (playerClients.isNotEmpty()){
            extractorArgs.add("player_client=${playerClients.joinToString(",")}")
        }

        if (poTokens.isNotEmpty()) {
            extractorArgs.add("po_token=${poTokens.joinToString(",")}")
        }

        val useLanguageForMetadata = sharedPreferences.getBoolean("use_app_language_for_metadata", true)
        if (useLanguageForMetadata) {
            val lang = Locale.getDefault().language
            val langTag = Locale.getDefault().toLanguageTag()
            if (context.getStringArray(R.array.subtitle_langs).contains(lang)) {
                extractorArgs.add("lang=$lang")
            }else if (context.getStringArray(R.array.subtitle_langs).contains(langTag)) {
                extractorArgs.add("lang=$langTag")
            }
        }

        val otherArgs = sharedPreferences.getString("youtube_other_extractor_args", "")!!
        if (otherArgs.isNotBlank()) {
            extractorArgs.add(otherArgs)
        }

        val extArgs = extractorArgs.joinToString(";")
        if (extractorArgs.isNotEmpty()) {
            this.addOption("--extractor-args", "youtube:${extArgs}")
        }
    }

    private fun YoutubeDLRequest.addConfig(commandString: String) {
        this.addOption(
            "--config-locations",
            File(context.cacheDir.absolutePath + "/${System.currentTimeMillis()}${UUID.randomUUID()}.txt").apply {
                writeText(commandString)
            }.absolutePath
        )
    }

    private fun StringJoiner.addOption(vararg elements: Any) {
        this.add(elements.first().toString())
        if (elements.size > 1) {
            for (el in elements.drop(1)) {
                val arg = el.toString()
                    //.replace("\\", "\\\\")
                    .replace("\"", "\\\"")

                this.add("\"$arg\"")
            }
        }
    }
}