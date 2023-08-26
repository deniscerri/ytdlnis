package com.deniscerri.ytdlnis.database.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import kotlinx.coroutines.flow.Flow


class DownloadRepository(private val downloadDao: DownloadDao) {
    val allDownloads : Pager<Int, DownloadItem> = Pager(
        config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 1),
        pagingSourceFactory = {downloadDao.getAllDownloads()}
    )
    val activeDownloads : Flow<List<DownloadItem>> = downloadDao.getActiveDownloads()
    val queuedDownloads : Pager<Int, DownloadItem> = Pager(
        config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 1),
        pagingSourceFactory = {downloadDao.getQueuedDownloads()}
    )
    val cancelledDownloads : Pager<Int, DownloadItem> = Pager(
        config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 1),
        pagingSourceFactory = {downloadDao.getCancelledDownloads()}
    )
    val erroredDownloads : Pager<Int, DownloadItem> = Pager(
        config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 1),
        pagingSourceFactory = {downloadDao.getErroredDownloads()}
    )
    val savedDownloads : Pager<Int, DownloadItem> = Pager(
        config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 1),
        pagingSourceFactory = {downloadDao.getSavedDownloads()}
    )

    val activeDownloadsCount : Flow<Int> = downloadDao.getActiveDownloadsCountFlow()

    enum class Status {
        Active, Paused, Queued, QueuedPaused, Error, Cancelled, Saved
    }

    suspend fun insert(item: DownloadItem) : Long {
        return downloadDao.insert(item)
    }

    suspend fun insertAll(items: List<DownloadItem>) : List<Long> {
        return downloadDao.insertAll(items)
    }

    suspend fun delete(id: Long){
        downloadDao.delete(id)
    }

    suspend fun update(item: DownloadItem){
        downloadDao.update(item)
    }


    suspend fun setDownloadStatus(item: DownloadItem, status: Status){
        item.status = status.toString()
        update(item)
    }

    fun getItemByID(id: Long) : DownloadItem {
        return downloadDao.getDownloadById(id)
    }

    fun getActiveDownloads() : List<DownloadItem> {
        return downloadDao.getActiveAndPausedDownloadsList()
    }

    fun getActiveAndQueuedDownloads() : List<DownloadItem> {
        return downloadDao.getActiveAndQueuedDownloadsList()
    }

    fun getQueuedDownloads() : List<DownloadItem> {
        return downloadDao.getQueuedDownloadsList()
    }

    fun getCancelledDownloads() : List<DownloadItem> {
        return downloadDao.getCancelledDownloadsList()
    }

    fun getPausedDownloads() : List<DownloadItem> {
        return downloadDao.getPausedDownloadsList()
    }

    fun getErroredDownloads() : List<DownloadItem> {
        return downloadDao.getErroredDownloadsList()
    }

    suspend fun deleteCancelled(){
        downloadDao.deleteCancelled()
    }

    suspend fun deleteErrored(){
        downloadDao.deleteErrored()
    }

    suspend fun deleteSaved(){
        downloadDao.deleteSaved()
    }

    suspend fun cancelQueued(){
        downloadDao.cancelQueued()
    }

    fun pauseDownloads(){
        downloadDao.pauseActiveAndQueued()
    }

    fun unPauseDownloads(){
        downloadDao.unPauseActiveAndQueued()
    }

    fun removeLogID(logID: Long){
        downloadDao.removeLogID(logID)
    }

    fun removeAllLogID(){
        downloadDao.removeAllLogID()
    }

}