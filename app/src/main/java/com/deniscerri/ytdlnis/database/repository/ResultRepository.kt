package com.deniscerri.ytdlnis.database.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.deniscerri.ytdlnis.database.dao.CommandTemplateDao
import com.deniscerri.ytdlnis.database.dao.ResultDao
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.util.InfoUtil
import kotlinx.coroutines.flow.MutableStateFlow

class ResultRepository(private val resultDao: ResultDao, private val commandTemplateDao: CommandTemplateDao, private val context: Context) {
    private val tag: String = "ResultRepository"
    val allResults : LiveData<List<ResultItem>> = resultDao.getResults()
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
        val items = infoUtil.getTrending(context)
        itemCount.value = items.size
        for (i in items){
            resultDao.insert(i!!)
        }
    }

    suspend fun search(inputQuery: String, resetResults: Boolean) : ArrayList<ResultItem?>{
        val infoUtil = InfoUtil(context)
        try{
            if (resetResults) deleteAll()
            val res = infoUtil.search(inputQuery)
            itemCount.value = res.size
            res.forEach {
                resultDao.insert(it!!)
            }
            return res
        }catch (ignored: Exception){}
        return arrayListOf()
    }

    suspend fun getOne(inputQuery: String, resetResults: Boolean) : ArrayList<ResultItem?>{
        val infoUtil = InfoUtil(context)
        val query = infoUtil.getIDFromYoutubeURL(inputQuery)
        try {
            val v = infoUtil.getVideo(query)
            if (resetResults) {
                deleteAll()
                itemCount.value = 1
            }else{
                v!!.playlistTitle = "ytdlnis-Search"
            }
            resultDao.insert(v!!)
            return arrayListOf(v)
        } catch (e: Exception) {
            Log.e(tag, e.toString())
        }
        return arrayListOf()
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
        items.forEach {
            resultDao.insert(it!!)
        }
        itemCount.value = items.size
        return items
    }

    suspend fun getDefault(inputQuery: String, resetResults: Boolean) : ArrayList<ResultItem?> {
        val infoUtil = InfoUtil(context)
        try {
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
        } catch (e: Exception) {
            Log.e(tag, e.toString())
        }
        return arrayListOf()
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

    fun getTemplates() : List<CommandTemplate> {
        return commandTemplateDao.getAllTemplates()
    }

    fun getItemByURL(url: String): ResultItem {
        return resultDao.getResultByURL(url)
    }
}