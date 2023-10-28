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
        for (i in items){
            resultDao.insert(i!!)
        }
    }

    suspend fun search(inputQuery: String, resetResults: Boolean) : ArrayList<ResultItem?>{
        val infoUtil = InfoUtil(context)
        if (resetResults) deleteAll()
        val res = infoUtil.search(inputQuery)
        itemCount.value = res.size
        res.forEach {
            resultDao.insert(it!!)
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
            v.forEach { it?.playlistTitle = "ytdlnis-Search" }
        }
        v.forEach {
            resultDao.insert(it!!)
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
            val tmp = infoUtil.getPlaylist(query, nextPageToken)
            items.addAll(tmp.videos)
            val tmpToken = tmp.nextPageToken
            if (tmpToken.isEmpty()) break
            if (tmpToken == nextPageToken) break
            nextPageToken = tmpToken
        } while (true)
        itemCount.value = items.size
        items.forEach {
            resultDao.insert(it!!)
        }
        return items
    }

    suspend fun getDefault(inputQuery: String, resetResults: Boolean) : ArrayList<ResultItem?> {
        val infoUtil = InfoUtil(context)
        val items = infoUtil.getFromYTDL(inputQuery)
        if (resetResults) {
            deleteAll()
            itemCount.value = items.size
        }else{
            items.forEach { it!!.playlistTitle = "ytdlnis-Search" }
        }
        items.forEach {
            resultDao.insert(it!!)
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

    fun getItemByURL(url: String): ResultItem {
        return resultDao.getResultByURL(url)
    }
}