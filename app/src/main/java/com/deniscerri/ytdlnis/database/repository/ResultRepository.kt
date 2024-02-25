package com.deniscerri.ytdlnis.database.repository

import android.content.Context
import android.util.Patterns
import com.deniscerri.ytdlnis.database.dao.ResultDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.util.InfoUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.regex.Pattern

class ResultRepository(private val resultDao: ResultDao, private val context: Context) {
    private val tag: String = "ResultRepository"
    val allResults : Flow<List<ResultItem>> = resultDao.getResults()
    var itemCount = MutableStateFlow(-1)

    enum class SourceType {
        YOUTUBE_VIDEO, YOUTUBE_PLAYLIST, SEARCH_QUERY, YT_DLP
    }

    suspend fun insert(it: ResultItem){
        resultDao.insert(it)
    }

    fun getFirstResult() : ResultItem{
        return resultDao.getFirstResult()
    }

    suspend fun updateTrending(){
        deleteAll()
        val infoUtil = InfoUtil(context)
        val items = infoUtil.getTrending()
        itemCount.value = items.size
        resultDao.insertMultiple(items)
    }

    suspend fun search(inputQuery: String, resetResults: Boolean, addToResults: Boolean) : ArrayList<ResultItem>{
        val infoUtil = InfoUtil(context)
        if (resetResults) deleteAll()
        val res = infoUtil.search(inputQuery)
        itemCount.value = res.size
        if (addToResults){
            val ids = resultDao.insertMultiple(res)
            ids.forEachIndexed { index, id ->
                res[index].id = id
            }
        }
        return res
    }

    suspend fun getYoutubeVideo(inputQuery: String, resetResults: Boolean, addToResults: Boolean) : ArrayList<ResultItem>{
        val infoUtil = InfoUtil(context)
        val v = infoUtil.getYoutubeVideo(inputQuery) ?: return arrayListOf()
        if (resetResults) {
            deleteAll()
            itemCount.value = v.size
        }else{
            v.filter { it.playlistTitle.isBlank() }.forEach { it.playlistTitle = "ytdlnis-Search" }
        }
        if (addToResults){
            val ids = resultDao.insertMultiple(v)
            ids.forEachIndexed { index, id ->
                v[index].id = id
            }
        }
        return ArrayList(v)
    }

    suspend fun getPlaylist(inputQuery: String, resetResults: Boolean, addToResults: Boolean) : ArrayList<ResultItem>{
        val query = inputQuery.split("list=".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[1].split("&").first()
        var nextPageToken = ""
        if (resetResults) deleteAll()
        val infoUtil = InfoUtil(context)
        val items = arrayListOf<ResultItem>()
        do {
            val tmp = infoUtil.getPlaylist(query, nextPageToken, if (items.isNotEmpty()) items[0].playlistTitle else "")
            if (addToResults){
                val ids = resultDao.insertMultiple(tmp.videos.toList())
                ids.forEachIndexed { index, id ->
                    tmp.videos[index].id = id
                }
            }
            items.addAll(tmp.videos)
            val tmpToken = tmp.nextPageToken
            if (tmpToken.isEmpty()) break
            if (tmpToken == nextPageToken) break
            nextPageToken = tmpToken
        } while (true)
        itemCount.value = items.size
        return items
    }

    suspend fun getDefault(inputQuery: String, resetResults: Boolean, addToResults: Boolean, singleItem: Boolean = false) : ArrayList<ResultItem> {
        val infoUtil = InfoUtil(context)
        val items = infoUtil.getFromYTDL(inputQuery, singleItem)
        if (resetResults) {
            deleteAll()
            itemCount.value = items.size
        }else{
            items.filter { it.playlistTitle.isNullOrBlank() }.forEach { it.playlistTitle = "ytdlnis-Search" }
        }

        if (addToResults){
            val ids = resultDao.insertMultiple(items.toList())
            ids.forEachIndexed { index, id ->
                items[index].id = id
            }
        }

        return items
    }

    suspend fun delete(item: ResultItem){
        resultDao.delete(item.id)
    }

    suspend fun deleteAll(){
        itemCount.value = 0
        resultDao.deleteAll()
    }

    suspend fun update(item: ResultItem){
        resultDao.update(item)
    }

    fun getItemByURL(url: String): ResultItem? {
        return resultDao.getResultByURL(url)
    }

    suspend fun getResultsFromSource(inputQuery: String, resetResults: Boolean, addToResults: Boolean = true, singleItem: Boolean = false) : ArrayList<ResultItem> {
        return when(getQueryType(inputQuery)){
            SourceType.YOUTUBE_VIDEO -> {
                getYoutubeVideo(inputQuery, resetResults, addToResults)
            }
            SourceType.YOUTUBE_PLAYLIST -> {
                if (singleItem){
                    getDefault(inputQuery, resetResults, addToResults, true)
                }else{
                    getPlaylist(inputQuery, resetResults, addToResults)
                }
            }
            SourceType.SEARCH_QUERY -> {
                search(inputQuery, resetResults, addToResults)
            }
            SourceType.YT_DLP -> {
                getDefault(inputQuery, resetResults, addToResults)
            }
        }

    }

    fun getQueryType(inputQuery: String) : SourceType {
        var type = SourceType.SEARCH_QUERY
        val p = Pattern.compile("(^(https?)://(www.)?youtu(.be)?)|(^(https?)://(www.)?piped.video)")
        val m = p.matcher(inputQuery)
        if (m.find()) {
            type = SourceType.YOUTUBE_VIDEO
            if (inputQuery.contains("playlist?list=")) {
                type = SourceType.YOUTUBE_PLAYLIST
            }
        } else if (Patterns.WEB_URL.matcher(inputQuery).matches()) {
            type = SourceType.YT_DLP
        }
        return type
    }

    suspend fun updateDownloadItem(
        downloadItem: DownloadItem
    ) : DownloadItem? {
        if (downloadItem.title.isEmpty() || downloadItem.author.isEmpty() || downloadItem.thumb.isEmpty()){
            runCatching {
                val info = getResultsFromSource(downloadItem.url, resetResults = false, addToResults = false, singleItem = true).first()
                if (downloadItem.title.isEmpty()) downloadItem.title = info.title
                if (downloadItem.author.isEmpty()) downloadItem.author = info.author
                downloadItem.duration = info.duration
                downloadItem.website = info.website
                if (downloadItem.thumb.isEmpty()) downloadItem.thumb = info.thumb
                return downloadItem
            }
        }
        return null
    }

}