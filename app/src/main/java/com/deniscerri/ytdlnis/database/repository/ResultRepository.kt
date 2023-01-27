package com.deniscerri.ytdlnis.database.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import com.deniscerri.ytdlnis.database.dao.CommandTemplateDao
import com.deniscerri.ytdlnis.database.dao.ResultDao
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.util.InfoUtil

class ResultRepository(private val resultDao: ResultDao, private val commandTemplateDao: CommandTemplateDao, private val context: Context) {
    private val tag: String = "ResultRepository"
    val allResults : LiveData<List<ResultItem>> = resultDao.getResults()

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
        for (i in items){
            resultDao.insert(i!!)
        }
    }

    suspend fun search(inputQuery: String, resetResults: Boolean){
        val infoUtil = InfoUtil(context)
        try{
            if (resetResults) deleteAll()
            val res = infoUtil.search(inputQuery)
            res.forEach {
                resultDao.insert(it!!)
            }
        }catch (ignored: Exception){}
    }

    suspend fun getOne(inputQuery: String, resetResults: Boolean){
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
            resultDao.insert(v!!)
        } catch (e: Exception) {
            Log.e(tag, e.toString())
        }
    }

    suspend fun getPlaylist(inputQuery: String, resetResults: Boolean){
        val query = inputQuery.split("list=".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[1]
        var nextPageToken = ""
        if (resetResults) deleteAll()
        val infoUtil = InfoUtil(context)
        do {
            val tmp = infoUtil.getPlaylist(query, nextPageToken)
            val tmpVids = tmp.videos
            val tmpToken = tmp.nextPageToken
            tmpVids.forEach {
                resultDao.insert(it!!)
            }
            if (tmpToken.isEmpty()) break
            if (tmpToken == nextPageToken) break
            nextPageToken = tmpToken
        } while (true)
    }

    suspend fun getDefault(inputQuery: String, resetResults: Boolean){
        val infoUtil = InfoUtil(context)
        try {
            if (resetResults) deleteAll()
            val items = infoUtil.getFromYTDL(inputQuery)
            items.forEach {
                resultDao.insert(it!!)
            }
        } catch (e: Exception) {
            Log.e(tag, e.toString())
        }
    }


    suspend fun deleteAll(){
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