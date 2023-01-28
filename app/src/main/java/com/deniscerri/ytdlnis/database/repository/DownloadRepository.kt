package com.deniscerri.ytdlnis.database.repository

import android.util.Log
import androidx.lifecycle.LiveData
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.models.DownloadItem

class DownloadRepository(private val downloadDao: DownloadDao) {
    val allDownloads : LiveData<List<DownloadItem>> = downloadDao.getAllDownloads()
    val activeDownloads : LiveData<List<DownloadItem>> = downloadDao.getActiveDownloads()
    val queuedDownloads : LiveData<List<DownloadItem>> = downloadDao.getQueuedDownloads()
    val processingDownloads : LiveData<List<DownloadItem>> = downloadDao.getProcessingDownloads()

    enum class status {
        Active, Queued, Errored, Processing
    }

    suspend fun insert(item: DownloadItem) : Long {
        return downloadDao.insert(item)
    }

    suspend fun delete(item: DownloadItem){
        downloadDao.delete(item.id)
    }

    suspend fun update(item: DownloadItem){
        downloadDao.update(item)
    }

    fun getDownloadByWorkID(workID: Int) : DownloadItem{
        return downloadDao.getDownloadByWorkId(workID)
    }


    suspend fun setDownloadStatus(item: DownloadItem, status: status){
        item.status = status.toString()
        update(item);
    }

    fun getItemByID(id: Long) : DownloadItem {
        return downloadDao.getDownloadById(id)
    }

    suspend fun deleteProcessing(){
        downloadDao.deleteProcessing()
    }

    suspend fun deleteSingleProcessing(item: DownloadItem){
        downloadDao.deleteSingleProcessing(item.id)
    }

    suspend fun queueAllProcessing(){
        downloadDao.queueAllProcessing()
    }
}