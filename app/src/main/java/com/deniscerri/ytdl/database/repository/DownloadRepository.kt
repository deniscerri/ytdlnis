package com.deniscerri.ytdl.database.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.os.Looper
import android.widget.Toast
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.dao.DownloadDao
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.DownloadItemSimple
import com.deniscerri.ytdl.util.Extensions.toListString
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.work.DownloadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import java.util.concurrent.TimeUnit


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
    val scheduledDownloads: Pager<Int, DownloadItemSimple> = Pager(
        config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 1),
        pagingSourceFactory = {downloadDao.getScheduledDownloads()}
    )

    val activeDownloadsCount : Flow<Int> = downloadDao.getDownloadsCountByStatusFlow(listOf(Status.Active).toListString())
    val activeAndActivePausedDownloadsCount : Flow<Int> = downloadDao.getDownloadsCountByStatusFlow(listOf(Status.Active, Status.ActivePaused).toListString())
    val queuedDownloadsCount : Flow<Int> = downloadDao.getDownloadsCountByStatusFlow(listOf(Status.Queued, Status.QueuedPaused).toListString())
    val activeQueuedDownloadsCount : Flow<Int> = downloadDao.getDownloadsCountByStatusFlow(listOf(Status.Active, Status.ActivePaused, Status.Queued, Status.QueuedPaused).toListString())
    val cancelledDownloadsCount : Flow<Int> = downloadDao.getDownloadsCountByStatusFlow(listOf(Status.Cancelled).toListString())
    val erroredDownloadsCount : Flow<Int> = downloadDao.getDownloadsCountByStatusFlow(listOf(Status.Error).toListString())
    val savedDownloadsCount : Flow<Int> = downloadDao.getDownloadsCountByStatusFlow(listOf(Status.Saved).toListString())
    val pausedDownloadsCount : Flow<Int> = downloadDao.getDownloadsCountByStatusFlow(listOf(Status.ActivePaused, Status.QueuedPaused).toListString())
    val scheduledDownloadsCount : Flow<Int> = downloadDao.getDownloadsCountByStatusFlow(listOf(Status.Scheduled).toListString())

    enum class Status {
        Active, ActivePaused, Queued, QueuedPaused, Error, Cancelled, Saved, Processing, Scheduled
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

    fun getScheduledDownloadIDs() : List<Long> {
        return downloadDao.getScheduledDownloadIDs()
    }

    suspend fun deleteCancelled(){
        val cancelled = getCancelledDownloads()
        downloadDao.deleteCancelled()
        deleteCache(cancelled)
    }

    suspend fun deleteScheduled() {
        downloadDao.deleteScheduled()
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


    @SuppressLint("RestrictedApi")
    suspend fun startDownloadWorker(queuedItems: List<DownloadItem>, context: Context, inputData: Data.Builder = Data.Builder()) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val allowMeteredNetworks = sharedPreferences.getBoolean("metered_networks", true)
        val workManager = WorkManager.getInstance(context)

        val currentWork = workManager.getWorkInfosByTag("download").await()
        if (currentWork.size == 0 || currentWork.none{ it.state == WorkInfo.State.RUNNING } || (queuedItems.isNotEmpty() && queuedItems[0].downloadStartTime != 0L)){

            val currentTime = System.currentTimeMillis()
            var delay = 0L
            if (queuedItems.isNotEmpty()){
                delay = if (queuedItems[0].downloadStartTime != 0L){
                    queuedItems[0].downloadStartTime.minus(currentTime)
                } else 0
                if (delay <= 60000L) delay = 0L
            }


            val workConstraints = Constraints.Builder()
            if (!allowMeteredNetworks) workConstraints.setRequiredNetworkType(NetworkType.UNMETERED)
            else {
                workConstraints.setRequiredNetworkType(NetworkType.CONNECTED)
            }

            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .addTag("download")
                .setConstraints(workConstraints.build())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(inputData.build())

            queuedItems.forEach {
                workRequest.addTag(it.id.toString())
            }

            workManager.enqueueUniqueWork(
                System.currentTimeMillis().toString(),
                ExistingWorkPolicy.REPLACE,
                workRequest.build()
            )

        }

        val isCurrentNetworkMetered = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
        if (!allowMeteredNetworks && isCurrentNetworkMetered){
            Looper.prepare().run {
                Toast.makeText(context, context.getString(R.string.metered_network_download_start_info), Toast.LENGTH_LONG).show()
            }
        }
    }

}