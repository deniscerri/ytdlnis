package com.deniscerri.ytdlnis.database.repository

import androidx.lifecycle.LiveData
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.models.DownloadItem

class DownloadRepository(private val downloadDao: DownloadDao) {
    val allDownloads : LiveData<List<DownloadItem>> = downloadDao.getAllDownloads()
    val activeDownloads : LiveData<List<DownloadItem>> = downloadDao.getActiveDownloads()

    suspend fun insert(item: DownloadItem){
        downloadDao.insert(item)
    }

    suspend fun delete(item: DownloadItem){
        downloadDao.delete(item)
    }

    suspend fun update(item: DownloadItem){
        downloadDao.update(item)
    }

    fun getDownloadsByWorkID(workID: Int) : List<DownloadItem>{
        return downloadDao.getDownloadsByWorkId(workID)
    }


}