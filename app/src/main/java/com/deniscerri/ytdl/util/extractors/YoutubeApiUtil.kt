package com.deniscerri.ytdl.util.extractors

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.database.models.ResultItem
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

class YoutubeApiUtil(context: Context) {
    private var sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val countryCode = sharedPreferences.getString("locale", "")!!.ifEmpty { "US" }

    @Throws(JSONException::class)
    fun getTrending(): ArrayList<ResultItem> {
        val items = arrayListOf<ResultItem>()
        val key = sharedPreferences.getString("api_key", "")!!
        val url = "https://www.googleapis.com/youtube/v3/videos?part=snippet&chart=mostPopular&videoCategoryId=10&regionCode=${countryCode}&maxResults=25&key=$key"
        //short data
        val res = NetworkUtil.genericRequest(url)
        //extra data from the same videos
        val contentDetails =
            NetworkUtil.genericRequest("https://www.googleapis.com/youtube/v3/videos?part=contentDetails&chart=mostPopular&videoCategoryId=10&regionCode=${countryCode}&maxResults=25&key=$key")
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
            Log.e("YoutubeApiUtil", e.toString())
        }
        return video
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
}