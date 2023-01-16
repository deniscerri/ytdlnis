package com.deniscerri.ytdlnis.database.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.Transaction
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.dao.HistoryDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.util.FileUtil
import java.io.File

class HistoryRepository(private val historyDao: HistoryDao) {
    val items : LiveData<List<HistoryItem>> = historyDao.getAllHistory()
    enum class HistorySort{
        DESC, ASC
    }

    suspend fun getItem(id: Int) : HistoryItem {
        return historyDao.getHistoryItem(id)
    }

    fun getFiltered(query : String, format : String, site : String, sort: HistorySort) : List<HistoryItem> {
        return historyDao.getHistory(query, format, site, sort.toString())
    }

    suspend fun insert(item: HistoryItem){
        historyDao.insert(item)
    }

    suspend fun delete(item: HistoryItem, deleteFile: Boolean){
        historyDao.delete(item.id)
        if (deleteFile){
            val fileUtil = FileUtil()
            fileUtil.deleteFile(item.downloadPath)
        }
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
        items.value?.forEach { item ->
            if (!fileUtil.exists(item.downloadPath)){
                historyDao.delete(item.id)
            }
        }
    }

    suspend fun checkDownloaded(){

    }
}