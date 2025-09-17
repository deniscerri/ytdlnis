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
import com.deniscerri.ytdl.util.Extensions.getIDFromYoutubeURL
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
import java.util.Locale
import java.util.StringJoiner
import java.util.UUID

class YTDLPUtil(private val context: Context, private val commandTemplateDao: CommandTemplateDao) {
    private var sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val formatUtil = FormatUtil(context)
    private val handler = Handler(Looper.getMainLooper())

    private fun YoutubeDLRequest.applyDefaultOptionsForFetchingData(url: String?) {
        addOption("--skip-download")
        addOption("--quiet")
        addOption("--ignore-errors")
        addOption("--no-warnings")
        addOption("-R", "1")
        addOption("--compat-options", "manifest-filesize-approx")
        val socketTimeout = sharedPreferences.getString("socket_timeout", "5")!!.ifEmpty { "5" }
        addOption("--socket-timeout", socketTimeout)

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

        val proxy = sharedPreferences.getString("proxy", "")
        if (proxy!!.isNotBlank()){
            addOption("--proxy", proxy)
        }
        addOption("-P", FileUtil.getCachePath(context) + "/tmp")

        if (sharedPreferences.getBoolean("no_check_certificates", false)) {
            addOption("--no-check-certificates")
        }

        var extraCommands = commandTemplateDao.getAllTemplatesAsDataFetchingExtraCommands()

        extraCommands = if (url != null) {
            extraCommands.filter {
                it.urlRegex.any { u -> Regex(u).containsMatchIn(url) } ||
                it.urlRegex.isEmpty()
            }
        }else{
            extraCommands.filter { it.urlRegex.isEmpty() }
        }

        if (extraCommands.isNotEmpty()){
            //addCommands(extraCommands.split(" ", "\t", "\n"))
            addConfig(extraCommands.joinToString(" ") { it.content })
        }
    }

