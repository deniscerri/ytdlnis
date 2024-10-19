package com.deniscerri.ytdl.database.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.widget.Toast
import androidx.core.os.postDelayed
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
import com.deniscerri.ytdl.database.models.DownloadItemConfigureMultiple
import com.deniscerri.ytdl.database.models.DownloadItemSimple
import com.deniscerri.ytdl.util.Extensions.toListString
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.work.AlarmScheduler
import com.deniscerri.ytdl.work.DownloadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit


class DownloadRepository(private val downloadDao: DownloadDao) {
    val allDownloads : Pager<Int, DownloadItem> = Pager(
        config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 1),
        pagingSourceFactory = {downloadDao.getAllDownloads()}
    )
    val activeDownloads : Flow<List<DownloadItem>> = downloadDao.getActiveDownloads().distinctUntilChanged()
    val activePausedDownloads : Flow<List<DownloadItem>> = downloadDao.getActiveAndPausedDownloads().distinctUntilChanged()
    val pausedDownloads : Flow<List<DownloadItem>> = downloadDao.getPausedDownloads().distinctUntilChanged()
    val processingDownloads : Flow<List<DownloadItemConfigureMultiple>> = downloadDao.getProcessingDownloads().distinctUntilChanged()
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
    val activePausedDownloadsCount : Flow<Int> = downloadDao.getDownloadsCountByStatusFlow(listOf(Status.Active, Status.Paused).toListString())
    val queuedDownloadsCount : Flow<Int> = downloadDao.getDownloadsCountByStatusFlow(listOf(Status.Queued).toListString())
    val pausedDownloadsCount : Flow<Int> = downloadDao.getDownloadsCountByStatusFlow(listOf(Status.Paused).toListString())
    val cancelledDownloadsCount : Flow<Int> = downloadDao.getDownloadsCountByStatusFlow(listOf(Status.Cancelled).toListString())
    val erroredDownloadsCount : Flow<Int> = downloadDao.getDownloadsCountByStatusFlow(listOf(Status.Error).toListString())
    val savedDownloadsCount : Flow<Int> = downloadDao.getDownloadsCountByStatusFlow(listOf(Status.Saved).toListString())
    val scheduledDownloadsCount : Flow<Int> = downloadDao.getDownloadsCountByStatusFlow(listOf(Status.Scheduled).toListString())

    enum class Status {
        Active, Paused, Queued, Error, Cancelled, Saved, Processing, Scheduled, Duplicate
    }

    suspend fun insert(item: DownloadItem) : Long {
        return downloadDao.insert(item)
    }

    suspend fun insertAll(items: List<DownloadItem>) : List<Long> {
        return downloadDao.insertAll(items)
    }

    suspend fun deleteAll() {
        downloadDao.deleteAll()
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

    suspend fun updateAll(list: List<DownloadItem>) : List<DownloadItem> {
        return downloadDao.updateAll(list)
    }

    suspend fun updateWithoutUpsert(item: DownloadItem){
        kotlin.runCatching { downloadDao.updateWithoutUpsert(item) }
    }


    suspend fun setDownloadStatus(id: Long, status: Status){
        downloadDao.setStatus(id, status.toString())
    }

    fun getItemByID(id: Long) : DownloadItem {
        return downloadDao.getDownloadById(id)
    }

    fun getAllItemsByIDs(ids : List<Long>) : List<DownloadItem>{
        return downloadDao.getDownloadsByIds(ids)
    }

    fun getActiveDownloads() : List<DownloadItem> {
        return downloadDao.getActiveDownloadsList()
    }

    fun getProcessingDownloadsByUrl(url: String) : List<DownloadItem> {
        return downloadDao.getProcessingDownloadsByUrl(url)
    }

    suspend fun deleteProcessingByUrl(url: String) {
        downloadDao.deleteProcessingByUrl(url)
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

    fun getScheduledDownloads() : List<DownloadItem> {
        return downloadDao.getScheduledDownloadsList()
    }

    fun getCancelledDownloads() : List<DownloadItem> {
        return downloadDao.getCancelledDownloadsList()
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

    fun getActiveDownloadsCount() : Int {
        return downloadDao.getDownloadsCountByStatus(listOf(Status.Active).toListString())
    }

    suspend fun deleteScheduled() {
        downloadDao.deleteScheduled()
    }

    suspend fun deleteErrored(){
        val errored = getErroredDownloads()
        downloadDao.deleteErrored()
        deleteCache(errored)
    }

    suspend fun deleteQueued() {
        downloadDao.deleteQueued()
    }

    suspend fun deleteSaved(){
        downloadDao.deleteSaved()
    }

    suspend fun deleteProcessing(){
        downloadDao.deleteProcessing()
    }

    suspend fun deleteWithDuplicateStatus() {
        downloadDao.deleteWithDuplicateStatus()
    }

    suspend fun deleteAllWithIDs(ids: List<Long>){
        downloadDao.deleteAllWithIDs(ids)

    }

    suspend fun cancelActiveQueued(){
        downloadDao.cancelActiveQueued()
    }

    fun removeLogID(logID: Long){
        downloadDao.removeLogID(logID)
    }

    fun removeAllLogID(){
        downloadDao.removeAllLogID()
    }

    @SuppressLint("RestrictedApi")
    fun startDownloadWorker(queuedItems: List<DownloadItem>, context: Context, continueAfterPriorityItems: Boolean = true) : Result<String> {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val allowMeteredNetworks = sharedPreferences.getBoolean("metered_networks", true)
        val workManager = WorkManager.getInstance(context)

        val inputData = Data.Builder()
        val currentTime = System.currentTimeMillis()
        var delay = 0L
        if (queuedItems.isNotEmpty()){
            val earliestStart = queuedItems.minBy { it.downloadStartTime }
            delay = if (earliestStart.downloadStartTime != 0L){
                earliestStart.downloadStartTime.minus(currentTime)
            } else 0
            if (delay <= 60000L) delay = 0L
            inputData.putLongArray("priority_item_ids", queuedItems.map { it.id }.toLongArray())
        }

        val useAlarmForScheduling = sharedPreferences.getBoolean("use_alarm_for_scheduling", false)
        if (delay > 0L && useAlarmForScheduling) {
            AlarmScheduler(context).scheduleAt(queuedItems.minBy { it.downloadStartTime }.downloadStartTime)
            return Result.success("")
        }

        val workConstraints = Constraints.Builder()
        if (!allowMeteredNetworks) workConstraints.setRequiredNetworkType(NetworkType.UNMETERED)

        inputData.putBoolean("continue_after_priority_ids", continueAfterPriorityItems)

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .addTag("download")
            .setConstraints(workConstraints.build())
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(inputData.build())

        workManager.enqueueUniqueWork(
            System.currentTimeMillis().toString(),
            ExistingWorkPolicy.REPLACE,
            workRequest.build()
        )


        val message = StringBuilder()

        val isCurrentNetworkMetered = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
        if (!allowMeteredNetworks && isCurrentNetworkMetered){
            message.appendLine(context.getString(R.string.metered_network_download_start_info))
        }

        if (queuedItems.isNotEmpty()) {
            val first = queuedItems.first()
            if (first.downloadStartTime > 0L) {
                val date = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault()).format(queuedItems.first().downloadStartTime)
                message.appendLine(context.getString(R.string.download_rescheduled_to) + " " + date)
            }
        }

        return Result.success(message.toString())
    }

}