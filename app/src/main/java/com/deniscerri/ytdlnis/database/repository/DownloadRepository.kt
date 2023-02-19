package com.deniscerri.ytdlnis.database.repository

import androidx.lifecycle.LiveData
import com.deniscerri.ytdlnis.database.Converters
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.ResultItem

class DownloadRepository(private val downloadDao: DownloadDao) {
    val allDownloads : LiveData<List<DownloadItem>> = downloadDao.getAllDownloads()
    val activeDownloads : LiveData<List<DownloadItem>> = downloadDao.getActiveDownloads()
    val queuedDownloads : LiveData<List<DownloadItem>> = downloadDao.getQueuedDownloads()
    val processingDownloads : LiveData<List<DownloadItem>> = downloadDao.getProcessingDownloads()

    enum class Status {
        Active, Queued, Error, Processing, Cancelled
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


    suspend fun setDownloadStatus(item: DownloadItem, status: Status){
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

    fun checkIfPresentForProcessing(item: ResultItem): DownloadItem{
        return downloadDao.checkIfErrorOrCancelled(item.url)
    }

    fun checkIfReDownloadingErroredOrCancelled(item: DownloadItem) : Long {
        val converters = Converters()
        val format = converters.formatToString(item.format)
        return try {
            val i = downloadDao.getUnfinishedByURLAndFormat(item.url, format)
            i.id
        }catch (e: Exception){
            0L
        }
    }

}