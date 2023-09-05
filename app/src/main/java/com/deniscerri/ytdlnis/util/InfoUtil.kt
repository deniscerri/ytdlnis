package com.deniscerri.ytdlnis.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.text.Html
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.ChapterItem
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
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
    private var items: ArrayList<ResultItem?>
    private lateinit var sharedPreferences: SharedPreferences

    init {
        try {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            countryCODE = sharedPreferences.getString("locale", "")!!
            if (countryCODE.isEmpty()) countryCODE = "US"
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items = ArrayList()
    }


    fun search(query: String): ArrayList<ResultItem?> {
        items = ArrayList()
        return when(sharedPreferences.getString("search_engine", "ytsearch")){
            "ytsearch" -> try{
                searchFromPiped(query)
            }catch (e: Exception){
                getFromYTDL(query)
            }

            "ytsearchmusic" -> try{
                searchFromPipedMusic(query)
            }catch (e: Exception){
                getFromYTDL(query)
            }

            else -> getFromYTDL(query)
        }
    }

    @Throws(JSONException::class)
    fun searchFromPiped(query: String): ArrayList<ResultItem?> {
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
    fun getPlaylist(id: String, nextPageToken: String): PlaylistTuple {
        try{
            items = ArrayList()
            // -------------- PIPED API FUNCTION -------------------
            var url = ""
            url = if (nextPageToken.isBlank()) "$pipedURL/playlists/$id"
            else """$pipedURL/nextpage/playlists/$id?nextpage=${nextPageToken.replace("&prettyPrint", "%26prettyPrint")}"""

            val res = genericRequest(url)
            if (!res.has("relatedStreams")) throw Exception()

            val dataArray = res.getJSONArray("relatedStreams")
            var nextpage = res.getString("nextpage")
            for (i in 0 until dataArray.length()){
                val obj = dataArray.getJSONObject(i)
                val itm = createVideoFromPipedJSON(obj, "https://youtube.com" + obj.getString("url"))
                itm?.playlistTitle = "YTDLnis"
                items.add(itm)
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

            val duration = formatIntegerDuration(obj.getInt("duration"), Locale.US)
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
                        }catch (e: Exception) {
                            e.printStackTrace()
                        }
                        formats.add(formatObj)
                    }

                }
                formats.sortBy { it.filesize }
                formats.groupBy { it.format_id }.forEach {
                    if (it.value.count() > 1) {
                        it.value.filter { f-> !f.format_note.contains("original", true) }.forEachIndexed { index, format -> format.format_id = format.format_id.split("-")[0] + "-${index}" }
                        val engDefault = it.value.find { f -> f.format_note.contains("original", true) }
                        engDefault?.format_id = (engDefault?.format_id?.split("-")?.get(0) ?: "") + "-${it.value.size-1}"
                    }
                }
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
        items = ArrayList()
        if (sharedPreferences.getString("api_key", "")!!.isNotBlank()){
            return getTrendingFromYoutubeAPI()
        }
        return getTrendingFromPiped()
    }

    @Throws(JSONException::class)
    fun getTrendingFromYoutubeAPI(): ArrayList<ResultItem?> {
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
            v.playlistTitle = context.getString(R.string.trendingPlaylist)
            items.add(v)
        }
        return items
    }

    private fun getTrendingFromPiped(): ArrayList<ResultItem?> {
        val url = "$pipedURL/trending?region=$countryCODE"
        val res = genericArrayRequest(url)
        try {
            for (i in 0 until res.length()) {
                val element = res.getJSONObject(i)
                if (element.getInt("duration") < 0) continue
                element.put("uploader", element.getString("uploaderName"))
                val v = createVideoFromPipedJSON(element, "https://youtube.com" + element.getString("url"))
                if (v == null || v.thumb.isEmpty()) continue
                v.playlistTitle = context.getString(R.string.trendingPlaylist)
                items.add(v)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        return try {
            if(m.find() && formatSource == "piped"){
                val id = getIDFromYoutubeURL(url)
                val res = genericRequest("$pipedURL/streams/$id")
                if (res.length() == 0) getFromYTDL(url)[0]!!
                val item = createVideoFromPipedJSON(res, "https://youtube.com/watch?v=$id")
                item!!.formats
            }else{
                getFormatsFromYTDL(url)
            }
        }catch (e: Exception){
            getFormatsFromYTDL(url)
        }
    }

    private fun getFormatsFromYTDL(url: String) : List<Format> {
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--print", "%(formats)s")
            request.addOption("--print", "%(duration)s")
            request.addOption("--skip-download")
            request.addOption("-R", "1")
            request.addOption("--socket-timeout", "5")

            if (sharedPreferences.getBoolean("use_cookies", false)){
                FileUtil.getCookieFile(context){
                    request.addOption("--cookies", it)
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
        } catch (e: Exception) {
            e.printStackTrace()
            Looper.prepare().run {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            }
        }
        return emptyList()
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


                if (sharedPreferences.getBoolean("use_cookies", false)){
                    FileUtil.getCookieFile(context){
                        request.addOption("--cookies", it)
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
                val vid = createVideoFromPipedJSON(res, it)
                progress(vid!!.formats)
            }
        }

        urlsFile.delete()
    }

    fun getFromYTDL(query: String): ArrayList<ResultItem?> {
        items = ArrayList()
        val webpage_urls = mutableListOf<String>()
        val searchEngine = sharedPreferences.getString("search_engine", "ytsearch")
        try {
            val request : YoutubeDLRequest
            if (query.contains("http")){
                request = YoutubeDLRequest(query)
            }else{
                request = YoutubeDLRequest(emptyList())
                if (searchEngine == "ytsearchmusic"){
                    request.addOption("--default-search", "https://music.youtube.com/search?q=")
                    request.addOption("ytsearch25:\"${query}\"")
                }else{
                    request.addOption("${searchEngine}25:\"${query}\"")
                }
            }

            request.addOption("--flat-playlist")
            request.addOption("-j")
            request.addOption("--skip-download")
            request.addOption("-R", "1")
            request.addOption("--socket-timeout", "5")

            if (sharedPreferences.getBoolean("use_cookies", false)){
                FileUtil.getCookieFile(context){
                    request.addOption("--cookies", it)
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
            for (result in results) {
                if (result.isNullOrBlank()) continue
                val jsonObject = JSONObject(result)
                val title = if (jsonObject.has("title")) {
                    if (jsonObject.getString("title") == "[Private video]") continue
                    jsonObject.getString("title")
                } else {
                    jsonObject.getString("webpage_url_basename")
                }
                var author: String = if (jsonObject.has("uploader")) jsonObject.getString("uploader") else ""
                if (author.isEmpty() || author == "null"){
                    author = if (jsonObject.has("channel")) jsonObject.getString("channel") else ""
                    if (author.isEmpty() || author == "null"){
                        author = if (jsonObject.has("playlist_uploader")) jsonObject.getString("playlist_uploader") else ""
                    }
                }
                var duration = ""
                runCatching {
                    if (jsonObject.has("duration")) {
                        duration = formatIntegerDuration(jsonObject.getInt("duration"), Locale.US)
                    }
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
                val website = if (jsonObject.has("ie_key")) jsonObject.getString("ie_key") else jsonObject.getString("extractor")
                var playlistTitle: String? = ""
                if (jsonObject.has("playlist_title")) playlistTitle = jsonObject.getString("playlist_title")
                if(playlistTitle.equals(query)) playlistTitle = ""
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

                val url = if (jsonObject.has("url")){
                    jsonObject.getString("url")
                }else{
                    jsonObject.getString("webpage_url")
                }

                items.add(ResultItem(0,
                        url,
                        title,
                        author,
                        duration,
                        thumb!!,
                        website,
                        playlistTitle!!,
                        formats,
                    urls,
                        chapters
                    )
                )
                webpage_urls.add(jsonObject.getString("webpage_url"))
            }
            if (items.size == 1){
                items[0]?.url = webpage_urls.first()
            }
        } catch (e: Exception) {
            Looper.prepare().run {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            }
            e.printStackTrace()
        }
        return items
    }

    private fun parseYTDLFormats(formatsInJSON: JSONArray?) : ArrayList<Format> {
        val formats = arrayListOf<Format>()

        if (formatsInJSON != null) {
            for (f in 0 until formatsInJSON.length()){
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
                formats.add(formatProper)
            }
        }

        return formats
    }

    fun getMissingInfo(url: String): ResultItem? {
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--flat-playlist")
            request.addOption("-J")
            request.addOption("--skip-download")
            request.addOption("-R", "1")
            request.addOption("--socket-timeout", "5")

            if (sharedPreferences.getBoolean("use_cookies", false)){
                FileUtil.getCookieFile(context){
                    request.addOption("--cookies", it)
                }
            }



            val proxy = sharedPreferences.getString("proxy", "")
            if (proxy!!.isNotBlank()){
                request.addOption("--proxy", proxy)
            }

            val youtubeDLResponse = YoutubeDL.getInstance().execute(request)
            val jsonObject = JSONObject(youtubeDLResponse.out)

            var author: String = if (jsonObject.has("uploader")) jsonObject.getString("uploader") else ""
            if (author.isEmpty() || author == "null"){
                author = if (jsonObject.has("channel")) jsonObject.getString("channel") else ""
                if (author.isEmpty() || author == "null"){
                    author = if (jsonObject.has("playlist_uploader")) jsonObject.getString("playlist_uploader") else ""
                }
            }

            var duration = ""
            runCatching {
                if (jsonObject.has("duration")) {
                    duration = formatIntegerDuration(jsonObject.getInt("duration"), Locale.US)
                }
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
                if (isPlaylist){
                    "[${jsonObject.getInt("playlist_count")} Items] ${jsonObject.getString("title")}"
                }else{
                    jsonObject.getString("title")
                },
                author,
                duration,
                thumb!!,
                jsonObject.getString("extractor"),
                if (isPlaylist) jsonObject.getString("title") else "",
                arrayListOf(),
                "",
                arrayListOf(),
                System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Looper.prepare().run {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            }
            e.printStackTrace()
        }
        return null
    }


    fun getSearchSuggestions(query: String): ArrayList<String> {
        val url = "https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&client=firefox&q=$query"
        // invidousURL + "search/suggestions?q=" + query
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

    fun formatIntegerDuration(dur: Int, locale: Locale): String {
        var format = String.format(
            locale,
            "%02d:%02d:%02d",
            dur / 3600,
            dur % 3600 / 60,
            dur % 60
        )
        // 00:00:00
        if (dur < 600) format = format.substring(4) else if (dur < 3600) format =
            format.substring(3) else if (dur < 36000) format = format.substring(1)
        return format
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

                if (sharedPreferences.getBoolean("use_cookies", false)){
                    FileUtil.getCookieFile(context){
                        request.addOption("--cookies", it)
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

    fun getFilePaths(request: YoutubeDLRequest) : List<String>{
        return try{
            request.addOption("--print", "filename")
            val res = YoutubeDL.getInstance().execute(request)
            val results: Array<String?> = try {
                val lineSeparator = System.getProperty("line.separator")
                res.out.split(lineSeparator!!).toTypedArray()
            } catch (e: Exception) {
                arrayOf(res.out)
            }
            results.filter { !it.isNullOrBlank() }.map { it!!.substring(0, it.lastIndexOf(".")) }.map { it.substring(it.lastIndexOf("/") + 1, it.length) }
        }catch (e: Exception){
            listOf()
        }
    }

    fun buildYoutubeDLRequest(downloadItem: DownloadItem) : YoutubeDLRequest{
        val url = downloadItem.url
        val request = YoutubeDLRequest(url)
        val type = downloadItem.type

        val downDir : File
        if (!sharedPreferences.getBoolean("cache_downloads", true) && File(FileUtil.formatPath(downloadItem.downloadPath)).canWrite()){
            downDir = File(FileUtil.formatPath(downloadItem.downloadPath))
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
        } else {
            val concurrentFragments = sharedPreferences.getInt("concurrent_fragments", 1)
            if (concurrentFragments > 1) request.addOption("-N", concurrentFragments)
        }

        val retries = sharedPreferences.getString("--retries", "")!!
        val fragmentRetries = sharedPreferences.getString("--fragment_retries", "")!!

        if(retries.isNotEmpty()) request.addOption("retries", retries)
        if(fragmentRetries.isNotEmpty()) request.addOption("fragment-retries", fragmentRetries)

        val limitRate = sharedPreferences.getString("limit_rate", "")
        if (limitRate != "") request.addOption("-r", limitRate!!)
        if(downloadItem.type != DownloadViewModel.Type.command){
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
                request.addCommands(listOf("--replace-in-metadata","title",".*.",downloadItem.title.take(200)))
            }
            if (downloadItem.author.isNotBlank()){
                request.addCommands(listOf("--replace-in-metadata","uploader",".*.",downloadItem.author.take(25)))
                downloadItem.customFileNameTemplate = downloadItem.customFileNameTemplate.replace("%(uploader)s", downloadItem.author.take(25))
            }
            request.addCommands(listOf("--replace-in-metadata","uploader"," - Topic$",""))

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
                downloadItem.customFileNameTemplate += " %(section_title|)s "
                if (downloadItem.downloadSections.split(";").size > 1){
                    downloadItem.customFileNameTemplate += "%(autonumber)s"
                }
                request.addOption("--output-na-placeholder", " ")
            }

            if (sharedPreferences.getBoolean("use_audio_quality", false)){
                request.addOption("--audio-quality", sharedPreferences.getInt("audio_quality", 0))
            }

            if (downloadItem.extraCommands.isNotBlank()){
                val conf = File(FileUtil.getCachePath(context) + "/${System.currentTimeMillis()}.txt")
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
        }

        if (sharedPreferences.getBoolean("restrict_filenames", true)) {
            request.addOption("--restrict-filenames")
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

        when(type){
            DownloadViewModel.Type.audio -> {
                val supportedContainers = context.resources.getStringArray(R.array.audio_containers)

                var audioQualityId : String = downloadItem.format.format_id
                if (audioQualityId.isBlank() || audioQualityId == "0" || audioQualityId == context.getString(R.string.best_quality) || audioQualityId == "best") audioQualityId = ""
                else if (audioQualityId == context.getString(R.string.worst_quality) || audioQualityId == "worst") audioQualityId = "worstaudio"

                val ext = downloadItem.container
                if (audioQualityId.isNotBlank()) request.addOption("-f", audioQualityId)
                request.addOption("-x")

                if(ext.isNotBlank()){
                    if(!ext.matches("(webm)|(Default)|(${context.getString(R.string.defaultValue)})".toRegex()) && supportedContainers.contains(ext)){
                        request.addOption("--audio-format", ext)
                    }
                }

                request.addOption("-P", downDir.absolutePath)

                if (downloadItem.audioPreferences.splitByChapters && downloadItem.downloadSections.isBlank()){
                    request.addOption("--split-chapters")
                    request.addOption("-o", "chapter:%(section_title)s.%(ext)s")
                }else{

                    if (sharedPreferences.getBoolean("embed_metadata", true)){
                        request.addOption("--embed-metadata")

                        request.addOption("--parse-metadata", "%(release_year,upload_date)s:%(meta_date)s")

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
                        if (sharedPreferences.getBoolean("crop_thumbnail", true)){
                            try {
                                val config = File(context.cacheDir.absolutePath + "/config" + downloadItem.title + "##" + downloadItem.format.format_id + ".txt")
                                val configData = "--ppa \"ffmpeg: -c:v mjpeg -vf crop=\\\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\\\"\""
                                config.writeText(configData)
                                request.addOption("--ppa", "ThumbnailsConvertor:-qmin 1 -q:v 1")
                                request.addOption("--config", config.absolutePath)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    if (downloadItem.customFileNameTemplate.isNotBlank()){
                        request.addOption("-o", "${downloadItem.customFileNameTemplate}.%(ext)s")
                    }
                }

            }
            DownloadViewModel.Type.video -> {
                val supportedContainers = context.resources.getStringArray(R.array.video_containers)

                if (!sharedPreferences.getBoolean("embed_metadata", true)){
                    request.addOption("--no-embed-metadata")
                }

                if (downloadItem.videoPreferences.addChapters) {
                    if (sharedPreferences.getBoolean("use_sponsorblock", true)){
                        request.addOption("--sponsorblock-mark", "all")
                    }
                    request.addOption("--embed-chapters")
                }
                if (downloadItem.videoPreferences.embedSubs) {
                    request.addOption("--embed-subs")
                    request.addOption("--sub-langs", downloadItem.videoPreferences.subsLanguages.ifEmpty { "en.*,.*-orig" })
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

                val defaultFormats = context.resources.getStringArray(R.array.video_formats)
                var usingGenericFormat = false

                var videof = downloadItem.format.format_id
                if (videof == context.resources.getString(R.string.best_quality) || videof == "best") {
                    videof = "bestvideo"
                    usingGenericFormat = true
                }else if (videof == context.resources.getString(R.string.worst_quality) || videof == "worst") {
                    videof = "worst"
                }else if (defaultFormats.contains(videof)) {
                    videof = "bestvideo[height<="+videof.substring(0, videof.length -1)+"]"
                }

                val preferredFormatIDs = sharedPreferences.getString("format_id", "").toString()
                    .split(",")
                    .filter { it.isNotEmpty() }
                    .ifEmpty { listOf(videof) }

                var audiof = "bestaudio"
                if (downloadItem.videoPreferences.audioFormatIDs.isNotEmpty()){
                    audiof = downloadItem.videoPreferences.audioFormatIDs.joinToString("+")
                }
                if (downloadItem.videoPreferences.removeAudio) audiof = ""

                val preferredAudioFormatIDs = sharedPreferences.getString("format_id_audio", "")
                    .toString()
                    .split(",")
                    .filter { it.isNotEmpty() }
                    .ifEmpty { listOf(audiof) }

                val preferredVideoFormats = if (usingGenericFormat) preferredFormatIDs else listOf(videof)
                val preferredAudioFormats = if (usingGenericFormat && downloadItem.videoPreferences.audioFormatIDs.isEmpty()) {
                    preferredAudioFormatIDs
                } else listOf(audiof)

                if (preferredAudioFormats.any{it.contains("+")}){
                    request.addOption("--audio-multistreams")
                }

                val preferredCodec = sharedPreferences.getString("video_codec", "")
                val extPref = if (cont.isNotBlank()) "[ext=$cont]" else ""
                val vCodecPref = if (preferredCodec!!.isNotBlank()) "[vcodec^=$preferredCodec]" else ""

                val f = StringBuilder()

                //build format with extension and vcodec
                preferredVideoFormats.forEach { v ->
                    preferredAudioFormats.forEach { a ->
                        val aa = if (a.isNotBlank()) "+$a" else ""
                        f.append("$v$extPref$vCodecPref$aa/")
                    }
                }

                //build format with vcodec
                if (extPref.isNotBlank()){
                    preferredVideoFormats.forEach { v ->
                        preferredAudioFormats.forEach { a ->
                            val aa = if (a.isNotBlank()) "+$a" else ""
                            f.append("$videof$vCodecPref$aa/")
                        }
                    }
                }

                if (vCodecPref.isNotBlank()){
                    preferredVideoFormats.forEach { v ->
                        //build format with vcodec
                        preferredAudioFormats.forEach {a ->
                            val aa = if (a.isNotBlank()) "+$a" else ""
                            f.append("$v$aa/")
                        }
                    }
                }

                //build format with best audio
                preferredVideoFormats.forEach { v ->
                    if(!f.contains("$v+bestaudio/")){
                        f.append("$v+bestaudio/")
                    }
                }


                //build formats with standalone video
                preferredVideoFormats.forEach { v ->
                    if(!f.contains("/$v/")){
                        f.append("$v/")
                    }
                }

                if(!f.endsWith("best/")){
                    //last fallback
                    f.append("best")
                }

                request.addOption("-f", f.toString().replace("/$".toRegex(), ""))

                if (downloadItem.videoPreferences.writeSubs){
                    val subFormat = sharedPreferences.getString("sub_format", "srt")

                    request.addOption("--write-subs")
                    request.addOption("--write-auto-subs")
                    request.addOption("--sub-format", "${subFormat}/best")
                    request.addOption("--convert-subtitles", subFormat ?: "srt")
                    if (!downloadItem.videoPreferences.embedSubs) {
                        request.addOption("--sub-langs", downloadItem.videoPreferences.subsLanguages.ifEmpty { "en.*,.*-orig" })
                    }
                }

                if (downloadItem.videoPreferences.removeAudio){
                    request.addOption("--ppa", "ffmpeg:-an")
                }

                request.addOption("-P", downDir.absolutePath)

                if (downloadItem.videoPreferences.splitByChapters  && downloadItem.downloadSections.isBlank()){
                    request.addOption("--split-chapters")
                    request.addOption("-o", "chapter:%(section_title)s.%(ext)s")
                }else{
                    if (downloadItem.customFileNameTemplate.isNotBlank()){
                        request.addOption("-o", "${downloadItem.customFileNameTemplate}.%(ext)s")
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
        }

        return request
    }


    fun parseYTDLRequestString(request : YoutubeDLRequest) : String {
        val arr = request.buildCommand().toMutableList()
        for (i in arr.indices) {
            if (!arr[i].startsWith("-")) {
                arr[i] = "\"${arr[i]}\""
            }
        }

        return java.lang.String.join(" ", arr).replace("\"\"", "\" \"")
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