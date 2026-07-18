package com.deniscerri.ytdl.work.background

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.repository.HistoryRepository
import com.deniscerri.ytdl.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdl.database.repository.ResultRepository
import com.deniscerri.ytdl.util.DownloadQueueUtil
import com.deniscerri.ytdl.util.Extensions.calculateNextTimeForObserving
import com.deniscerri.ytdl.util.Extensions.hasReachedEnd
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.ObserveAlarmScheduler
import com.deniscerri.ytdl.util.extractors.ytdlp.YTDLPUtil
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ObserveSourceWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val sourceID = inputData.getLong("id", 0)
        if (sourceID == 0L) return Result.success()

        val notificationUtil = NotificationUtil(App.Companion.instance)
        val dbManager = DBManager.Companion.getInstance(context)
        val workManager = WorkManager.Companion.getInstance(context)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val repo =
            ObserveSourcesRepository(dbManager.observeSourcesDao)
        val historyRepo = HistoryRepository(dbManager.historyDao)
        val downloadRepo = DownloadRepository(dbManager.downloadDao)
        val commandTemplateDao = dbManager.commandTemplateDao
        val resultRepository = ResultRepository(dbManager.resultDao, commandTemplateDao, context)

        val ytdlpUtil = YTDLPUtil(context, commandTemplateDao)

        val item = runCatching { repo.getByID(sourceID) }.getOrNull() ?: return Result.success()
        if (item.status == ObserveSourcesRepository.SourceStatus.STOPPED) return Result.success()

        val workerID = System.currentTimeMillis().toInt()
        val notification = notificationUtil.createObserveSourcesNotification(item.name)
        if (Build.VERSION.SDK_INT >= 33) {
            setForegroundAsync(
                ForegroundInfo(
                    workerID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            )
        }else{
            setForegroundAsync(ForegroundInfo(workerID, notification))
        }

        var finished = false
        try {

            val list = runCatching {
                resultRepository.getResultsFromSource(item.url, resetResults = false, addToResults = false, singleItem = false)
            }.onFailure {
                Log.e("observe", it.toString())
            }.getOrElse { listOf() }.reversed()

            //delete downloaded items not present in source if sync is enabled
            if (item.syncWithSource && item.alreadyProcessedLinks.isNotEmpty()){
                val processedLinks = item.alreadyProcessedLinks
                val incomingLinks = list.map { it.url }

                val linksNotPresentAnymore = processedLinks.filter { !incomingLinks.contains(it) }
                linksNotPresentAnymore.forEach {
                    val historyItems = historyRepo.getAllByURL(it)
                    historyItems.filter { h -> h.type == item.downloadItemTemplate.type }.forEach { h ->
                        historyRepo.delete(h, true)
                    }
                }
            }

            val toProcess = mutableListOf<ResultItem>()
            //filter what results need to be downloaded, ignored
            for (result in list) {
                val url = result.url

                if (item.ignoredLinks.contains(result.url)) {
                    continue
                }

                val history = historyRepo.getAllByURLAndType(result.url, item.downloadItemTemplate.type)
                val hasHistory = history.isNotEmpty()
                val hasExistingFile = history.any { h -> h.downloadPath.any { path -> FileUtil.exists(path) }}

                // First-run "only new uploads" — ONLY if truly new (no history exists)
                if (item.getOnlyNewUploads && item.runCount == 0 && !hasHistory) {
                    item.ignoredLinks.add(result.url)
                    continue
                }


                // Retry missing downloads overrides everything except ignoredLinks
                if (item.retryMissingDownloads && (!hasHistory || !hasExistingFile)) {
                    toProcess.add(result)
                    continue
                }

                if (item.alreadyProcessedLinks.contains(url)) {
                    continue
                }

                toProcess.add(result)
            }

            val downloadItems = mutableListOf<DownloadItem>()
            toProcess.forEach {
                val string = Gson().toJson(item.downloadItemTemplate, DownloadItem::class.java)
                val downloadItem = Gson().fromJson(string, DownloadItem::class.java)
                downloadItem.title = it.title
//            downloadItem.author = it.author DONT ADD IT, can conflict with playlist uploader album artist etc etc
                downloadItem.duration = it.duration
                downloadItem.website = it.website
                downloadItem.url = it.url
                downloadItem.thumb = it.thumb
                downloadItem.status = DownloadRepository.Status.Queued.toString()
                downloadItem.playlistTitle = it.playlistTitle
                downloadItem.playlistURL = it.playlistURL
                downloadItem.playlistIndex = it.playlistIndex
                downloadItem.id = 0L
                downloadItems.add(downloadItem)
            }


            if (downloadItems.isNotEmpty()){
                DownloadQueueUtil(context).enqueue(downloadItems)
                item.alreadyProcessedLinks.addAll(downloadItems.map { it.url })
            }

            item.runCount += 1
            val currentTime = System.currentTimeMillis()
            finished = item.hasReachedEnd(currentTime)
            if (finished) item.status = ObserveSourcesRepository.SourceStatus.STOPPED
            withContext(Dispatchers.IO) { repo.update(item) }

        } catch (e: Exception) {
            Log.e("observe", "Observe run failed for ${item.name}", e)
        }

        if (!finished && item.status == ObserveSourcesRepository.SourceStatus.ACTIVE) {
            ObserveAlarmScheduler(context).schedule(item)
        }
        return Result.success()

    }

}