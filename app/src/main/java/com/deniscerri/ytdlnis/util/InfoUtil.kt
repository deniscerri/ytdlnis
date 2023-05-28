package com.deniscerri.ytdlnis.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.text.Html
import android.util.Log
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.ChapterItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.ResultItem
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
    private var key: String? = null
    private var useInvidous = false

    init {
        try {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            key = sharedPreferences.getString("api_key", "")
            countryCODE = sharedPreferences.getString("locale", "")!!
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items = ArrayList()
    }

    private fun init(){
        if (countryCODE.isEmpty()){
            countryCODE = "US"
        }
        if (!useInvidous){
            val res = genericRequest(invidousURL + "stats")
            useInvidous = res.length() != 0
        }
    }

    fun search(query: String): ArrayList<ResultItem?> {
        init()
        items = ArrayList()
        val searchEngine = sharedPreferences.getString("search_engine", "ytsearch")
        return if (searchEngine == "ytsearch"){
            if (key!!.isNotEmpty()) searchFromKey(query) else {
                try{
                    searchFromPiped(query)
                }catch (e: Exception){
                    getFromYTDL(query)
                }
            }
        }else getFromYTDL(query)
    }

    @Throws(JSONException::class)
    fun searchFromKey(query: String): ArrayList<ResultItem?> {
        //short data
        val res =
            genericRequest("https://youtube.googleapis.com/youtube/v3/search?part=snippet&q=$query&maxResults=25&regionCode=$countryCODE&key=$key")
        if (!res.has("items")) return getFromYTDL(query)
        val dataArray = res.getJSONArray("items")

        //extra data
        var url2 = "https://www.googleapis.com/youtube/v3/videos?id="
        //getting all ids, for the extra data request
        for (i in 0 until dataArray.length()) {
            val element = dataArray.getJSONObject(i)
            val snippet = element.getJSONObject("snippet")
            if (element.getJSONObject("id").getString("kind") == "youtube#video") {
                val videoID = element.getJSONObject("id").getString("videoId")
                url2 = "$url2$videoID,"
                snippet.put("videoID", videoID)
            }
        }
        url2 = url2.substring(
            0,
            url2.length - 1
        ) + "&part=contentDetails&regionCode=" + countryCODE + "&key=" + key
        val extra = genericRequest(url2)
        var j = 0
        for (i in 0 until dataArray.length()) {
            val element = dataArray.getJSONObject(i)
            val snippet = element.getJSONObject("snippet")
            if (element.getJSONObject("id").getString("kind") == "youtube#video") {
                var duration =
                    extra.getJSONArray("items").getJSONObject(j++).getJSONObject("contentDetails")
                        .getString("duration")
                duration = formatDuration(duration)
                if (duration == "0:00") {
                    continue
                }
                snippet.put("duration", duration)
                fixThumbnail(snippet)
                val v = createVideofromJSON(snippet)
                if (v == null || v.thumb.isEmpty()) {
                    continue
                }
                items.add(createVideofromJSON(snippet))
            }
        }
        return items
    }

    @Throws(JSONException::class)
    fun searchFromPiped(query: String): ArrayList<ResultItem?> {
        val data = genericRequest(pipedURL + "search?q=" + query + "?filter=videos")
        val dataArray = data.getJSONArray("items")
        if (dataArray.length() == 0) return getFromYTDL(query)
        for (i in 0 until dataArray.length()) {
            val element = dataArray.getJSONObject(i)
            if (element.getInt("duration") == -1) continue
            element.put("uploader", element.getString("uploaderName"))
            val v = createVideoFromPipedJSON(element, element.getString("url").removePrefix("/watch?v="))
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
            init()
            items = ArrayList()
            if (key!!.isEmpty()) {
                return if (useInvidous) getPlaylistFromInvidous(id) else PlaylistTuple(
                    "",
                    getFromYTDL("https://www.youtube.com/playlist?list=$id")
                )
            }
            val url = "https://youtube.googleapis.com/youtube/v3/playlistItems?part=snippet&pageToken=$nextPageToken&maxResults=50&regionCode=$countryCODE&playlistId=$id&key=$key"
            //short data
            val res = genericRequest(url)
            if (!res.has("items")) return PlaylistTuple(
                "",
                getFromYTDL("https://www.youtube.com/playlist?list=$id")
            )
            val dataArray = res.getJSONArray("items")

            //extra data
            var url2 = "https://www.googleapis.com/youtube/v3/videos?id="
            //getting all ids, for the extra data request
            for (i in 0 until dataArray.length()) {
                val element = dataArray.getJSONObject(i)
                val snippet = element.getJSONObject("snippet")
                val videoID = snippet.getJSONObject("resourceId").getString("videoId")
                url2 = "$url2$videoID,"
                snippet.put("videoID", videoID)
            }
            url2 = url2.substring(
                0,
                url2.length - 1
            ) + "&part=contentDetails&regionCode=" + countryCODE + "&key=" + key
            val extra = genericRequest(url2)
            val extraArray = extra.getJSONArray("items")
            var j = 0
            var i = 0
            while (i < extraArray.length()) {
                val element = dataArray.getJSONObject(i)
                val snippet = element.getJSONObject("snippet")
                var duration =
                    extra.getJSONArray("items").getJSONObject(j).getJSONObject("contentDetails")
                        .getString("duration")
                duration = formatDuration(duration)
                snippet.put("duration", duration)
                fixThumbnail(snippet)
                val v = createVideofromJSON(snippet)
                if (v == null || v.thumb.isEmpty()) {
                    i++
                    continue
                } else {
                    j++
                }
                v.playlistTitle = "YTDLnis"
                items.add(v)
                i++
            }
            val next = res.optString("nextPageToken")
            return PlaylistTuple(next, items)
        }catch (e: Exception){
            return PlaylistTuple(
                "",
                getFromYTDL("https://www.youtube.com/playlist?list=$id")
            )
        }
    }

    private fun getPlaylistFromInvidous(id: String): PlaylistTuple {
        val url = invidousURL + "playlists/" + id
        val res = genericRequest(url)
        if (res.length() == 0) return PlaylistTuple(
            "",
            getFromYTDL("https://www.youtube.com/playlist?list=$id")
        )
        try {
            val vids = res.getJSONArray("videos")
            for (i in 0 until vids.length()) {
                val element = vids.getJSONObject(i)
                val v = createVideoFromInvidiousJSON(element)
                if (v == null || v.thumb.isEmpty()) continue
                v.playlistTitle = res.getString("title")
                items.add(v)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return PlaylistTuple("", items)
    }

    @Throws(JSONException::class)
    fun getVideo(id: String): ResultItem? {
        try {
            init()
            if (key!!.isEmpty()) {
                val res = genericRequest(pipedURL + "streams/" + id)
                return if (res.length() == 0) getFromYTDL("https://www.youtube.com/watch?v=$id")[0] else createVideoFromPipedJSON(
                    res, id
                )
            }

            //short data
            var res =
                genericRequest("https://youtube.googleapis.com/youtube/v3/videos?part=snippet,contentDetails&id=$id&key=$key")
            if (!res.has("items")) return getFromYTDL("https://www.youtube.com/watch?v=$id")[0]
            var duration = res.getJSONArray("items").getJSONObject(0).getJSONObject("contentDetails")
                .getString("duration")
            duration = formatDuration(duration)
            res = res.getJSONArray("items").getJSONObject(0).getJSONObject("snippet")
            res.put("videoID", id)
            res.put("duration", duration)
            fixThumbnail(res)
            return createVideofromJSON(res)
        }catch (e: Exception){
            return getFromYTDL("https://www.youtube.com/watch?v=$id")[0]
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

    private fun createVideoFromInvidiousJSON(obj: JSONObject): ResultItem? {
        var video: ResultItem? = null
        try {
            val id = obj.getString("videoId")
            val title = Html.fromHtml(obj.getString("title").toString()).toString()
            val author = Html.fromHtml(obj.getString("author").toString()).toString()

            if (author.isBlank()) throw Exception()

            val duration = formatIntegerDuration(obj.getInt("lengthSeconds"), Locale.getDefault())
            val thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            val url = "https://www.youtube.com/watch?v=$id"
            val formats : ArrayList<Format> = ArrayList()
            if (obj.has("adaptiveFormats")){
                val formatsInJSON = obj.getJSONArray("adaptiveFormats")
                for (f in 0 until formatsInJSON.length()){
                    val format = formatsInJSON.getJSONObject(f)
                    if(!format.has("container")) continue
                    val formatObj = Gson().fromJson(format.toString(), Format::class.java)
                    try{
                        if (!formatObj.format_note.contains("audio", ignoreCase = true)){
                            val codecs = "\"([^\"]*)\"".toRegex().find(format.getString("type").split(";")[1])!!.value.split(",")
                            if (codecs.size > 1){
                                formatObj.vcodec = codecs[0].replace("\"", "")
                                formatObj.acodec = codecs[1].replace("\"", "")
                            }else if (codecs.size == 1){
                                formatObj.vcodec = codecs[0].replace("\"", "")
                                formatObj.acodec = "none"
                            }
                        }
                    }catch (e: Exception) {
                        e.printStackTrace()
                    }

                    formats.add(formatObj)
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
                "",
                ArrayList()
                )
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
        return video
    }

    private fun createVideoFromPipedJSON(obj: JSONObject, id: String): ResultItem? {
        var video: ResultItem? = null
        try {
            val title = Html.fromHtml(obj.getString("title").toString()).toString()
            val author = Html.fromHtml(obj.getString("uploader").toString()).toString()
            if (author.isBlank()) throw Exception()

            val duration = formatIntegerDuration(obj.getInt("duration"), Locale.getDefault())
            val thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            val url = "https://www.youtube.com/watch?v=$id"
            val formats : ArrayList<Format> = ArrayList()
            if (obj.has("audioStreams")){
                val formatsInJSON = obj.getJSONArray("audioStreams")
                for (f in 0 until formatsInJSON.length()){
                    val format = formatsInJSON.getJSONObject(f)
                    if (format.getInt("bitrate") == 0) continue
                    val formatObj = Gson().fromJson(format.toString(), Format::class.java)
                    try{
                        formatObj.acodec = format.getString("codec")
                        val streamURL = format.getString("url")
                        formatObj.format_id = streamURL.substringAfter("itag=").substringBefore("&")
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
                        val streamURL = format.getString("url")
                        formatObj.format_id = streamURL.substringAfter("itag=").substringBefore("&")
                    }catch (e: Exception) {
                        e.printStackTrace()
                    }
                    formats.add(formatObj)
                }
            }
            formats.sortBy { it.filesize }

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
                if (obj.has("hls")) obj.getString("hls") else "",
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
    fun getTrending(context: Context): ArrayList<ResultItem?> {
        init()
        items = ArrayList()
        return if (key!!.isEmpty()) {
            getTrendingFromPiped()
        } else getTrendingFromKey(context)
    }

    @Throws(JSONException::class)
    fun getTrendingFromKey(context: Context): ArrayList<ResultItem?> {
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
        val url = pipedURL + "trending?region=" + countryCODE
        val res = genericArrayRequest(url)
        try {
            for (i in 0 until res.length()) {
                val element = res.getJSONObject(i)
                if (element.getInt("duration") < 0) continue
                element.put("uploader", element.getString("uploaderName"))
                val v = createVideoFromPipedJSON(element,  element.getString("url").removePrefix("/watch?v="))
                if (v == null || v.thumb.isEmpty()) continue
                v.playlistTitle = context.getString(R.string.trendingPlaylist)
                items.add(v)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    fun getIDFromYoutubeURL(inputQuery: String) : String {
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
        init()
        val p = Pattern.compile("^(https?)://(www.)?youtu(.be)?")
        val m = p.matcher(url)
        val formatSource = sharedPreferences.getString("formats_source", "yt-dlp")
        return if(m.find() && formatSource == "piped"){
            val id = getIDFromYoutubeURL(url)
            val res = genericRequest(pipedURL + "streams/" + id)
            if (res.length() == 0) getFromYTDL(url)[0]!!
            val item = createVideoFromPipedJSON(res, id)
            item!!.formats
        }else{
            getFormatsFromYTDL(url)
        }
    }

    private fun getFormatsFromYTDL(url: String) : List<Format> {
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--print", "%(formats)s")
            request.addOption("--skip-download")
            request.addOption("-R", "1")
            request.addOption("--socket-timeout", "5")
            val cookiesFile = File(context.cacheDir, "cookies.txt")
            if (cookiesFile.exists()){
                request.addOption("--cookies", cookiesFile.absolutePath)
            }

            val proxy = sharedPreferences.getString("proxy", "")
            if (proxy!!.isNotBlank()){
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

            val formats : ArrayList<Format> = ArrayList()
            for (f in 0 until jsonArray.length()){
                val format = jsonArray.getJSONObject(f)
                if (format.has("filesize") && format.get("filesize") == "None"){
                    format.remove("filesize")
                    format.put("filesize", 0)
                }
                val formatProper = Gson().fromJson(format.toString(), Format::class.java)
                if (format.has("format_note")){
                    if (!formatProper!!.format_note.contains("audio only", true)) {
                        formatProper.format_note = format.getString("format_note")
                    }else{
                        formatProper.format_note = "${format.getString("format_note")} audio"
                    }
                }
                if (formatProper!!.format_note == "storyboard") continue
                formatProper.container = format.getString("ext")
                formats.add(formatProper)
            }

            return formats
        } catch (e: Exception) {
            Looper.prepare().run {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            }
            e.printStackTrace()
        }
        return emptyList()
    }

    fun getFormatsMultiple(urls: List<String>, progress: (progress: List<Format>) -> Unit){
        init()
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
                val cookiesFile = File(context.cacheDir, "cookies.txt")
                if (cookiesFile.exists()){
                    request.addOption("--cookies", cookiesFile.absolutePath)
                }

                val proxy = sharedPreferences.getString("proxy", "")
                if (proxy!!.isNotBlank()){
                    request.addOption("--proxy", proxy)
                }

                YoutubeDL.getInstance().execute(request){ progress, _, line ->
                    try{
                        val formats = mutableListOf<Format>()
                        val listOfStrings = JSONArray(line)
                        for (f in 0 until listOfStrings.length()){
                            val format = listOfStrings.get(f) as JSONObject
                            try{
                                if (format.getString("filesize") == "None") continue
                            }catch (e: Exception) { continue }
                            if (format.has("filesize") && format.get("filesize") == "None"){
                                format.remove("filesize")
                                format.put("filesize", 0)
                            }
                            val formatProper = Gson().fromJson(format.toString(), Format::class.java)
                            if ( !formatProper!!.format_note.contains("audio only", true)) {
                                formatProper.format_note = format.getString("format_note")
                            }else{
                                formatProper.format_note = "${format.getString("format_note")} audio"
                            }
                            if (formatProper.format_note == "storyboard") continue
                            formatProper.container = format.getString("ext")
                            formats.add(formatProper)
                        }
                        progress(formats)
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
                val res = genericRequest(pipedURL + "streams/" + id)
                val vid = createVideoFromPipedJSON(res, id)
                progress(vid!!.formats)
            }
        }

        urlsFile.delete()
    }

    fun getFromYTDL(query: String): ArrayList<ResultItem?> {
        init()
        items = ArrayList()
        val searchEngine = sharedPreferences.getString("search_engine", "ytsearch")
        try {
            val request = YoutubeDLRequest(query)
            request.addOption("--flat-playlist")
            request.addOption("-j")
            request.addOption("--skip-download")
            request.addOption("-R", "1")
            request.addOption("--socket-timeout", "5")
            if (!query.contains("http")) request.addOption("--default-search", "${searchEngine}25")

            val cookiesFile = File(context.cacheDir, "cookies.txt")
            if (cookiesFile.exists()){
                request.addOption("--cookies", cookiesFile.absolutePath)
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
                var author: String? = ""
                if (jsonObject.has("uploader")) {
                    author = jsonObject.getString("uploader")
                }
                var duration = ""
                runCatching {
                    if (jsonObject.has("duration")) {
                        duration = formatIntegerDuration(jsonObject.getInt("duration"), Locale.getDefault())
                    }
                }
                val url = jsonObject.getString("webpage_url")
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
                val formats : ArrayList<Format> = ArrayList()
                if (formatsInJSON != null) {
                    for (f in 0 until formatsInJSON.length()){
                        val format = formatsInJSON.getJSONObject(f)
                        if (format.has("filesize") && format.get("filesize") == "None"){
                            format.remove("filesize")
                            format.put("filesize", 0)
                        }
                        val formatProper = Gson().fromJson(format.toString(), Format::class.java)
                        if (format.has("format_note") && formatProper.format_note != null){
                            if (!formatProper!!.format_note.contains("audio only", true)) {
                                formatProper.format_note = format.getString("format_note")
                            }else{
                                formatProper.format_note = "${format.getString("format_note")} audio"
                            }
                        }
                        if (formatProper.format_note == "storyboard") continue
                        formatProper.container = format.getString("ext")
                        formats.add(formatProper)
                    }
                }

                val chaptersInJSON = if (jsonObject.has("formats") && jsonObject.get("formats") is JSONArray) jsonObject.getJSONArray("formats") else null
                val listType: Type = object : TypeToken<List<ChapterItem>>() {}.type
                val chapters : ArrayList<ChapterItem> = Gson().fromJson(chaptersInJSON?.toString(), listType)

                Log.e("aa", formats.toString())
                items.add(ResultItem(0,
                        url,
                        title,
                        author!!,
                        duration,
                        thumb!!,
                        website,
                        playlistTitle!!,
                        formats,
                        jsonObject.getString("urls") ?: "",
                        chapters
                    )
                )
            }
        } catch (e: Exception) {
            Looper.prepare().run {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            }
            e.printStackTrace()
        }
        Log.e("aa", items.toString())
        return items
    }

    fun getMissingInfo(url: String): ResultItem? {
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--flat-playlist")
            request.addOption("-J")
            request.addOption("--skip-download")
            request.addOption("-R", "1")
            request.addOption("--socket-timeout", "5")

            val cookiesFile = File(context.cacheDir, "cookies.txt")
            if (cookiesFile.exists()){
                request.addOption("--cookies", cookiesFile.absolutePath)
            }

            val proxy = sharedPreferences.getString("proxy", "")
            if (proxy!!.isNotBlank()){
                request.addOption("--proxy", proxy)
            }

            val youtubeDLResponse = YoutubeDL.getInstance().execute(request)
            val jsonObject = JSONObject(youtubeDLResponse.out)

            var author: String? = ""
            if (jsonObject.has("uploader")) {
                author = jsonObject.getString("uploader")
            }

            var duration = ""
            runCatching {
                if (jsonObject.has("duration")) {
                    duration = formatIntegerDuration(jsonObject.getInt("duration"), Locale.getDefault())
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
                author!!,
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
            val request = YoutubeDLRequest(url)
            request.addOption("--get-url")
            request.addOption("--print", "%(chapters)s")
            request.addOption("--skip-download")
            request.addOption("-R", "1")
            request.addOption("--socket-timeout", "5")

            val cookiesFile = File(context.cacheDir, "cookies.txt")
            if (cookiesFile.exists()){
                request.addOption("--cookies", cookiesFile.absolutePath)
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

    class PlaylistTuple internal constructor(
        var nextPageToken: String,
        var videos: ArrayList<ResultItem?>
    )

    companion object {
        private const val TAG = "API MANAGER"
        private const val invidousURL = "https://invidious.baczek.me/api/v1/"
        private const val pipedURL = "https://pipedapi.kavin.rocks/"
        private var countryCODE: String = ""
    }
}