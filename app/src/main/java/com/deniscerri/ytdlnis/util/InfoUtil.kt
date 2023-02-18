package com.deniscerri.ytdlnis.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.google.gson.Gson
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList

class InfoUtil(context: Context) {
    private var items: ArrayList<ResultItem?>
    private var key: String? = null
    private var useInvidous = false

    init {
        try {
            val sharedPreferences =
                context.getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
            key = sharedPreferences.getString("api_key", "")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items = ArrayList()
    }

    private fun init(){
        if (countryCODE == null){
            val country = genericRequest("https://ipwho.is/")
            countryCODE = try {
                country.getString("country_code")
            } catch (e: Exception) {
                "US"
            }
        }
        if (!useInvidous){
            val res = genericRequest(invidousURL + "stats")
            Log.e("Assa", res.toString())
            useInvidous = res.length() != 0
        }
    }

    @Throws(JSONException::class)
    fun search(query: String): ArrayList<ResultItem?> {
        init()
        items = ArrayList()
        return if (key!!.isEmpty()) {
            if (useInvidous) searchFromInvidous(query) else getFromYTDL(query)
        } else searchFromKey(query)
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
    fun searchFromInvidous(query: String): ArrayList<ResultItem?> {
        val dataArray = genericArrayRequest(invidousURL + "search?q=" + query + "?type=video")
        if (dataArray.length() == 0) return getFromYTDL(query)
        for (i in 0 until dataArray.length()) {
            val element = dataArray.getJSONObject(i)
            if (!element.getString("type").equals("video")) continue
            val duration = formatIntegerDuration(element.getInt("lengthSeconds"))
            if (duration == "0:00") {
                continue
            }
            element.put("duration", duration)
            element.put(
                "thumb",
                element.getJSONArray("videoThumbnails").getJSONObject(0).getString("url")
            )
            val v = createVideofromInvidiousJSON(element)
            if (v == null || v.thumb.isEmpty()) {
                continue
            }
            items.add(v)
        }
        return items
    }

    @Throws(JSONException::class)
    fun getPlaylist(id: String, nextPageToken: String): PlaylistTuple {
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
                val v = createVideofromInvidiousJSON(element)
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
        init()
        if (key!!.isEmpty()) {
            return if (useInvidous) {
                val res = genericRequest(invidousURL + "videos/" + id)
                if (res.length() == 0) getFromYTDL("https://www.youtube.com/watch?v=$id")[0] else createVideofromInvidiousJSON(
                    res
                )
            } else {
                getFromYTDL("https://www.youtube.com/watch?v=$id")[0]
            }
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
                    ArrayList()
                )
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
        return video
    }

    private fun createVideofromInvidiousJSON(obj: JSONObject): ResultItem? {
        var video: ResultItem? = null
        try {
            val id = obj.getString("videoId")
            val title = obj.getString("title").toString()
            val author = obj.getString("author").toString()
            val duration = formatIntegerDuration(obj.getInt("lengthSeconds"))
            val thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            val url = "https://www.youtube.com/watch?v=$id"
            val formats : ArrayList<Format> = ArrayList()
            if (obj.has("adaptiveFormats")){
                val formatsInJSON = obj.getJSONArray("adaptiveFormats");
                for (f in 0 until formatsInJSON.length()){
                    val format = formatsInJSON.getJSONObject(f)
                    if(!format.has("container")) continue
                    formats.add(Gson().fromJson(format.toString(), Format::class.java))
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
                    formats
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
            conn.connectTimeout = 10000
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
            conn.connectTimeout = 10000
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
            if (useInvidous) getTrendingFromInvidous(context) else ArrayList()
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

    private fun getTrendingFromInvidous(context: Context): ArrayList<ResultItem?> {
        val url = invidousURL + "trending?type=music&region=" + countryCODE
        val res = genericArrayRequest(url)
        try {
            for (i in 0 until res.length()) {
                val element = res.getJSONObject(i)
                if (element.getString("type") != "video") continue
                val v = createVideofromInvidiousJSON(element)
                if (v == null || v.thumb.isEmpty()) continue
                v.playlistTitle = context.getString(R.string.trendingPlaylist)
                items.add(v)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    fun getFromYTDL(query: String): ArrayList<ResultItem?> {
        init()
        items = ArrayList()
        try {
            val request = YoutubeDLRequest(query)
            request.addOption("--flat-playlist")
            request.addOption("-j")
            request.addOption("--skip-download")
            request.addOption("-R", "1")
            request.addOption("--socket-timeout", "5")
            if (!query.contains("http")) request.addOption("--default-search", "ytsearch25")
            val youtubeDLResponse = YoutubeDL.getInstance().execute(request)
            val results: Array<String?> = try {
                val lineSeparator = System.getProperty("line.separator")
                youtubeDLResponse.out.split(lineSeparator!!).toTypedArray()
            } catch (e: Exception) {
                arrayOf(youtubeDLResponse.out)
            }
            val pl = JSONObject(results[0]!!)
            for (result in results) {
                val jsonObject = JSONObject(result!!)
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
                if (jsonObject.has("duration")) {
                    duration = formatIntegerDuration(jsonObject.getInt("duration"))
                }
                val url = jsonObject.getString("webpage_url")
                var thumb: String? = ""
                if (jsonObject.has("thumbnail")) {
                    thumb = jsonObject.getString("thumbnail")
                } else if (jsonObject.has("thumbnails")) {
                    val thumbs = jsonObject.getJSONArray("thumbnails")
                    thumb = thumbs.getJSONObject(thumbs.length() - 1).getString("url")
                }
                val website = if (jsonObject.has("ie_key")) jsonObject.getString("ie_key") else jsonObject.getString("extractor")
                var playlistTitle: String? = ""
                if (jsonObject.has("playlist_title")) playlistTitle = jsonObject.getString("playlist_title")
                if(playlistTitle.equals(query)) playlistTitle = ""
                val formatsInJSON = if (jsonObject.has("formats")) jsonObject.getJSONArray("formats") else null
                val formats : ArrayList<Format> = ArrayList()
                if (formatsInJSON != null) {
                    for (f in 0 until formatsInJSON.length()){
                        val format = formatsInJSON.getJSONObject(f).toString()
                        formats.add(Gson().fromJson(format, Format::class.java))
                    }
                }
                Log.e(TAG, formats.toString())
                items.add(ResultItem(0,
                        url,
                        title,
                        author!!,
                        duration,
                        thumb!!,
                        website,
                        playlistTitle!!,
                        formats
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
    }

    fun getSearchSuggestions(query: String): ArrayList<String> {
        val url = "https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&client=firefox&q=$query"
        // invidousURL + "search/suggestions?q=" + query
        val res = genericArrayRequest(url)
        if (res.length() == 0) return ArrayList()
        val suggestionList = ArrayList<String>()
        try {
            for (i in 0 until res.getJSONArray(1).length()) {
                suggestionList.add(res.getJSONArray(1).getString(i))
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

    private fun formatIntegerDuration(dur: Int): String {
        var format = String.format(
            Locale.getDefault(),
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

    class PlaylistTuple internal constructor(
        var nextPageToken: String,
        var videos: ArrayList<ResultItem?>
    )

    companion object {
        private const val TAG = "API MANAGER"
        private const val invidousURL = "https://invidious.nerdvpn.de/api/v1/"
        private var countryCODE: String? = null
    }
}