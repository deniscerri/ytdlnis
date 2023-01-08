package com.deniscerri.ytdlnis.database.repository

import androidx.lifecycle.LiveData
import androidx.room.Transaction
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.dao.HistoryDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.util.FileUtil

class HistoryRepository(private val historyDao: HistoryDao) {
    val allHistory : LiveData<List<HistoryItem>> = historyDao.getAllHistory()

    suspend fun getItem(id: Int) : HistoryItem?{
        return historyDao.getHistoryItem(id)
    }

    suspend fun insert(item: HistoryItem){
        historyDao.insert(item)
    }

    suspend fun delete(item: HistoryItem){
        historyDao.delete(item)
    }

    suspend fun deleteAll(){
        historyDao.deleteAll()
    }

    suspend fun deleteDuplicates(){
        historyDao.deleteDuplicates()
    }

    suspend fun update(item: HistoryItem){
        historyDao.update(item)
    }

    suspend fun clearDeletedHistory(){
        val fileUtil = FileUtil()
        allHistory.value?.forEach { item ->
            if (!fileUtil.exists(item.downloadPath)){
                delete(item)
            }
        }
    }

    suspend fun checkDownloaded(){

    }
}