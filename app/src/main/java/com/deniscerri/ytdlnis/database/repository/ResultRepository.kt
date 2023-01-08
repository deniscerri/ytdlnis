package com.deniscerri.ytdlnis.database.repository

import androidx.lifecycle.LiveData
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.dao.ResultDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.ResultItem

class ResultRepository(private val resultDao: ResultDao) {
    val allResults : LiveData<List<ResultItem>> = resultDao.getResults()

    suspend fun insert(items: List<ResultItem>){
        items.forEach {
            resultDao.insert(it)
        }
    }

    suspend fun deleteAll(){
        resultDao.deleteAll()
    }

    suspend fun update(item: ResultItem){
        resultDao.update(item)
    }


}