package com.deniscerri.ytdl.database.repository

import android.content.Context
import android.util.Patterns
import com.deniscerri.ytdl.database.dao.ResultDao
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.util.Extensions.isYoutubeURL
import com.deniscerri.ytdl.util.InfoUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.regex.Pattern

class ResultRepository(private val resultDao: ResultDao, private val context: Context) {
    val YTDLNIS_SEARCH = "YTDLNIS_SEARCH"
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
            v.filter { it.playlistTitle.isBlank() }.forEach { it.playlistTitle = YTDLNIS_SEARCH }
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
            delay(1000)
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
            items.filter { it.playlistTitle.isBlank() }.forEach { it.playlistTitle = YTDLNIS_SEARCH }
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

    suspend fun deleteByUrl(url: String) {
        resultDao.deleteByUrl(url)
    }

    suspend fun deleteAll(){
        itemCount.value = 0
        resultDao.deleteAll()
    }

    suspend fun update(item: ResultItem){
        resultDao.update(item)
    }

    fun getItemByID(id: Long) : ResultItem? {
        return resultDao.getResultByID(id)
    }

    fun getItemByURL(url: String): ResultItem? {
        return resultDao.getResultByURL(url)
    }

    fun getAllByURL(url: String) : List<ResultItem> {
        return resultDao.getAllByURL(url)
    }

    fun getAllByIDs(ids: List<Long>) : List<ResultItem> {
        return resultDao.getAllByIDs(ids)
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
                getDefault(inputQuery, resetResults, addToResults, singleItem)
            }
        }

    }

    private fun getQueryType(inputQuery: String) : SourceType {
        var type = SourceType.SEARCH_QUERY
        if (inputQuery.isYoutubeURL()) {
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
                if (downloadItem.playlistTitle.isNotBlank() && downloadItem.playlistTitle != YTDLNIS_SEARCH) downloadItem.playlistTitle = info.playlistTitle
                downloadItem.duration = info.duration
                downloadItem.website = info.website
                if (downloadItem.thumb.isEmpty()) downloadItem.thumb = info.thumb
                return downloadItem
            }
        }
        return null
    }

}