    @SuppressLint("RestrictedApi")
    fun getFromYTDL(query: String, singleItem: Boolean = false): ArrayList<ResultItem> {
        val searchEngine = sharedPreferences.getString("search_engine", "ytsearch")

        val request : YoutubeDLRequest
        if (query.contains("http")){
            if (query.isYoutubeWatchVideosURL()) {
                request = YoutubeDLRequest(emptyList())
                val config = File(context.cacheDir.absolutePath + "/config" + System.currentTimeMillis() + "##url.txt")
                config.writeText(query)
                request.addOption("--config", config.absolutePath)
            }else{
                request = YoutubeDLRequest(query)
            }
        }else{
            request = YoutubeDLRequest(emptyList())
            when (searchEngine){
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

    private fun parseYTDLPListResults(results: List<String?>, query: String = "") : ArrayList<ResultItem> {
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
                    jsonObject.getStringByAny("webpage_url", "original_url", "url")
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

            val availableSubtitles = mutableListOf<String>()
            if (jsonObject.has("subtitles")) {
                availableSubtitles.addAll(jsonObject.getJSONObject("subtitles").keys().asSequence().toList())
            }

            val res = ResultItem(0,
                url = url,
                title = title,
                author = author,
                duration = duration,
                thumb = thumb!!,
                website = website,
                playlistTitle = playlistTitle,
                formats = formats,
                urls = urls,
                chapters = chapters,
                playlistURL = playlistURL,
                playlistIndex = playlistIndex,
                availableSubtitles = availableSubtitles
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
            if (!request.hasOption("--no-check-certificates")) request.addOption("--no-check-certificates")

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
            e.message?.split(System.lineSeparator())?.onEach { line ->
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
        if (!request.hasOption("--no-check-certificates")) request.addOption("--no-check-certificates")

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
                request.addOption("--extractor-args", "youtube:player_skip=webpage,configs,js;player_client=android,web")
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

    @SuppressLint("RestrictedApi")
    fun buildYoutubeDLRequest(downloadItem: DownloadItem) : YoutubeDLRequest {
        var useItemURL = sharedPreferences.getBoolean("use_itemurl_instead_playlisturl", false)
        // for /releases youtube channel playlists that have playlists inside of them, cant use indexing or match filter id, so download on its own
        if (downloadItem.url.isYoutubeURL() && downloadItem.url.getIDFromYoutubeURL() == null) {
            useItemURL = true
        }

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
                if(downloadItem.playlistIndex == null || downloadItem.url.isYoutubeURL()){
                    request.addOption("--match-filter", "id~='${downloadItem.url.getIDFromYoutubeURL()}'")
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

        downloadItem.rowNumber.apply {
            if (this > 0) {
                request.addOption("--autonumber-start", this.toString())
            }
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
                metadataCommands.addOption("--replace-in-metadata", "title", """^.*$""", downloadItem.title.replace("""\""", """\\"""))
                metadataCommands.addOption("--parse-metadata", "%(title)s:%(meta_title)s")
            }


            if (downloadItem.author.isNotBlank()){
                metadataCommands.addOption("--replace-in-metadata", "uploader", """^.*$""", downloadItem.author.replace("""\""", """\\"""))
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
        var aCodecPref = runCatching { context.getStringArray(R.array.audio_codec_values_ytdlp)[aCodecPrefIndex] }.getOrElse { "" }

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

                if (downloadItem.downloadSections.isNotBlank()) {
                    formatSorting.add(0, "proto:https")
                }

                if(formatSorting.isNotEmpty()) {
                    request.addOption("-S", formatSorting.joinToString(","))
                }

                request.addOption("-P", downDir.absolutePath)


                val useArtistTags = if (downloadItem.url.isYoutubeURL()) "artists,artist," else ""
                if (downloadItem.author.isBlank()) {
                    metadataCommands.addOption("--parse-metadata", """%(${useArtistTags}uploader,channel,creator|)l:^(?P<uploader>.*?)(?:(?= - Topic)|$)""")
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

                if (downloadItem.audioPreferences.bitrate.isNotBlank()) {
                    request.addOption("--audio-quality", downloadItem.audioPreferences.bitrate)
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
                if(outputContainer.isNotEmpty() && outputContainer != "Default" && outputContainer != context.getString(R.string.defaultValue) && supportedContainers.contains(outputContainer)){
                    cont = outputContainer

                    val cantRecode = listOf("avi")
                    if (downloadItem.videoPreferences.recodeVideo && !cantRecode.contains(cont)) {
                        request.addOption("--recode-video", outputContainer.lowercase())
                    }else{
                        if (downloadItem.videoPreferences.compatibilityMode) {
                            request.addOption("--recode-video", "mp4")
                            request.addOption("--merge-output-format", "mp4/mkv")
                        }
                        else {
                            request.addOption("--merge-output-format", outputContainer.lowercase())
                        }
                    }

                    if (!listOf("webm", "avi", "flv", "gif").contains(outputContainer.lowercase())) {
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
                var audioF = downloadItem.videoPreferences.audioFormatIDs.joinToString("+") { f ->
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
                }.ifBlank { "ba" }
                val preferredAudioLanguage = sharedPreferences.getString("audio_language", "")!!
                if (downloadItem.videoPreferences.removeAudio) audioF = ""

                var abrSort = ""
                if(audioF.contains("kbps_ytdlnisgeneric")){
                    abrSort = audioF.split("kbps")[0]
                    audioF = ""
                }

                val f = StringBuilder()

                val preferredCodec = sharedPreferences.getString("video_codec", "")
                val vCodecPrefIndex = context.getStringArray(R.array.video_codec_values).indexOf(preferredCodec)
                var vCodecPref = context.getStringArray(R.array.video_codec_values_ytdlp)[vCodecPrefIndex]

                if (downloadItem.videoPreferences.compatibilityMode) {
                    vCodecPref = "h264"
                    aCodecPref = "aac"
                }

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

                    val preferredFormatIDs = sharedPreferences.getString("format_id", "").toString()
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
                        if (f.isBlank()) {
                            f.append("bv/")
                        }

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
                        "file_size" -> {
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

                if (downloadItem.downloadSections.isNotBlank()) {
                    formatSorting.add(0, "proto:https")
                }

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
                    if (sharedPreferences.getBoolean("no_keep_subs", false) && !downloadItem.videoPreferences.writeSubs && !downloadItem.videoPreferences.writeAutoSubs) {
                        request.addOption("--compat-options", "no-keep-subs")
                    }

                    request.addOption("--embed-subs")
                }

                if (downloadItem.videoPreferences.embedSubs || downloadItem.videoPreferences.writeSubs || downloadItem.videoPreferences.writeAutoSubs){
                    val subFormat = sharedPreferences.getString("sub_format", "")
                    if(subFormat!!.isNotBlank()){
                        request.addOption("--sub-format", "${subFormat}/best")
                        request.addOption("--convert-subtitles", subFormat)
                    }
                    request.addOption("--sub-langs", downloadItem.videoPreferences.subsLanguages.ifEmpty { "en.*,.*-orig" })
                }

                if (downloadItem.videoPreferences.removeAudio && outputContainer != "gif") {
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
                kotlin.runCatching {
                    downloadItem.extraCommands = downloadItem.extraCommands.replace(cacheDirArg.value, "")
                }
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
}