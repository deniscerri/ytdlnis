package com.deniscerri.ytdl.util.extractors

import android.content.Context
import android.content.SharedPreferences
import android.text.Html
import android.util.Log
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.database.models.ChapterItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.util.Extensions.toStringDuration
import com.google.gson.Gson
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.delay
import org.json.JSONException
import org.json.JSONObject
import java.util.ArrayList
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

class PipedApiUtil(private val context: Context) {
    private var sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val countryCode = sharedPreferences.getString("locale", "")!!.ifEmpty { "US" }
    private val defaultPipedURL = "https://pipedapi.kavin.rocks/"
    private val pipedURL = sharedPreferences.getString("piped_instance", "")!!.ifEmpty { defaultPipedURL }.removeSuffix("/")

    fun getPipedInstances() : List<String> {
        kotlin.runCatching {
            val res = NetworkUtil.genericArrayRequest("https://piped-instances.kavin.rocks/")
            val list = mutableListOf<String>()
            for (i in 0 until res.length()) {
                val element = res.getJSONObject(i)
                list.add(element.getString("api_url"))
            }
            return list
        }
        return listOf()
    }

    fun getVideoData(url : String) : Result<List<ResultItem>> {
        val id = getIDFromYoutubeURL(url)
        val res = NetworkUtil.genericRequest("$pipedURL/streams/$id")
        if (res.length() == 0) {
            return Result.failure(Throwable())
        }

        val vid = createVideoFromPipedJSON(res, url) ?: return Result.failure(Throwable())
        return Result.success(listOf(vid))
    }

    fun getFormats(url: String) : Result<List<Format> > {
        try {
            val id = getIDFromYoutubeURL(url)
            val res = NetworkUtil.genericRequest("$pipedURL/streams/$id")
            if (res.length() == 0) {
                return Result.failure(Throwable())
            }else {
                val item = createVideoFromPipedJSON(res, "https://youtube.com/watch?v=$id", true)
                return Result.success(item!!.formats)
            }

        }catch(e: Exception) {
            println(e)
            if (e is CancellationException) throw e
            return Result.failure(e)
        }
    }

    fun getFormatsForAll(urls: List<String>, progress: (progress: ResultViewModel.MultipleFormatProgress) -> Unit) : Result<MutableList<MutableList<Format>>> {
        return kotlin.runCatching {
            val formatCollection = mutableListOf<MutableList<Format>>()
            urls.forEach { url ->
                val id = getIDFromYoutubeURL(url)
                val res = NetworkUtil.genericRequest("$pipedURL/streams/$id")
                createVideoFromPipedJSON(res, url).apply {
                    formatCollection.add(this!!.formats)
                    progress(
                        ResultViewModel.MultipleFormatProgress(url, this.formats)
                    )
                }
            }

            return Result.success(formatCollection)
        }.onFailure {
            return Result.failure(it)
        }
    }

    @Throws(JSONException::class)
    fun search(query: String): Result<ArrayList<ResultItem>> {
        val items = arrayListOf<ResultItem>()
        val data = NetworkUtil.genericRequest("$pipedURL/search?q=$query&filter=videos&region=${countryCode}")
        val dataArray = data.getJSONArray("items")
        if (dataArray.length() == 0) return Result.failure(Throwable())
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
        return Result.success(items)
    }

    @Throws(JSONException::class)
    fun searchMusic(query: String): Result<ArrayList<ResultItem>> {
        val items = arrayListOf<ResultItem>()
        val data = NetworkUtil.genericRequest("$pipedURL/search?q=$query=&filter=music_songs&region=${countryCode}")
        val dataArray = data.getJSONArray("items")
        if (dataArray.length() == 0) return Result.failure(Throwable())
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
        return Result.success(items)
    }

    fun getStreamingUrlAndChapters(url: String) : Result<Pair<List<String>, List<ChapterItem>?>> {
        val id = getIDFromYoutubeURL(url)
        val res = NetworkUtil.genericRequest("$pipedURL/streams/$id")
        if (res.length() == 0) {
            throw Exception()
        }else{
            val item = createVideoFromPipedJSON(res, url)
            if (item!!.urls.isBlank()) return Result.failure(Throwable())

            val urls = item.urls.split(",")
            val chapters = item.chapters
            return Result.success(Pair(urls, chapters))
        }
    }

    suspend fun getPlaylistData(id: String, progress: (pagedResults: MutableList<ResultItem>) -> Unit) : Result<List<ResultItem>> {
        val totalItems = mutableListOf<ResultItem>()
        val nextPageToken = ""
        var playlistName = ""

        while (true) {
            val items = mutableListOf<ResultItem>()

            var url = ""
            url = if (nextPageToken.isBlank()) "$pipedURL/playlists/$id"
            else """$pipedURL/nextpage/playlists/$id?nextpage=${
                nextPageToken.replace(
                    "&prettyPrint",
                    "%26prettyPrint"
                )
            }"""

            println(url)

            val res = NetworkUtil.genericRequest(url)
            if (!res.has("relatedStreams")) throw Exception()

            val dataArray = res.getJSONArray("relatedStreams")
            val nextpage = res.getString("nextpage")
            val isMixPlaylist = nextPageToken.isBlank() && res.getInt("videos") < 0
            if (isMixPlaylist) throw YoutubeDLException("This playlist type is unviewable.")

            for (i in 0 until dataArray.length()) {
                kotlin.runCatching {
                    val obj = dataArray.getJSONObject(i)
                    createVideoFromPipedJSON(
                        obj,
                        "https://youtube.com" + obj.getString("url")
                    )?.apply {
                        playlistTitle = playlistName.ifEmpty { runCatching { res.getString("name") }.getOrElse { "" } }
                        playlistURL = "https://www.youtube.com/playlist?list=$id"
                        items.add(this)
                        if (playlistTitle.isNotBlank() && playlistName.isNotBlank()) {
                            playlistName = playlistTitle
                        }
                    }
                }
            }

            progress(items)
            delay(1000)
            totalItems.addAll(items)
            if (nextpage == "null") break
        }

        return Result.success(totalItems)
    }


     fun getTrending(): ArrayList<ResultItem> {
        val items = arrayListOf<ResultItem>()
        val url = "$pipedURL/trending?region=${countryCode}"
        val res = NetworkUtil.genericArrayRequest(url)
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

    private fun createVideoFromPipedJSON(obj: JSONObject, url: String, ignoreFormatPreference : Boolean = false): ResultItem? {
        var video: ResultItem? = null
        try {
            val id = getIDFromYoutubeURL(url)
            val title = Html.fromHtml(obj.getString("title").toString()).toString()
            val author = try {
                Html.fromHtml(obj.getString("uploader").toString()).toString()
            }catch (e: Exception){
                Html.fromHtml(obj.getString("uploaderName").toString()).toString()
            }.removeSuffix(" - Topic")

            val duration = obj.getInt("duration").toStringDuration(Locale.US)
            val thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            val formats : ArrayList<Format> = ArrayList()

            if(sharedPreferences.getString("formats_source", "yt-dlp") == "piped" || ignoreFormatPreference){
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
                        val defaultLang = it.value.find { f -> f.format_note.contains("original", true) }
                        defaultLang?.format_id = (defaultLang?.format_id?.split("-")?.get(0) ?: "") + "-${it.value.size-1}"
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
            Log.e("PipedAPIUtil", e.toString())
        }
        return video
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



}