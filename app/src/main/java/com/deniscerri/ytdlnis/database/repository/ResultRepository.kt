package com.deniscerri.ytdlnis.database.repository

import android.content.Context
import com.deniscerri.ytdlnis.database.dao.ResultDao
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.util.InfoUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class ResultRepository(private val resultDao: ResultDao, private val context: Context) {
    private val tag: String = "ResultRepository"
    val allResults : Flow<List<ResultItem>> = resultDao.getResults()
    var itemCount = MutableStateFlow(-1)

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

    suspend fun search(inputQuery: String, resetResults: Boolean) : ArrayList<ResultItem?>{
        val infoUtil = InfoUtil(context)
        if (resetResults) deleteAll()
        val res = infoUtil.search(inputQuery)
        itemCount.value = res.size
        val ids = resultDao.insertMultiple(res)
        ids.forEachIndexed { index, id ->
            res[index]?.id = id
        }
        return res
    }

    suspend fun getYoutubeVideo(inputQuery: String, resetResults: Boolean) : ArrayList<ResultItem?>{
        val infoUtil = InfoUtil(context)
        val v = infoUtil.getYoutubeVideo(inputQuery)
        if (resetResults) {
            deleteAll()
            itemCount.value = v.size
        }else{
            v.filter { it?.playlistTitle.isNullOrBlank() }.forEach { it?.playlistTitle = "ytdlnis-Search" }
        }
        val ids = resultDao.insertMultiple(v)
        ids.forEachIndexed { index, id ->
            v[index]?.id = id
        }
        return ArrayList(v)
    }

    suspend fun getPlaylist(inputQuery: String, resetResults: Boolean) : ArrayList<ResultItem?>{
        val query = inputQuery.split("list=".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[1].split("&").first()
        var nextPageToken = ""
        if (resetResults) deleteAll()
        val infoUtil = InfoUtil(context)
        val items : ArrayList<ResultItem?> = arrayListOf()
        do {
            val tmp = infoUtil.getPlaylist(query, nextPageToken, if (items.isNotEmpty()) items[0]?.playlistTitle ?: "" else "")
            val ids = resultDao.insertMultiple(tmp.videos.toList())
            ids.forEachIndexed { index, id ->
                tmp.videos[index]?.id = id
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

    suspend fun getDefault(inputQuery: String, resetResults: Boolean) : ArrayList<ResultItem?> {
        val infoUtil = InfoUtil(context)
        val items = infoUtil.getFromYTDL(inputQuery)
        if (resetResults) {
            deleteAll()
            itemCount.value = items.size
        }else{
            items.filter { it?.playlistTitle.isNullOrBlank() }.forEach { it!!.playlistTitle = "ytdlnis-Search" }
        }
        val ids = resultDao.insertMultiple(items.toList())
        ids.forEachIndexed { index, id ->
            items[index]?.id = id
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
}