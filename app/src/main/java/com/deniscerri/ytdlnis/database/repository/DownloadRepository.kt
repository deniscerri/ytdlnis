package com.deniscerri.ytdlnis.database.repository

import androidx.lifecycle.LiveData
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.models.DownloadItem

class DownloadRepository(private val downloadDao: DownloadDao) {
    val allDownloads : LiveData<List<DownloadItem>> = downloadDao.getAllDownloads()

    suspend fun insert(item: DownloadItem){
        downloadDao.insert(item)
    }

    suspend fun delete(item: DownloadItem){
        downloadDao.delete(item)
    }

    suspend fun update(item: DownloadItem){
        downloadDao.update(item)
    }

    suspend fun getQueuedDownloads() : List<DownloadItem>{
        val items = downloadDao.getQueuedDownloads()
        for (i in items){
            i.status = "Downloading"
            downloadDao.update(i)
        }
        return items
    }


}