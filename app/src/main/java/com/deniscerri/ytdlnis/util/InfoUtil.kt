package com.deniscerri.ytdlnis.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Looper
import android.text.Html
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.anggrayudi.storage.extension.count
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.ChapterItem
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.util.Extensions.getIntByAny
import com.deniscerri.ytdlnis.util.Extensions.getStringByAny
import com.deniscerri.ytdlnis.util.Extensions.toStringDuration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.regex.Pattern


class InfoUtil(private val context: Context) {
    private lateinit var sharedPreferences: SharedPreferences

    init {
        try {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            countryCODE = sharedPreferences.getString("locale", "")!!
            if (countryCODE.isEmpty()) countryCODE = "US"
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    fun search(query: String): ArrayList<ResultItem?> {
        return runCatching {
            when(sharedPreferences.getString("search_engine", "ytsearch")){
                "ytsearch" -> searchFromPiped(query)
                "ytsearchmusic" -> searchFromPipedMusic(query)
                else -> throw Exception()
            }
        }.getOrElse {
            getFromYTDL(query)
        }
    }

    @Throws(JSONException::class)
    fun searchFromPiped(query: String): ArrayList<ResultItem?> {
        val items = arrayListOf<ResultItem?>()
        val data = genericRequest("$pipedURL/search?q=$query&filter=videos&region=${countryCODE}")
        val dataArray = data.getJSONArray("items")
        if (dataArray.length() == 0) return getFromYTDL(query)
        for (i in 0 until dataArray.length()) {
            val element = dataArray.getJSONObject(i)
            if (element.getInt("duration") == -1) continue
            element.put("uploader", element.getString("uploaderName"))
            val v = createVideoFromPipedJSON(element, "https://youtube.com" + element.getString("url"))
            if (v == null || v.thumb.isEmpty()) {
                continue
            }
            items.add(v)
        }
        return items
    }

    @Throws(JSONException::class)
    fun searchFromPipedMusic(query: String): ArrayList<ResultItem?> {
        val items = arrayListOf<ResultItem?>()
        val data = genericRequest("$pipedURL/search?q=$query=&filter=music_songs&region=${countryCODE}")
        val dataArray = data.getJSONArray("items")
        if (dataArray.length() == 0) return getFromYTDL(query)
        for (i in 0 until dataArray.length()) {
            val element = dataArray.getJSONObject(i)
            if (element.getInt("duration") == -1) continue
            element.put("uploader", element.getString("uploaderName"))
            val v = createVideoFromPipedJSON(element, "https://youtube.com" + element.getString("url"))
            if (v == null || v.thumb.isEmpty()) {
                continue
            }
            items.add(v)
        }
        return items
    }

    @Throws(JSONException::class)
    fun getPlaylist(id: String, nextPageToken: String, playlistName: String): PlaylistTuple {
        try{
            val items = arrayListOf<ResultItem?>()
            // -------------- PIPED API FUNCTION -------------------
            var url = ""
            url = if (nextPageToken.isBlank()) "$pipedURL/playlists/$id"
            else """$pipedURL/nextpage/playlists/$id?nextpage=${nextPageToken.replace("&prettyPrint", "%26prettyPrint")}"""

            val res = genericRequest(url)
            if (!res.has("relatedStreams")) throw Exception()

            val dataArray = res.getJSONArray("relatedStreams")
            var nextpage = res.getString("nextpage")
            for (i in 0 until dataArray.length()){
                kotlin.runCatching {
                    val obj = dataArray.getJSONObject(i)
                    val itm = createVideoFromPipedJSON(obj, "https://youtube.com" + obj.getString("url"))
                    itm?.playlistTitle = runCatching { res.getString("name") }.getOrElse { playlistName }
                    itm?.playlistURL = "https://www.youtube.com/playlist?list=$id"
                    items.add(itm)
                }
            }
            if (nextpage == "null") nextpage = ""
            return PlaylistTuple(nextpage, items)
        }catch (e: Exception){
            return PlaylistTuple(
                "",
                getFromYTDL("https://www.youtube.com/playlist?list=$id")
            )
        }
    }

    @Throws(JSONException::class)
    fun getYoutubeVideo(url: String): List<ResultItem?> {
        val theURL = url.replace("\\?list.*".toRegex(), "")
        return try {
            val id = getIDFromYoutubeURL(theURL)
            val res = genericRequest("$pipedURL/streams/$id")
            if (res.length() == 0) getFromYTDL(theURL) else listOf(createVideoFromPipedJSON(res, theURL))
        }catch (e: Exception){
            val v = getFromYTDL(theURL)
            v.forEach { it!!.url = theURL }
            v
        }
    }

    private fun createVideofromJSON(obj: JSONObject): ResultItem? {
        var video: ResultItem? = null
        try {
            val id = obj.getString("videoID")
            val title = obj.getString("title").toString()
            val author = obj.getString("channelTitle").toString()
            val duration = obj.getString("duration")
            val thumb = obj.getString("thumb")
            val url = "https://www.youtube.com/watch?v=$id"
            video = ResultItem(0,
                    url,
                    title,
                    author,
                    duration,
                    thumb,
                    "youtube",
                    "",
                    ArrayList(),
                "",
                ArrayList()
                )
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
        return video
    }
    private fun createVideoFromPipedJSON(obj: JSONObject, url: String): ResultItem? {
        var video: ResultItem? = null
        try {
            val id = getIDFromYoutubeURL(url)
            val title = Html.fromHtml(obj.getString("title").toString()).toString()
            val author = try {
                 Html.fromHtml(obj.getString("uploader").toString()).toString()
            }catch (e: Exception){
                Html.fromHtml(obj.getString("uploaderName").toString()).toString()
            }

            val duration = obj.getInt("duration").toStringDuration(Locale.US)
            val thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            val formats : ArrayList<Format> = ArrayList()

            if(sharedPreferences.getString("formats_source", "yt-dlp") == "piped"){
                if (obj.has("audioStreams")){
                    val formatsInJSON = obj.getJSONArray("audioStreams")
                    for (f in 0 until formatsInJSON.length()){
                        val format = formatsInJSON.getJSONObject(f)
                        if (format.getInt("bitrate") == 0) continue
                        val formatObj = Gson().fromJson(format.toString(), Format::class.java)
                        try{
                            formatObj.acodec = format.getString("codec")
                            formatObj.asr = format.getString("quality")
                            if (! format.getString("audioTrackName").equals("null", ignoreCase = true)){
                                formatObj.format_note = format.getString("audioTrackName") + " Audio, " + formatObj.format_note
                            }else{
                                formatObj.format_note = formatObj.format_note + " Audio"
                            }
                            if (!formatObj.tbr.isNullOrBlank()){
                                formatObj.tbr = (formatObj.tbr!!.toInt() / 1000).toString() + "k"
                            }

                        }catch (e: Exception) {
                            e.printStackTrace()
                        }
                        formats.add(formatObj)
                    }

                }

                if (obj.has("videoStreams")){
                    val formatsInJSON = obj.getJSONArray("videoStreams")
                    for (f in 0 until formatsInJSON.length()){
                        val format = formatsInJSON.getJSONObject(f)
                        if (format.getInt("bitrate") == 0) continue
                        val formatObj = Gson().fromJson(format.toString(), Format::class.java)
                        try{
                            formatObj.vcodec = format.getString("codec")
                            if (!formatObj.tbr.isNullOrBlank()){
                                formatObj.tbr = (formatObj.tbr!!.toInt() / 1000).toString() + "k"
                            }
                        }catch (e: Exception) {
                            e.printStackTrace()
                        }
                        formats.add(formatObj)
                    }

                }
                formats.groupBy { it.format_id }.forEach {
                    if (it.value.count() > 1) {
                        it.value.filter { f-> !f.format_note.contains("original", true) }.forEachIndexed { index, format -> format.format_id = format.format_id.split("-")[0] + "-${index}" }
                        val engDefault = it.value.find { f -> f.format_note.contains("original", true) }
                        engDefault?.format_id = (engDefault?.format_id?.split("-")?.get(0) ?: "") + "-${it.value.size-1}"
                    }
                }
                formats.sortByDescending { it.filesize }
            }

            val chapters = ArrayList<ChapterItem>()
            if (obj.has("chapters") && obj.getJSONArray("chapters").length() > 0){
                val chaptersJArray = obj.getJSONArray("chapters")
                for (c in 0 until chaptersJArray.length()){
                    val chapter = chaptersJArray.getJSONObject(c)
                    val end = if (c == chaptersJArray.length() - 1) obj.getInt("duration") else chaptersJArray.getJSONObject(c+1).getInt("start")
                    val item = ChapterItem(chapter.getInt("start").toLong(), end.toLong(), chapter.getString("title"))
                    chapters.add(item)
                }
            }

            video = ResultItem(0,
                url,
                title,
                author,
                duration,
                thumb,
                "youtube",
                "",
                formats,
                if (obj.has("hls") && obj.getString("hls") != "null") obj.getString("hls") else "",
                chapters
            )
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
        return video
    }

    private fun genericRequest(url: String): JSONObject {
        Log.e(TAG, url)
        val reader: BufferedReader
        var line: String?
        val responseContent = StringBuilder()
        val conn: HttpURLConnection
        var json = JSONObject()
        try {
            val req = URL(url)
            conn = req.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 5000
            if (conn.responseCode < 300) {
                reader = BufferedReader(InputStreamReader(conn.inputStream))
                while (reader.readLine().also { line = it } != null) {
                    responseContent.append(line)
                }
                reader.close()
                json = JSONObject(responseContent.toString())
                if (json.has("error")) {
                    throw Exception()
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
        return json
    }

    private fun genericArrayRequest(url: String): JSONArray {
        Log.e(TAG, url)
        val reader: BufferedReader
        var line: String?
        val responseContent = StringBuilder()
        val conn: HttpURLConnection
        var json = JSONArray()
        try {
            val req = URL(url)
            conn = req.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 5000
            if (conn.responseCode < 300) {
                reader = BufferedReader(InputStreamReader(conn.inputStream))
                while (reader.readLine().also { line = it } != null) {
                    responseContent.append(line)
                }
                reader.close()
                json = JSONArray(responseContent.toString())
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
        return json
    }

    private fun fixThumbnail(o: JSONObject): JSONObject {
        var imageURL = ""
        try {
            val thumbs = o.getJSONObject("thumbnails")
            imageURL = thumbs.getJSONObject("maxres").getString("url")
        } catch (e: Exception) {
            try {
                val thumbs = o.getJSONObject("thumbnails")
                imageURL = thumbs.getJSONObject("high").getString("url")
            } catch (u: Exception) {
                try {
                    val thumbs = o.getJSONObject("thumbnails")
                    imageURL = thumbs.getJSONObject("default").getString("url")
                } catch (ignored: Exception) {
                }
            }
        }
        try {
            o.put("thumb", imageURL)
        } catch (ignored: Exception) {
        }
        return o
    }

    @Throws(JSONException::class)
    fun getTrending(): ArrayList<ResultItem?> {
        if (sharedPreferences.getString("api_key", "")!!.isNotBlank()){
            return getTrendingFromYoutubeAPI()
        }
        return getTrendingFromPiped()
    }

    @Throws(JSONException::class)
    fun getTrendingFromYoutubeAPI(): ArrayList<ResultItem?> {
        val items = arrayListOf<ResultItem?>()
        val key = sharedPreferences.getString("api_key", "")!!
        val url = "https://www.googleapis.com/youtube/v3/videos?part=snippet&chart=mostPopular&videoCategoryId=10&regionCode=$countryCODE&maxResults=25&key=$key"
        //short data
        val res = genericRequest(url)
        //extra data from the same videos
        val contentDetails =
            genericRequest("https://www.googleapis.com/youtube/v3/videos?part=contentDetails&chart=mostPopular&videoCategoryId=10&regionCode=$countryCODE&maxResults=25&key=$key")
        if (!contentDetails.has("items")) return ArrayList()
        val dataArray = res.getJSONArray("items")
        val extraDataArray = contentDetails.getJSONArray("items")
        for (i in 0 until dataArray.length()) {
            val element = dataArray.getJSONObject(i)
            val snippet = element.getJSONObject("snippet")
            var duration = extraDataArray.getJSONObject(i).getJSONObject("contentDetails")
                .getString("duration")
            duration = formatDuration(duration)
            snippet.put("videoID", element.getString("id"))
            snippet.put("duration", duration)
            fixThumbnail(snippet)
            val v = createVideofromJSON(snippet)
            if (v == null || v.thumb.isEmpty()) {
                continue
            }
            items.add(v)
        }
        return items
    }

    private fun getTrendingFromPiped(): ArrayList<ResultItem?> {
        val items = arrayListOf<ResultItem?>()
        val url = "$pipedURL/trending?region=$countryCODE"
        val res = genericArrayRequest(url)
        for (i in 0 until res.length()) {
            val element = res.getJSONObject(i)
            if (element.getInt("duration") < 0) continue
            element.put("uploader", element.getString("uploaderName"))
            val v = createVideoFromPipedJSON(element, "https://youtube.com" + element.getString("url"))
            if (v == null || v.thumb.isEmpty()) continue
            items.add(v)
        }
        return items
    }

    private fun getIDFromYoutubeURL(inputQuery: String) : String {
        var el: Array<String?> =
            inputQuery.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        var query = el[el.size - 1]
        if (query!!.contains("watch?v=")) {
            query = query.substring(8)
        }
        el = query.split("&".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        query = el[0]
        el = query!!.split("\\?".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        query = el[0]
        return query!!
    }

    fun getFormats(url: String) : List<Format> {
        
        val p = Pattern.compile("^(https?)://(www.)?youtu(.be)?")
        val m = p.matcher(url)
        val formatSource = sharedPreferences.getString("formats_source", "yt-dlp")
        if (m.find() && formatSource == "piped"){
            return try {
                val id = getIDFromYoutubeURL(url)
                val res = genericRequest("$pipedURL/streams/$id")
                if (res.length() == 0) getFromYTDL(url)[0]!!
                val item = createVideoFromPipedJSON(res, "https://youtube.com/watch?v=$id")
                item!!.formats
            }catch(e: Exception) {
                if (e is CancellationException) throw e
                return getFormatsFromYTDL(url)
            }
        }else{
            return getFormatsFromYTDL(url)
        }
    }

    private fun getFormatsFromYTDL(url: String) : List<Format> {
        val request = YoutubeDLRequest(url)
        request.addOption("--print", "%(formats)s")
        request.addOption("--print", "%(duration)s")
        request.addOption("--skip-download")
        request.addOption("-R", "1")
        request.addOption("--compat-options", "manifest-filesize-approx")
        request.addOption("--socket-timeout", "5")

        if (sharedPreferences.getBoolean("force_ipv4", false)){
            request.addOption("-4")
        }

        if (sharedPreferences.getBoolean("use_cookies", false)){
            FileUtil.getCookieFile(context){
                request.addOption("--cookies", it)
            }

            val useHeader = sharedPreferences.getBoolean("use_header", false)
            val header = sharedPreferences.getString("useragent_header", "")
            if (useHeader && !header.isNullOrBlank()){
                request.addOption("--add-header","User-Agent:${header}")
            }
        }

        val proxy = sharedPreferences.getString("proxy", "")
        if (proxy!!.isNotBlank()) {
            request.addOption("--proxy", proxy)
        }



        val res = YoutubeDL.getInstance().execute(request)
        val results: Array<String?> = try {
            val lineSeparator = System.getProperty("line.separator")
            res.out.split(lineSeparator!!).toTypedArray()
        } catch (e: Exception) {
            arrayOf(res.out)
        }
        val json = results[0]
        val jsonArray = JSONArray(json)

        return parseYTDLFormats(jsonArray)
    }

    fun getFormatsMultiple(urls: List<String>, progress: (progress: List<Format>) -> Unit){
        val urlsFile = File(context.cacheDir, "urls.txt")
        urlsFile.delete()
        urlsFile.createNewFile()
        urls.forEach {
            urlsFile.appendText(it+"\n")
        }

        val formatSource = sharedPreferences.getString("formats_source", "yt-dlp")
        val p = Pattern.compile("^(https?)://(www.)?youtu(.be)?")
        val allYoutubeLinks = urls.any {p.matcher(it).find() }
        if (formatSource == "yt-dlp" || !allYoutubeLinks){
            try {
                val request = YoutubeDLRequest(emptyList())
                request.addOption("--print", "%(formats)s")
                request.addOption("-a", urlsFile.absolutePath)
                request.addOption("--skip-download")
                request.addOption("-R", "1")
                request.addOption("--socket-timeout", "5")

                if (sharedPreferences.getBoolean("force_ipv4", false)){
                    request.addOption("-4")
                }


                if (sharedPreferences.getBoolean("use_cookies", false)){
                    FileUtil.getCookieFile(context){
                        request.addOption("--cookies", it)
                    }

                    val useHeader = sharedPreferences.getBoolean("use_header", false)
                    val header = sharedPreferences.getString("useragent_header", "")
                    if (useHeader && !header.isNullOrBlank()){
                        request.addOption("--add-header","User-Agent:${header}")
                    }
                }


                val proxy = sharedPreferences.getString("proxy", "")
                if (proxy!!.isNotBlank()){
                    request.addOption("--proxy", proxy)
                }



                YoutubeDL.getInstance().execute(request){ progress, _, line ->
                    try{
                        if (line.isNotBlank()){
                            val listOfStrings = JSONArray(line)
                            progress(parseYTDLFormats(listOfStrings))
                        }

                    }catch (e: Exception){
                        progress(emptyList())
                    }
                }
            } catch (e: Exception) {
                Looper.prepare().run {
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                }
                e.printStackTrace()
            }
        }else{
            urls.forEach {
                val id = getIDFromYoutubeURL(it)
                val res = genericRequest("$pipedURL/streams/$id")
                createVideoFromPipedJSON(res, it)?.apply {
                    progress(this.formats)
                }
            }
        }

        urlsFile.delete()
    }

    @SuppressLint("RestrictedApi")
    fun getFromYTDL(query: String): ArrayList<ResultItem?> {
        val items = arrayListOf<ResultItem?>()
        val searchEngine = sharedPreferences.getString("search_engine", "ytsearch")

        val request : YoutubeDLRequest
        if (query.contains("http")){
            request = YoutubeDLRequest(query)
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
        val lang = sharedPreferences.getString("app_language", "en")
        if (searchEngine == "ytsearch" && context.getStringArray(R.array.subtitle_langs).contains(lang)){
            request.addOption("--extractor-args", "youtube:lang=$lang")
        }

        request.addOption("--flat-playlist")
        request.addOption("-j")
        request.addOption("--skip-download")
        request.addOption("-R", "1")
        request.addOption("--compat-options", "manifest-filesize-approx")
        request.addOption("--socket-timeout", "5")

        if (sharedPreferences.getBoolean("force_ipv4", false)){
            request.addOption("-4")
        }

        if (sharedPreferences.getBoolean("use_cookies", false)){
            FileUtil.getCookieFile(context){
                request.addOption("--cookies", it)
            }

            val useHeader = sharedPreferences.getBoolean("use_header", false)
            val header = sharedPreferences.getString("useragent_header", "")
            if (useHeader && !header.isNullOrBlank()){
                request.addOption("--add-header","User-Agent:${header}")
            }
        }

        val proxy = sharedPreferences.getString("proxy", "")
        if (proxy!!.isNotBlank()){
            request.addOption("--proxy", proxy)
        }



        val youtubeDLResponse = YoutubeDL.getInstance().execute(request)
        val results: List<String?> = try {
            val lineSeparator = System.getProperty("line.separator")
            youtubeDLResponse.out.split(lineSeparator!!)
        } catch (e: Exception) {
            listOf(youtubeDLResponse.out)
        }.filter { it.isNotBlank() }.apply {
            if (this.isEmpty()) throw Exception("Command Used: \n yt-dlp ${parseYTDLRequestString(request)}")
        }
        for (result in results) {
            if (result.isNullOrBlank()) continue
            val jsonObject = JSONObject(result)
            val title = jsonObject.getStringByAny("track", "alt_title", "title", "webpage_url_basename")
            if (title == "[Private video]") continue

            val author = jsonObject.getStringByAny("uploader", "channel", "playlist_uploader", "uploader_id")
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

            val website = jsonObject.getStringByAny("ie_key", "extractor_key", "extractor")
            var playlistTitle = jsonObject.getStringByAny("playlist_title")
            var playlistURL: String? = ""
            var playlistIndex: Int? = null

            if(playlistTitle.removeSurrounding("\"") == query) playlistTitle = ""

            if (playlistTitle.isNotBlank()){
                playlistURL = query
                kotlin.runCatching { playlistIndex = jsonObject.getInt("playlist_index") }
            }

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

            val url = if (jsonObject.has("url") && results.size > 1){
                jsonObject.getString("url")
            }else{
                if (Patterns.WEB_URL.matcher(query).matches()){
                    query
                }else{
                    jsonObject.getString("webpage_url")
                }
            }

            items.add(ResultItem(0,
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
            )
        }
        return items
    }

    private fun parseYTDLFormats(formatsInJSON: JSONArray?) : ArrayList<Format> {
        val formats = arrayListOf<Format>()

        if (formatsInJSON != null) {
            for (f in formatsInJSON.length() - 1 downTo 0){
                val format = formatsInJSON.getJSONObject(f)
                if (format.has("filesize")){
                    if (format.get("filesize") == "None"){
                        format.remove("filesize")
                        if (format.has("filesize_approx") && format.get("filesize_approx") != "None"){
                            format.put("filesize", format.getInt("filesize_approx"))
                        }else{
                            format.put("filesize", 0)
                        }
                    }
                    try{
                        val size = format.get("filesize").toString().toFloat()
                        format.remove("filesize")
                        format.put("filesize", size)
                    }catch (ignored: Exception){}
                }
                val formatProper = Gson().fromJson(format.toString(), Format::class.java)

                if (formatProper.format_note == null) continue
                if (format.has("format_note")){
                    if (!formatProper!!.format_note.contains("audio only", true)) {
                        formatProper.format_note = format.getString("format_note")
                    }else{
                        if (!formatProper.format_note.endsWith("audio", true)){
                            formatProper.format_note = "${format.getString("format_note")} audio"
                        }
                    }
                }
                if (formatProper.format_note == "storyboard") continue
                formatProper.container = format.getString("ext")
                if (formatProper.tbr == "None") formatProper.tbr = ""
                if (!formatProper.tbr.isNullOrBlank()){
                    formatProper.tbr = formatProper.tbr + "k"
                }

                if(formatProper.vcodec.isEmpty() || formatProper.vcodec == "null"){
                    if(formatProper.acodec.isEmpty() || formatProper.acodec == "null"){
                        formatProper.vcodec = format.getStringByAny("video_ext", "ext").ifEmpty { "unknown" }
                    }
                }

                formats.add(formatProper)
            }
        }
        return formats
    }

    fun getMissingInfo(url: String): ResultItem? {
        val request = YoutubeDLRequest(url)
        request.addOption("--flat-playlist")
        request.addOption("-J")
        request.addOption("--skip-download")
        request.addOption("-R", "1")
        request.addOption("--socket-timeout", "5")

        if (sharedPreferences.getBoolean("force_ipv4", false)){
            request.addOption("-4")
        }

        if (sharedPreferences.getBoolean("use_cookies", false)){
            FileUtil.getCookieFile(context){
                request.addOption("--cookies", it)
            }

            val useHeader = sharedPreferences.getBoolean("use_header", false)
            val header = sharedPreferences.getString("useragent_header", "")
            if (useHeader && !header.isNullOrBlank()){
                request.addOption("--add-header","User-Agent:${header}")
            }
        }



        val proxy = sharedPreferences.getString("proxy", "")
        if (proxy!!.isNotBlank()){
            request.addOption("--proxy", proxy)
        }



        val youtubeDLResponse = YoutubeDL.getInstance().execute(request)
        val jsonObject = JSONObject(youtubeDLResponse.out)

        val title = jsonObject.getStringByAny("track", "alt_title", "title", "webpage_url_basename")
        val author = jsonObject.getStringByAny("uploader", "channel", "playlist_uploader", "uploader_id")

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

        val isPlaylist = jsonObject.has("playlist_count")
        return ResultItem(
            0,
            url,
            if (isPlaylist) "" else title,
            author,
            duration,
            thumb!!,
            jsonObject.getString("extractor"),
            if (isPlaylist) jsonObject.getString("title") else "",
            arrayListOf(),
            "",
            arrayListOf(),
            "",
            null,
            System.currentTimeMillis()
        )
    }


    fun getSearchSuggestions(query: String): ArrayList<String> {
        val url = "https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&client=firefox&q=$query"
        val res = genericArrayRequest(url)
        Log.e("aa", res.toString())
        if (res.length() == 0) return ArrayList()
        val suggestionList = ArrayList<String>()
        try {
            for (i in 0 until res.getJSONArray(1).length()) {
                val item = res.getJSONArray(1).getString(i)
                suggestionList.add(item)
            }
        } catch (ignored: Exception) {
            ignored.printStackTrace()
        }
        return suggestionList
    }

    private fun formatDuration(dur: String): String {
        var badDur = dur
        if (dur == "P0D") {
            return "LIVE"
        }
        var hours = false
        var duration = ""
        badDur = badDur.substring(2)
        if (badDur.contains("H")) {
            hours = true
            duration += String.format(
                Locale.getDefault(),
                "%02d",
                badDur.substring(0, badDur.indexOf("H")).toInt()
            ) + ":"
            badDur = badDur.substring(badDur.indexOf("H") + 1)
        }
        if (badDur.contains("M")) {
            duration += String.format(
                Locale.getDefault(),
                "%02d",
                badDur.substring(0, badDur.indexOf("M")).toInt()
            ) + ":"
            badDur = badDur.substring(badDur.indexOf("M") + 1)
        } else if (hours) duration += "00:"
        if (badDur.contains("S")) {
            if (duration.isEmpty()) duration = "00:"
            duration += String.format(
                Locale.getDefault(),
                "%02d",
                badDur.substring(0, badDur.indexOf("S")).toInt()
            )
        } else {
            duration += "00"
        }
        if (duration == "00:00") {
            duration = ""
        }
        return duration
    }



    fun getStreamingUrlAndChapters(url: String) : MutableList<String?> {
        try {
            val p = Pattern.compile("(^(https?)://(www.)?(music.)?youtu(.be)?)|(^(https?)://(www.)?piped.video)")
            val m = p.matcher(url)

            if (m.find()){
                return getStreamingUrlAndChaptersFromPIPED(url)
            }else{
                throw Exception()
            }
        }catch (e: Exception) {
            try {
                val request = YoutubeDLRequest(url)
                request.addOption("--get-url")
                request.addOption("--print", "%(chapters)s")
                request.addOption("--skip-download")
                request.addOption("-R", "1")
                request.addOption("--socket-timeout", "5")

                if (sharedPreferences.getBoolean("force_ipv4", false)){
                    request.addOption("-4")
                }

                if (sharedPreferences.getBoolean("use_cookies", false)){
                    FileUtil.getCookieFile(context){
                        request.addOption("--cookies", it)
                    }

                    val useHeader = sharedPreferences.getBoolean("use_header", false)
                    val header = sharedPreferences.getString("useragent_header", "")
                    if (useHeader && !header.isNullOrBlank()){
                        request.addOption("--add-header","User-Agent:${header}")
                    }
                }



                val proxy = sharedPreferences.getString("proxy", "")
                if (proxy!!.isNotBlank()){
                    request.addOption("--proxy", proxy)
                }



                val youtubeDLResponse = YoutubeDL.getInstance().execute(request)
                val results: Array<String?> = try {
                    val lineSeparator = System.getProperty("line.separator")
                    youtubeDLResponse.out.split(lineSeparator!!).toTypedArray()
                } catch (e: Exception) {
                    arrayOf(youtubeDLResponse.out)
                }
                return results.filter { it!!.isNotEmpty() }.toMutableList()
            } catch (e: Exception) {
                return mutableListOf()
            }
        }
    }

    private fun getStreamingUrlAndChaptersFromPIPED(url: String) : MutableList<String?> {
        val id = getIDFromYoutubeURL(url)
        val res = genericRequest("$pipedURL/streams/$id")
        if (res.length() == 0) {
            throw Exception()
        }else{
            val item = createVideoFromPipedJSON(res, url)
            if (item!!.urls.isBlank()) throw Exception()
            val list = mutableListOf<String?>(Gson().toJson(item.chapters))
            list.addAll(item.urls.split(","))
            return list
        }
    }

    @SuppressLint("RestrictedApi")
    fun buildYoutubeDLRequest(downloadItem: DownloadItem) : YoutubeDLRequest{
        val request = if (downloadItem.playlistURL.isNullOrBlank() || downloadItem.playlistTitle.isBlank()){
            YoutubeDLRequest(downloadItem.url)
        }else{
            YoutubeDLRequest(downloadItem.playlistURL!!).apply {
                if(downloadItem.playlistIndex == null){
                    val matchPortion = downloadItem.url.split("/").last().split("=").last().split("&").first()
                    addOption("--match-filter", "id~='${matchPortion}'")
                }else{
                    addOption("-I", "${downloadItem.playlistIndex!!}:${downloadItem.playlistIndex}")
                }
            }
        }

        val type = downloadItem.type

        val downDir : File
        if (!sharedPreferences.getBoolean("cache_downloads", true) && File(FileUtil.formatPath(downloadItem.downloadPath)).canWrite()){
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
            request.addOption("--external-downloader-args", "aria2c:\"--summary-interval=1\"")
        }

        val concurrentFragments = sharedPreferences.getInt("concurrent_fragments", 1)
        if (concurrentFragments > 1) request.addOption("-N", concurrentFragments)

        val retries = sharedPreferences.getString("--retries", "")!!
        val fragmentRetries = sharedPreferences.getString("--fragment_retries", "")!!

        if(retries.isNotEmpty()) request.addOption("--retries", retries)
        if(fragmentRetries.isNotEmpty()) request.addOption("--fragment-retries", fragmentRetries)

        val limitRate = sharedPreferences.getString("limit_rate", "")!!
        if (limitRate.isNotBlank()) request.addOption("-r", limitRate)

        val bufferSize = sharedPreferences.getString("buffer_size", "")!!
        if (bufferSize.isNotBlank()){
            request.addOption("--buffer-size", bufferSize)
            request.addOption("--no-resize-buffer")
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

        val proxy = sharedPreferences.getString("proxy", "")
        if (proxy!!.isNotBlank()){
            request.addOption("--proxy", proxy)
        }

        val keepCache = sharedPreferences.getBoolean("keep_cache", false)
        if(keepCache){
            request.addOption("--part")
            request.addOption("--keep-fragments")
        }

        val embedMetadata = sharedPreferences.getBoolean("embed_metadata", true)
        var filenameTemplate = downloadItem.customFileNameTemplate

        if(downloadItem.type != DownloadViewModel.Type.command){
            request.addOption("--trim-filenames",  254 - downDir.absolutePath.length)

            if (downloadItem.SaveThumb) {
                request.addOption("--write-thumbnail")
                request.addOption("--convert-thumbnails", "jpg")
            }
            if (!sharedPreferences.getBoolean("mtime", false)){
                request.addOption("--no-mtime")
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
                request.addCommands(listOf("--replace-in-metadata", "title", ".+", downloadItem.title))
            }

            request.addOption("--parse-metadata", "%(channel,uploader,creator,artist|null)s:%(uploader)s")
            request.addOption("--parse-metadata", "%(uploader)s:%(album_artist)s")
            if (downloadItem.author.isNotBlank()){
                request.addCommands(listOf("--replace-in-metadata", "uploader", ".+", downloadItem.author))
                request.addCommands(listOf("--replace-in-metadata","uploader"," - Topic$",""))
            }

            if (downloadItem.downloadSections.isNotBlank()){
                downloadItem.downloadSections.split(";").forEach {
                    if (it.isBlank()) return@forEach
                    if (it.contains(":"))
                        request.addOption("--download-sections", "*$it")
                    else
                        request.addOption("--download-sections", it)

                    if (sharedPreferences.getBoolean("force_keyframes", false) && !request.hasOption("--force-keyframes-at-cuts")){
                        request.addOption("--force-keyframes-at-cuts")
                    }
                }
                filenameTemplate = if (filenameTemplate.isBlank()){
                    "%(section_title&{} |)s%(title)s"
                }else{
                    "%(section_title&{} |)s$filenameTemplate"
                }
                if (downloadItem.downloadSections.split(";").size > 1){
                    filenameTemplate = "%(autonumber)d. %(section_start>%H∶%M∶%S)s $filenameTemplate"
                }
            }

            if (sharedPreferences.getBoolean("use_audio_quality", false)){
                request.addOption("--audio-quality", sharedPreferences.getInt("audio_quality", 0))
            }

            if (downloadItem.extraCommands.isNotBlank()){
                val cache = File(FileUtil.getCachePath(context))
                cache.mkdirs()
                val conf = File(cache.absolutePath + "/${System.currentTimeMillis()}.txt")
                conf.createNewFile()
                conf.writeText(downloadItem.extraCommands)
                request.addOption(
                    "--config-locations",
                    conf.absolutePath
                )
            }

            if (sharedPreferences.getBoolean("write_description", false)){
                request.addOption("--write-description")
            }

            filenameTemplate = filenameTemplate.replace("%(uploader)s", "%(uploader,channel)s")
        }

        if (downloadItem.playlistTitle.isNotBlank()){
            request.addCommands(listOf("--replace-in-metadata", "playlist", ".+", downloadItem.playlistTitle))
            runCatching {
                if (downloadItem.playlistIndex != null){
                    request.addOption("--parse-metadata", downloadItem.playlistIndex.toString() + ":%(playlist_index)s")
                }
            }
        }

        if (sharedPreferences.getBoolean("download_archive", false)){
            request.addOption("--download-archive", FileUtil.getDownloadArchivePath(context))
        }

        val preferredAudioCodec = sharedPreferences.getString("audio_codec", "")!!
        val aCodecPrefIndex = context.getStringArray(R.array.audio_codec_values).indexOf(preferredAudioCodec)
        val aCodecPref = runCatching { context.getStringArray(R.array.audio_codec_values_ytdlp)[aCodecPrefIndex] }.getOrElse { "" }

        when(type){
            DownloadViewModel.Type.audio -> {
                val supportedContainers = context.resources.getStringArray(R.array.audio_containers)

                var audioQualityId : String = downloadItem.format.format_id
                if (audioQualityId.isBlank() || listOf("0", context.getString(R.string.best_quality), "ba", "best", "").contains(audioQualityId)){
                    audioQualityId = ""
                }else if (listOf(context.getString(R.string.worst_quality), "wa", "worst").contains(audioQualityId)){
                    audioQualityId = "wa/w"
                }


                val ext = downloadItem.container
                if (audioQualityId.isNotBlank()) {
                    if (audioQualityId.contains("-") && !downloadItem.format.lang.isNullOrBlank() && downloadItem.format.lang != "None"){
                        audioQualityId = "ba[format_id~='^(${audioQualityId.split("-")[0]})'][language^=${downloadItem.format.lang}]/ba/b"
                    }
                    request.addOption("-f", audioQualityId)
                }else{
                    //enters here if generic or quick downloaded with ba format
                    val preferredLanguage = sharedPreferences.getString("audio_language","")!!
                    if (preferredLanguage.isNotBlank()){
                        request.addOption("-f", "ba[language^=$preferredLanguage]/ba/b")
                    }
                }
                request.addOption("-x")

                val formatSorting = StringBuilder("hasaud")

                if (aCodecPref.isNotBlank()){
                    formatSorting.append(",acodec:$aCodecPref")
                }

                if(ext.isNotBlank()){
                    if(!ext.matches("(webm)|(Default)|(${context.getString(R.string.defaultValue)})".toRegex()) && supportedContainers.contains(ext)){
                        request.addOption("--audio-format", ext)
                        formatSorting.append(",ext:$ext")
                    }
                }

                request.addOption("-P", downDir.absolutePath)
                request.addOption("-S", formatSorting.toString())

                if (downloadItem.audioPreferences.splitByChapters && downloadItem.downloadSections.isBlank()){
                    request.addOption("--split-chapters")
                    request.addOption("-o", "chapter:%(section_title)s.%(ext)s")
                }else{

                    if (embedMetadata){
                        request.addOption("--embed-metadata")

                        request.addOption("--parse-metadata", "%(release_year,release_date>%Y,upload_date>%Y)s:%(meta_date)s")
                        request.addOption("--parse-metadata", "video:%(uploader)s:%(album_artist)s")

                        if (downloadItem.playlistTitle.isNotEmpty()) {
                            request.addOption("--parse-metadata", "%(album,playlist,title)s:%(meta_album)s")
                            request.addOption("--parse-metadata", "%(track_number,playlist_index)d:%(meta_track)s")
                        } else {
                            request.addOption("--parse-metadata", "%(album,title)s:%(meta_album)s")
                        }
                    }


                    if (downloadItem.audioPreferences.embedThumb) {
                        request.addOption("--embed-thumbnail")
                        if (! request.hasOption("--convert-thumbnails")) request.addOption("--convert-thumbnails", "jpg")
                        val cropThumb = downloadItem.audioPreferences.cropThumb ?: sharedPreferences.getBoolean("crop_thumbnail", true)
                        if (cropThumb){
                            try {
                                val config = File(context.cacheDir.absolutePath + "/config" + downloadItem.id + "##ffmpegCrop.txt")
                                val configData = "--ppa \"ffmpeg:-c:v mjpeg -vf crop=\\\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\\\"\""
                                config.writeText(configData)
                                request.addOption("--ppa", "ThumbnailsConvertor:-qmin 1 -q:v 1")
                                request.addOption("--config", config.absolutePath)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    if (filenameTemplate.isNotBlank()){
                        request.addOption("-o", "${filenameTemplate.removeSuffix(".%(ext)s")}.%(ext)s")
                    }
                }

            }
            DownloadViewModel.Type.video -> {
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
                    request.addOption("--merge-output-format", outputContainer.lowercase())
                    if (outputContainer != "webm") {
                        val embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false)
                        if (embedThumb) {
                            request.addOption("--embed-thumbnail")
                        }
                    }
                }

                //format logic
                var videoF = downloadItem.format.format_id
                var audioF = downloadItem.videoPreferences.audioFormatIDs.joinToString("+").ifBlank { "ba" }
                val preferredAudioLanguage = sharedPreferences.getString("audio_language", "")!!
                if (downloadItem.videoPreferences.removeAudio) audioF = ""

                val f = StringBuilder()

                val preferredCodec = sharedPreferences.getString("video_codec", "")
                val vCodecPrefIndex = context.getStringArray(R.array.video_codec_values).indexOf(preferredCodec)
                val vCodecPref = context.getStringArray(R.array.video_codec_values_ytdlp)[vCodecPrefIndex]

                val defaultFormats = context.resources.getStringArray(R.array.video_formats_values)
                val usingGenericFormat = defaultFormats.contains(videoF) || downloadItem.allFormats.isEmpty() || downloadItem.allFormats == getGenericVideoFormats(context.resources)
                if (!usingGenericFormat){
                    // with audio removed
                    if (audioF.isBlank()){
                        f.append("$videoF/bv/b")
                    }else{
                        //with audio
                        audioF = "+$audioF"
                        f.append("$videoF$audioF/$videoF+ba/$videoF/b")

                        if (audioF.count("+") > 1){
                            request.addOption("--audio-multistreams")
                        }
                    }
                }else{
                    if (videoF == context.resources.getString(R.string.best_quality) || videoF == "best") {
                        videoF = "bv"
                    }else if (videoF == context.resources.getString(R.string.worst_quality) || videoF == "worst") {
                        videoF = "wv"
                        if (audioF == "ba") audioF = "wa"
                    }else if (defaultFormats.contains(videoF)) {
                        videoF = "bv[height<="+videoF.split("_")[0].dropLast(1)+"]"
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
                        preferredAudioFormatIDs.forEach { a ->
                            val aa = if (a.isNotBlank()) "+$a" else ""
                            f.append("$v$aa/")
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

                StringBuilder().apply {
                    if (vCodecPref.isNotBlank()) append(",vcodec:$vCodecPref")
                    if (aCodecPref.isNotBlank()) append(",acodec:$aCodecPref")
                    if (cont.isNotBlank()) append(",ext:$cont")
                    if (this.isNotBlank()){
                        request.addOption("-S", "+hasaud$this")
                    }
                }


                request.addOption("-f", f.toString().replace("/$".toRegex(), ""))

                if (downloadItem.videoPreferences.writeSubs){
                    val subFormat = sharedPreferences.getString("sub_format", "srt")

                    request.addOption("--write-subs")
                    if(subFormat!!.isNotBlank()){
                        request.addOption("--sub-format", "${subFormat}/best")
                        request.addOption("--convert-subtitles", subFormat ?: "srt")
                    }

                }

                if (downloadItem.videoPreferences.embedSubs) {
                    request.addOption("--embed-subs")
                }

                if(downloadItem.videoPreferences.writeAutoSubs){
                    request.addOption("--write-auto-subs")
                }

                if (downloadItem.videoPreferences.embedSubs || downloadItem.videoPreferences.writeSubs || downloadItem.videoPreferences.writeAutoSubs){
                    request.addOption("--sub-langs", downloadItem.videoPreferences.subsLanguages.ifEmpty { "en.*,.*-orig" })
                }



                if (downloadItem.videoPreferences.removeAudio){
                    request.addOption("--ppa", "ffmpeg:-an")
                }

                request.addOption("-P", downDir.absolutePath)

                if (downloadItem.videoPreferences.splitByChapters  && downloadItem.downloadSections.isBlank()){
                    request.addOption("--split-chapters")
                    request.addOption("-o", "chapter:%(section_title)s.%(ext)s")
                }else{
                    if (filenameTemplate.isNotBlank()){
                        request.addOption("-o", "${filenameTemplate.removeSuffix(".%(ext)s")}.%(ext)s")
                    }
                }

            }
            DownloadViewModel.Type.command -> {
                request.addOption("-P", downDir.absolutePath)
                request.addOption(
                    "--config-locations",
                    File(context.cacheDir.absolutePath + "/config[${downloadItem.id}].txt").apply {
                        writeText(downloadItem.format.format_note)
                    }.absolutePath
                )

            }

            else -> {}
        }

        return request
    }

    fun getPipedInstances() : List<String> {
        kotlin.runCatching {
            val res = genericArrayRequest("https://piped-instances.kavin.rocks/")
            val list = mutableListOf<String>()
            for (i in 0 until res.length()) {
                val element = res.getJSONObject(i)
                list.add(element.getString("api_url"))
            }
            return list
        }
        return listOf()
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

    fun getGenericAudioFormats(resources: Resources) : MutableList<Format>{
        val audioFormatIDPreference = sharedPreferences.getString("format_id_audio", "").toString().split(",").filter { it.isNotEmpty() }
        val audioFormats = resources.getStringArray(R.array.audio_formats)
        val audioFormatsValues = resources.getStringArray(R.array.audio_formats_values)
        val formats = mutableListOf<Format>()
        val containerPreference = sharedPreferences.getString("audio_format", "")
        val acodecPreference = sharedPreferences.getString("audio_codec", "m4a|mp4a|aac")
        audioFormats.forEachIndexed { idx, it -> formats.add(Format(audioFormatsValues[idx], containerPreference!!,"",acodecPreference!!, "",0, it)) }
        audioFormats.reverse()
        audioFormatIDPreference.forEach { formats.add(Format(it, containerPreference!!,"",acodecPreference!!, "",1, it)) }
        return formats
    }

    fun getGenericVideoFormats(resources: Resources) : MutableList<Format>{
        val formatIDPreference = sharedPreferences.getString("format_id", "").toString().split(",").filter { it.isNotEmpty() }
        val videoFormats = resources.getStringArray(R.array.video_formats_values)
        val formats = mutableListOf<Format>()
        val containerPreference = sharedPreferences.getString("video_format", "")
        val acodecPreference = sharedPreferences.getString("audio_codec", "m4a|mp4a|aac")
        val vcodecPreference = sharedPreferences.getString("video_codec", "avc|h264")!!.ifEmpty { resources.getString(R.string.defaultValue) }
        videoFormats.reversed().forEach { formats.add(Format(it, containerPreference!!,vcodecPreference,acodecPreference!!, "",0, it.split("_")[0])) }
        formatIDPreference.forEach { formats.add(Format(it, containerPreference!!,vcodecPreference,acodecPreference!!, "",1, it)) }
        return formats
    }

    private val pipedURL = sharedPreferences.getString("piped_instance", defaultPipedURL)?.ifEmpty { defaultPipedURL }?.removeSuffix("/")

    class PlaylistTuple internal constructor(
        var nextPageToken: String,
        var videos: ArrayList<ResultItem?>
    )

    companion object {
        private const val TAG = "API MANAGER"
        private const val defaultPipedURL = "https://pipedapi.kavin.rocks/"
        private var countryCODE: String = ""
    }
}