package com.deniscerri.ytdl.util.extractors.newpipe

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.database.models.ChapterItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.util.Extensions.toStringDuration
import com.google.gson.Gson
import kotlinx.serialization.Serializer
import okhttp3.OkHttpClient
import org.json.JSONException
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItemExtractor
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.kiosk.KioskList
import org.schabi.newpipe.extractor.linkhandler.LinkHandler
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeMusicSearchExtractor
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeSearchExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.utils.ExtractorHelper
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

class NewPipeUtil(context: Context) {
    private var sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val countryCode = sharedPreferences.getString("locale", "")!!.ifEmpty { "US" }
    private val language = sharedPreferences.getString("app_language", "")!!.ifEmpty { "en" }
    init {
        NewPipe.init(NewPipeDownloaderImpl(OkHttpClient.Builder()), Localization(language, countryCode))
    }

    fun getVideoData(url : String) : Result<List<ResultItem>> {
        try {
            val streamInfo = StreamInfo.getInfo(NewPipe.getService(ServiceList.YouTube.serviceId), url)
            val vid = createVideoFromStream(streamInfo, url) ?: return Result.failure(Throwable())
            return Result.success(listOf(vid))
        }catch (e: Exception) {
            return Result.failure(e)
        }
    }

    fun getFormats(url: String) : Result<List<Format> > {
        try {
            val streamInfo = StreamInfo.getInfo(NewPipe.getService(ServiceList.YouTube.serviceId), url)
            val vid = createVideoFromStream(streamInfo, url)
            return Result.success(vid!!.formats)
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
                val streamInfo = StreamInfo.getInfo(NewPipe.getService(ServiceList.YouTube.serviceId), url)
                createVideoFromStream(streamInfo, url).apply {
                    formatCollection.add(this!!.formats)
                    progress(ResultViewModel.MultipleFormatProgress(url, this.formats))
                }
            }
            return Result.success(formatCollection)
        }.onFailure {
            return Result.failure(it)
        }
    }

    @Throws(JSONException::class)
    fun search(query: String): Result<ArrayList<ResultItem>> {
        try {
            val items = arrayListOf<ResultItem>()
            val res = SearchInfo.getInfo(NewPipe.getService(ServiceList.YouTube.serviceId),
                NewPipe.getService(ServiceList.YouTube.serviceId)
                    .searchQHFactory
                    .fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), ""))

            if (res.relatedItems.isEmpty()) return Result.failure(Throwable())

            for (i in 0 until res.relatedItems.size) {
                val element = res.relatedItems[i]
                if (element is StreamInfoItem) {
                    if (element.duration <= 0) continue
                    val v = createVideoFromStreamInfoItem(element, element.url) ?: continue
                    items.add(v)
                }
            }
            return Result.success(items)

        }catch (e: Exception){
            return Result.failure(e)
        }
    }

    @Throws(JSONException::class)
    fun searchMusic(query: String): Result<ArrayList<ResultItem>> {
        try {
            val items = arrayListOf<ResultItem>()
            val res = SearchInfo.getInfo(NewPipe.getService(ServiceList.YouTube.serviceId),
                NewPipe.getService(ServiceList.YouTube.serviceId)
                    .searchQHFactory
                    .fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), ""))
            if (res.relatedItems.isEmpty()) return Result.failure(Throwable())

            for (i in 0 until res.relatedItems.size) {
                val element = res.relatedItems[i]
                if (element is StreamInfoItem) {
                    if (element.duration <= 0) continue
                    val v = createVideoFromStreamInfoItem(element, element.url) ?: continue
                    items.add(v)
                }
            }
            return Result.success(items)

        }catch (e: Exception){
            return Result.failure(e)
        }
    }

    fun getStreamingUrlAndChapters(url: String) : Result<Pair<List<String>, List<ChapterItem>?>> {
        try {
            val streamInfo = StreamInfo.getInfo(NewPipe.getService(ServiceList.YouTube.serviceId), url)
            val item = createVideoFromStream(streamInfo, url)
            if (item!!.urls.isBlank()) return Result.failure(Throwable())
            val urls = item.urls.split(",")
            val chapters = item.chapters
            return Result.success(Pair(urls, chapters))
        }catch (e: Exception) {
            return Result.failure(e)
        }
    }

    fun getChannelData(url: String, progress: (pagedResults: MutableList<ResultItem>) -> Unit) : Result<List<ResultItem>> {
        try {
            val req = ChannelInfo.getInfo(ServiceList.YouTube, url)
            println(Gson().toJson(req))
            val items = mutableListOf<ResultItem>()
            for (tab in req.tabs) {
                if (listOf("videos", "shorts", "livestreams").contains(tab.contentFilters[0])) {
                    val tabInfo = ChannelTabInfo.getInfo(ServiceList.YouTube, tab)
                    val tmp = getChannelTabData(tab, tabInfo, req.name, "${url}/${tabInfo.url.split("/").last()}") {
                        progress(it)
                    }
                    if (tmp.isFailure) continue
                    else items.addAll(tmp.getOrNull()!!)
                }
            }
            return Result.success(items)
        }catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun getChannelTabData(linkHandler: ListLinkHandler, tabInfo: ChannelTabInfo, channelName: String, playlistURL: String, progress: (pagedResults: MutableList<ResultItem>) -> Unit) : Result<List<ResultItem>> {
        try {
            val totalItems = mutableListOf<ResultItem>()
            var nextPage : Page? = null
            var playlistName = ""

            while (true) {
                val items = mutableListOf<ResultItem>()
                val req = if (nextPage == null) {
                    if (tabInfo.hasNextPage()) {
                        nextPage = tabInfo.nextPage
                    }
                    playlistName = "$channelName - ${tabInfo.name}"
                    tabInfo.relatedItems.toList()
                } else {
                    val tmp = ChannelTabInfo.getMoreItems(ServiceList.YouTube, linkHandler, nextPage)
                    nextPage = if (tmp.hasNextPage()) tmp.nextPage else null
                    tmp.items.toList()
                }

                if (req.isEmpty()) return Result.failure(Throwable())

                for (element in req) {
                    if (element is StreamInfoItem) {
                        if (element.duration <= 0) continue
                        val v = createVideoFromStreamInfoItem(element, element.url) ?: continue
                        v.apply {
                            playlistTitle = playlistName
                            this.playlistURL = playlistURL
                            items.add(this)
                        }
                    }
                }

                totalItems.addAll(items)
                progress(items)
                if (nextPage == null || items.isEmpty()) break
            }

            return Result.success(totalItems)
        }catch (e: Exception) {
            return Result.failure(e)
        }
    }

    fun getPlaylistData(playlistURL: String, progress: (pagedResults: MutableList<ResultItem>) -> Unit) : Result<List<ResultItem>> {
        try {
            val totalItems = mutableListOf<ResultItem>()
            var nextPage : Page? = null
            var playlistName = ""

            while (true) {
                val items = mutableListOf<ResultItem>()
                val req = if (nextPage == null) {
                    val tmp = PlaylistInfo.getInfo(ServiceList.YouTube, playlistURL)
                    if (tmp.hasNextPage()) {
                        nextPage = tmp.nextPage
                    }
                    playlistName = tmp.name
                    tmp.relatedItems.toList()
                } else {
                    val tmp = PlaylistInfo.getMoreItems(ServiceList.YouTube, playlistURL, nextPage)
                    nextPage = if (tmp.hasNextPage()) tmp.nextPage else null
                    tmp.items.toList()
                }

                if (req.isEmpty()) return Result.failure(Throwable())

                for (element in req) {
                    if (element is StreamInfoItem) {
                        if (element.duration <= 0) continue
                        val v = createVideoFromStreamInfoItem(element, element.url) ?: continue
                        v.apply {
                            playlistTitle = playlistName
                            this.playlistURL = playlistURL
                            items.add(this)
                        }
                    }
                }

                totalItems.addAll(items)
                progress(items)
                if (nextPage == null || items.isEmpty()) break
            }

            return Result.success(totalItems)
        }catch (e: Exception) {
            return Result.failure(e)
        }
    }


     fun getTrending(): ArrayList<ResultItem> {
        try {
            val items = arrayListOf<ResultItem>()
            val info = KioskInfo.getInfo(NewPipe.getService(ServiceList.YouTube.serviceId), "https://www.youtube.com/feed/trending")
            if (info.relatedItems.isEmpty()) return arrayListOf()

            for (i in 0 until info.relatedItems.size) {
                val element = info.relatedItems[i]
                if (element is StreamInfoItem) {
                    if (element.duration <= 0) continue
                    val v = createVideoFromStreamInfoItem(element, element.url) ?: continue
                    items.add(v)
                }
            }

            return items
        }catch (err: Exception) {
            return arrayListOf()
        }
    }

    private fun createVideoFromStreamInfoItem(stream: StreamInfoItem, url: String) : ResultItem? {
        var video: ResultItem? = null
        try {
            val id = getIDFromYoutubeURL(url)
            val title = stream.name
            val author = stream.uploaderName.removeSuffix(" - Topic")
            val duration = stream.duration.toInt().toStringDuration(Locale.US)
            val thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

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
            Log.e("NewPipeUtil", e.toString())
        }
        return video
    }

    private fun createVideoFromStream(stream: StreamInfo, url: String, ignoreFormatPreference : Boolean = false): ResultItem? {
        var video: ResultItem? = null
        try {
            val id = getIDFromYoutubeURL(url)
            val title = stream.name
            val author = stream.uploaderName.removeSuffix(" - Topic")
            val duration = stream.duration.toInt().toStringDuration(Locale.US)
            val thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            val formats : ArrayList<Format> = ArrayList()


            if(sharedPreferences.getString("formats_source", "yt-dlp") == "piped" || ignoreFormatPreference){
                if (stream.audioStreams.isNotEmpty()){
                    for (f in 0 until stream.audioStreams.size){
                        val it = stream.audioStreams[f]
                        if (it.bitrate == 0) continue

                        val formatObj = Format(
                            format_id = it.itag.toString(),
                            container = it.format!!.name,
                            acodec = it.codec,
                            filesize = it.itagItem!!.contentLength,
                            format_note = (it.audioTrackName ?: (it.itagItem?.getResolutionString() ?: ((it.bitrate / 1000).toString() + "k"))) + " Audio",
                            lang = it.audioLocale?.language,
                            asr = it.itagItem!!.sampleRate.toString(),
                            url = it.content,
                            tbr = (it.bitrate / 1000).toString() + "k"
                        )

                        formats.add(formatObj)
                    }
                }

                if (stream.videoStreams.isNotEmpty()){
                    for (f in 0 until stream.videoStreams.size){
                        val it = stream.videoStreams[f]
                        if (it.bitrate == 0) continue

                        val formatObj = Format(
                            format_id = it.itag.toString(),
                            container = it.format!!.name,
                            vcodec = it.codec,
                            format_note = it.itagItem!!.getResolutionString() ?: it.quality,
                            filesize = it.itagItem!!.contentLength,
                            url = it.content,
                            tbr = (it.bitrate / 1000).toString() + "k"
                        )
                        formats.add(formatObj)
                    }
                }

                if (stream.videoOnlyStreams.isNotEmpty()){
                    for (f in 0 until stream.videoOnlyStreams.size){
                        val it = stream.videoOnlyStreams[f]
                        if (it.bitrate == 0) continue

                        val formatObj = Format(
                            format_id = it.itag.toString(),
                            container = it.format!!.name,
                            vcodec = it.codec,
                            format_note = it.itagItem!!.getResolutionString() ?: it.quality,
                            filesize = it.itagItem!!.contentLength,
                            url = it.content,
                            tbr = (it.bitrate / 1000).toString() + "k"
                        )
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
            if (stream.streamSegments.isNotEmpty()){
                for (c in 0 until stream.streamSegments.size){
                    val chapter = stream.streamSegments[c]
                    val end = if (c == stream.streamSegments.size - 1) stream.duration.toInt() else stream.streamSegments[c+1].startTimeSeconds
                    val item = ChapterItem(chapter.startTimeSeconds.toLong(), end.toLong(), chapter.title)
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
                if (stream.hlsUrl.isNotBlank() && stream.hlsUrl != "null") stream.hlsUrl else "",
                chapters
            )
        } catch (e: Exception) {
            Log.e("NewPipeUtil", e.toString())
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