package com.deniscerri.ytdlnis.database.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.dao.ResultDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.util.InfoUtil

class ResultRepository(private val resultDao: ResultDao) {
    val allResults : LiveData<List<ResultItem>> = resultDao.getResults()

    suspend fun insert(it: ResultItem){
        resultDao.insert(it)
    }

    suspend fun updateTrending(context: Context){
        resultDao.deleteAll();
        val infoUtil = InfoUtil(context)
        val items = infoUtil.getTrending(context)
        for (i in items){
            resultDao.insert(i!!);
        }
    }

    suspend fun deleteAll(){
        resultDao.deleteAll()
    }

    suspend fun update(item: ResultItem){
        resultDao.update(item)
    }


}