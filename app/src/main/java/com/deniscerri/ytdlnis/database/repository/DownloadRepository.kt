package com.deniscerri.ytdlnis.database.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.deniscerri.ytdlnis.App
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.DownloadItemSimple
import com.deniscerri.ytdlnis.util.FileUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File


class DownloadRepository(private val downloadDao: DownloadDao) {
    val allDownloads : Pager<Int, DownloadItem> = Pager(
        config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 1),
        pagingSourceFactory = {downloadDao.getAllDownloads()}
    )
    val activeDownloads : Flow<List<DownloadItem>> = downloadDao.getActiveDownloads().distinctUntilChanged()
    val processingDownloads : Flow<List<DownloadItem>> = downloadDao.getProcessingDownloads().distinctUntilChanged()
    val queuedDownloads : Pager<Int, DownloadItemSimple> = Pager(
        config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 1),
        pagingSourceFactory = {downloadDao.getQueuedDownloads()}
    )
    val cancelledDownloads : Pager<Int, DownloadItemSimple> = Pager(
        config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 1),
        pagingSourceFactory = {downloadDao.getCancelledDownloads()}
    )
    val erroredDownloads : Pager<Int, DownloadItemSimple> = Pager(
        config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 1),
        pagingSourceFactory = {downloadDao.getErroredDownloads()}
    )
    val savedDownloads : Pager<Int, DownloadItemSimple> = Pager(
        config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 1),
        pagingSourceFactory = {downloadDao.getSavedDownloads()}
    )

    val activeDownloadsCount : Flow<Int> = downloadDao.getActiveDownloadsCountFlow()

    enum class Status {
        Active, ActivePaused, PausedReQueued, Queued, QueuedPaused, Error, Cancelled, Saved, Processing
    }

    suspend fun insert(item: DownloadItem) : Long {
        return downloadDao.insert(item)
    }

    suspend fun insertAll(items: List<DownloadItem>) : List<Long> {
        return downloadDao.insertAll(items)
    }

    suspend fun delete(id: Long){
        val item = getItemByID(id)
        downloadDao.delete(id)
        deleteCache(listOf(item))
    }

    private fun deleteCache(items: List<DownloadItem>) {
        val cacheDir = FileUtil.getCachePath(App.instance)
        items.forEach {
           runCatching { File(cacheDir, it.id.toString()).deleteRecursively() }
        }
    }

    suspend fun update(item: DownloadItem){
        downloadDao.update(item)
    }

    suspend fun updateWithoutUpsert(item: DownloadItem){
        kotlin.runCatching { downloadDao.updateWithoutUpsert(item) }
    }


    suspend fun setDownloadStatus(item: DownloadItem, status: Status){
        item.status = status.toString()
        update(item)
    }

    fun getItemByID(id: Long) : DownloadItem {
        return downloadDao.getDownloadById(id)
    }

    fun getAllItemsByIDs(ids : List<Long>) : List<DownloadItem>{
        return downloadDao.getDownloadsByIds(ids)
    }

    fun getActiveDownloads() : List<DownloadItem> {
        return downloadDao.getActiveAndPausedDownloadsList()
    }

    fun getProcessingDownloads() : List<DownloadItem> {
        return downloadDao.getProcessingDownloadsList()
    }

    fun getActiveAndQueuedDownloads() : List<DownloadItem> {
        return downloadDao.getActiveAndQueuedDownloadsList()
    }

    fun getActiveAndQueuedDownloadIDs() : List<Long> {
        return downloadDao.getActiveAndQueuedDownloadIDs()
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
        val cancelled = getCancelledDownloads()
        downloadDao.deleteCancelled()
        deleteCache(cancelled)
    }

    suspend fun deleteErrored(){
        val errored = getErroredDownloads()
        downloadDao.deleteErrored()
        deleteCache(errored)
    }

    suspend fun deleteSaved(){
        downloadDao.deleteSaved()
    }

    suspend fun deleteProcessing(){
        downloadDao.deleteProcessing()
    }

    suspend fun deleteAllWithIDs(ids: List<Long>){
        downloadDao.deleteAllWithIDs(ids)

    }

    suspend fun cancelActiveQueued(){
        downloadDao.cancelActiveQueued()
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