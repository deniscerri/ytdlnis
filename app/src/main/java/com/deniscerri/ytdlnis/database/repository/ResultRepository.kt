package com.deniscerri.ytdlnis.database.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.deniscerri.ytdlnis.database.dao.CommandTemplateDao
import com.deniscerri.ytdlnis.database.dao.ResultDao
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.util.InfoUtil

class ResultRepository(private val resultDao: ResultDao, private val commandTemplateDao: CommandTemplateDao, private val context: Context) {
    private val tag: String = "ResultRepository"
    val allResults : LiveData<List<ResultItem>> = resultDao.getResults()
    var itemCount = MutableLiveData(0)

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
        itemCount.postValue(items.size)
        for (i in items){
            resultDao.insert(i!!)
        }
    }

    suspend fun search(inputQuery: String, resetResults: Boolean) : ArrayList<ResultItem?>{
        val infoUtil = InfoUtil(context)
        try{
            if (resetResults) deleteAll()
            val res = infoUtil.search(inputQuery)
            itemCount.postValue(res.size)
            res.forEach {
                resultDao.insert(it!!)
            }
            return res
        }catch (ignored: Exception){}
        return arrayListOf()
    }

    suspend fun getOne(inputQuery: String, resetResults: Boolean) : ArrayList<ResultItem?>{
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
        val infoUtil = InfoUtil(context)
        try {
            val v = infoUtil.getVideo(query!!)
            if (resetResults) deleteAll()
            itemCount.postValue(1)
            resultDao.insert(v!!)
            return arrayListOf(v)
        } catch (e: Exception) {
            Log.e(tag, e.toString())
        }
        return arrayListOf()
    }

    suspend fun getPlaylist(inputQuery: String, resetResults: Boolean) : ArrayList<ResultItem?>{
        val query = inputQuery.split("list=".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[1]
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
        itemCount.postValue(items.size)
        items.forEach {
            resultDao.insert(it!!)
        }
        return items
    }

    suspend fun getDefault(inputQuery: String, resetResults: Boolean) : ArrayList<ResultItem?> {
        val infoUtil = InfoUtil(context)
        try {
            if (resetResults) deleteAll()
            val items = infoUtil.getFromYTDL(inputQuery)
            itemCount.postValue(items.size)
            items.forEach {
                resultDao.insert(it!!)
            }
            return items
        } catch (e: Exception) {
            Log.e(tag, e.toString())
        }
        return arrayListOf()
    }


    suspend fun deleteAll(){
        itemCount.postValue(0)
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