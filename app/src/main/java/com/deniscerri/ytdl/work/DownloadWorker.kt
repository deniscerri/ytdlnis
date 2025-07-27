package com.deniscerri.ytdl.work

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.models.LogItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.repository.LogRepository
import com.deniscerri.ytdl.database.repository.ResultRepository
import com.deniscerri.ytdl.util.Extensions.getMediaDuration
import com.deniscerri.ytdl.util.Extensions.toStringDuration
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.extractors.YTDLPUtil
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.security.MessageDigest
import java.util.Locale


class DownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val workNotif = NotificationUtil(App.instance).createDefaultWorkerNotification()

        return ForegroundInfo(
            1000000000,
            workNotif,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }



    @OptIn(ExperimentalStdlibApi::class)
    @SuppressLint("RestrictedApi")
    override suspend fun doWork(): Result {
        val workManager = WorkManager.getInstance(context)
        if (workManager.isRunning("download") || isStopped) return Result.Failure()

        setForegroundSafely()

        val notificationUtil = NotificationUtil(App.instance)
        val dbManager = DBManager.getInstance(context)
        val dao = dbManager.downloadDao
        val historyDao = dbManager.historyDao
        val commandTemplateDao = dbManager.commandTemplateDao
        val logRepo = LogRepository(dbManager.logDao)
        val resultRepo = ResultRepository(dbManager.resultDao, commandTemplateDao, context)
        val ytdlpUtil = YTDLPUtil(context, commandTemplateDao)
        val handler = Handler(Looper.getMainLooper())
        val alarmScheduler = AlarmScheduler(context)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val time = System.currentTimeMillis() + 6000
        val priorityItemIDs = (inputData.getLongArray("priority_item_ids") ?: longArrayOf()).toMutableList()
        val continueAfterPriorityIds = inputData.getBoolean("continue_after_priority_ids", true)
        val queuedItems = if (priorityItemIDs.isEmpty()) {
            dao.getQueuedScheduledDownloadsUntil(time)
        }else {
            dao.getQueuedScheduledDownloadsUntilWithPriority(time, priorityItemIDs)
        }

        // this is needed for observe sources call, so it wont create result items
        // [removed]
        //val createResultItem = inputData.getBoolean("createResultItem", true)

        val confTmp = Configuration(context.resources.configuration)
        val locale = if (Build.VERSION.SDK_INT < 33) {
            sharedPreferences.getString("app_language", "")!!.ifEmpty { Locale.getDefault().language }
        }else{
            Locale.getDefault().language
        }.run {
            split("-")
        }.run {
            if (this.size == 1) Locale(this[0]) else Locale(this[0], this[1])
        }
        confTmp.setLocale(locale)
        val metrics = DisplayMetrics()
        val resources = Resources(context.assets, metrics, confTmp)

        val openQueueIntent = Intent(context, MainActivity::class.java)
        openQueueIntent.setAction(Intent.ACTION_VIEW)
        openQueueIntent.putExtra("destination", "Queue")
        val openDownloadQueue = PendingIntent.getActivity(
            context,
            1000000000,
            openQueueIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        queuedItems.collectLatest { items ->
            if (this@DownloadWorker.isStopped) return@collectLatest

            runningYTDLInstances.clear()
            val activeDownloads = dao.getActiveDownloadsList()
            activeDownloads.forEach {
                runningYTDLInstances.add(it.id)
            }

            val running = ArrayList(runningYTDLInstances)
            val useScheduler = sharedPreferences.getBoolean("use_scheduler", false)
            if (items.isEmpty() && running.isEmpty()) {
                WorkManager.getInstance(context).cancelWorkById(this@DownloadWorker.id)
                return@collectLatest
            }

            if (useScheduler){
                if (items.none{it.downloadStartTime > 0L} && running.isEmpty() && !alarmScheduler.isDuringTheScheduledTime()) {
                    WorkManager.getInstance(context).cancelWorkById(this@DownloadWorker.id)
                    return@collectLatest
                }
            }

            if (priorityItemIDs.isEmpty() && !continueAfterPriorityIds) {
                WorkManager.getInstance(context).cancelWorkById(this@DownloadWorker.id)
                return@collectLatest
            }

            val concurrentDownloads = sharedPreferences.getInt("concurrent_downloads", 1) - running.size
            val eligibleDownloads = if (priorityItemIDs.isNotEmpty()) {
                val tmp = priorityItemIDs.take(concurrentDownloads)
                items.filter { it.id !in running && tmp.contains(it.id) }
            }else{
                items.take(concurrentDownloads).filter {  it.id !in running }
            }

            eligibleDownloads.forEach{downloadItem ->
                val notification = notificationUtil.createDownloadServiceNotification(openDownloadQueue, downloadItem.title.ifEmpty { downloadItem.url })
                notificationUtil.notify(downloadItem.id.toInt(), notification)

                CoroutineScope(Dispatchers.IO).launch {
                    val writtenPath = downloadItem.format.format_note.contains("-P ")
                    val noCache = writtenPath || (!sharedPreferences.getBoolean("cache_downloads", true) && File(FileUtil.formatPath(downloadItem.downloadPath)).canWrite())

                    val request = ytdlpUtil.buildYoutubeDLRequest(downloadItem)
                    downloadItem.status = DownloadRepository.Status.Active.toString()
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(1500)
                        //update item if its incomplete
                        resultRepo.updateDownloadItem(downloadItem)?.apply {
                            val status = dao.checkStatus(this.id)
                            if (status == DownloadRepository.Status.Active){
                                dao.updateWithoutUpsert(this)
                            }
                        }
                    }

                    val cacheDir = FileUtil.getCachePath(context)
                    val tempFileDir = File(cacheDir, downloadItem.id.toString())
                    tempFileDir.delete()
                    tempFileDir.mkdirs()

                    val downloadLocation = downloadItem.downloadPath
                    val keepCache = sharedPreferences.getBoolean("keep_cache", false)
                    val logDownloads = sharedPreferences.getBoolean("log_downloads", false) && !downloadItem.incognito


                    val commandString = ytdlpUtil.parseYTDLRequestString(request)
                    val initialLogDetails = "Downloading:\n" +
                            "Title: ${downloadItem.title}\n" +
                            "URL: ${downloadItem.url}\n" +
                            "Type: ${downloadItem.type}\n" +
                            "Command:\n$commandString \n\n"
                    val logString = StringBuilder(initialLogDetails)
                    val logItem = LogItem(
                        0,
                        downloadItem.title.ifBlank { downloadItem.url },
                        logString.toString(),
                        downloadItem.format,
                        downloadItem.type,
                        System.currentTimeMillis(),
                    )


                    runBlocking {
                        if (logDownloads) logItem.id = logRepo.insert(logItem)
                        downloadItem.logID = logItem.id
                        dao.update(downloadItem)
                    }

                    val eventBus = EventBus.getDefault()

                    runCatching {
                        YoutubeDL.getInstance().destroyProcessById(downloadItem.id.toString())
                        YoutubeDL.getInstance().execute(request, downloadItem.id.toString(), true){ progress, _, line ->
                            eventBus.post(WorkerProgress(progress.toInt(), line, downloadItem.id, downloadItem.logID))
                            val title: String = downloadItem.title.ifEmpty { downloadItem.url }
                            notificationUtil.updateDownloadNotification(
                                downloadItem.id.toInt(),
                                line, progress.toInt(), 0, title,
                                NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID
                            )
                            CoroutineScope(Dispatchers.IO).launch {
                                if (logDownloads) {
                                    logRepo.update(line, logItem.id)
                                }
                                logString.append("$line\n")
                            }
                        }
                    }.onSuccess {
                        resultRepo.updateDownloadItem(downloadItem)?.apply {
                            dao.updateWithoutUpsert(this)
                        }
                        //val wasQuickDownloaded = resultDao.getCountInt() == 0
                        runBlocking {
                            var finalPaths = mutableListOf<String>()

                            if (noCache){
                                eventBus.post(WorkerProgress(100, "Scanning Files", downloadItem.id, downloadItem.logID))
                                val outputSequence = it.out.split("\n")
                                finalPaths =
                                    outputSequence.asSequence()
                                        .filter { it.startsWith("'/storage") }
                                        .map { it.removeSuffix("\n") }
                                        .map { it.removeSurrounding("'", "'") }
                                        .toMutableList()

                                finalPaths.addAll(
                                    outputSequence.asSequence()
                                        .filter { it.startsWith("[SplitChapters]") && it.contains("Destination: ") }
                                        .map { it.split("Destination: ")[1] }
                                        .map { it.removeSuffix("\n") }
                                        .toList()
                                )

                                finalPaths.sortBy { File(it).lastModified() }
                                finalPaths = finalPaths.distinct().toMutableList()
                                FileUtil.scanMedia(finalPaths, context)
                            }else{
                                //move file from internal to set download directory
                                eventBus.post(WorkerProgress(100, "Moving file to ${FileUtil.formatPath(downloadLocation)}", downloadItem.id, downloadItem.logID))
                                try {
                                    finalPaths = withContext(Dispatchers.IO){
                                        FileUtil.moveFile(tempFileDir.absoluteFile,context, downloadLocation, keepCache){ p ->
                                            eventBus.post(WorkerProgress(p, "Moving file to ${FileUtil.formatPath(downloadLocation)}", downloadItem.id, downloadItem.logID))
                                        }
                                    }.filter { !it.matches("\\.(description)|(txt)\$".toRegex()) }.toMutableList()

                                    if (finalPaths.isNotEmpty()){
                                        eventBus.post(WorkerProgress(100, "Moved file to ${FileUtil.formatPath(downloadLocation)}", downloadItem.id, downloadItem.logID))
                                    }
                                }catch (e: Exception){
                                    e.printStackTrace()
                                    if (e.message?.isNotBlank() == true) {
                                        handler.postDelayed({
                                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                        }, 1000)
                                    }

                                }
                            }


                            val nonMediaExtensions = mutableListOf<String>().apply {
                                addAll(context.getStringArray(R.array.thumbnail_containers_values))
                                addAll(context.getStringArray(R.array.sub_formats_values).filter { it.isNotBlank() })
                                add("description")
                                add("txt")
                            }
                            finalPaths = finalPaths.filter { path -> !nonMediaExtensions.any { path.endsWith(it) } }.toMutableList()
                            FileUtil.deleteConfigFiles(request)

                            //put download in history
                            if (!downloadItem.incognito) {
                                if (request.hasOption("--download-archive") && finalPaths.isEmpty()) {
                                    handler.postDelayed({
                                        Toast.makeText(context, resources.getString(R.string.download_already_exists), Toast.LENGTH_LONG).show()
                                    }, 100)
                                }else{
                                    if (finalPaths.isNotEmpty()) {
                                        val unixTime = System.currentTimeMillis() / 1000
                                        finalPaths.first().apply {
                                            val file = File(this)
                                            var duration = downloadItem.duration
                                            val d = file.getMediaDuration(context)
                                            if (d > 0) duration = d.toStringDuration(Locale.US)

                                            downloadItem.format.filesize = file.length()
                                            downloadItem.format.container = file.extension
                                            downloadItem.duration = duration
                                        }

                                        val historyItem = HistoryItem(0,
                                            downloadItem.url,
                                            downloadItem.title,
                                            downloadItem.author,
                                            downloadItem.duration,
                                            downloadItem.thumb,
                                            downloadItem.type,
                                            unixTime,
                                            finalPaths,
                                            downloadItem.website,
                                            downloadItem.format,
                                            downloadItem.format.filesize,
                                            downloadItem.id,
                                            commandString)
                                        historyDao.insert(historyItem)
                                    }
                                }
                            }

                            withContext(Dispatchers.Main) {
                                notificationUtil.cancelDownloadNotification(downloadItem.id.toInt())
                                notificationUtil.createDownloadFinished(
                                    downloadItem.id, downloadItem.title, downloadItem.type,  if (finalPaths.isEmpty()) null else finalPaths, resources
                                )
                            }

//                            if (wasQuickDownloaded && createResultItem){
//                                runCatching {
//                                    eventBus.post(WorkerProgress(100, "Creating Result Items", downloadItem.id))
//                                    runBlocking {
//                                        infoUtil.getFromYTDL(downloadItem.url).forEach { res ->
//                                            if (res != null) {
//                                                resultDao.insert(res)
//                                            }
//                                        }
//                                    }
//                                }
//                            }

                            dao.delete(downloadItem.id)

                            if (logDownloads){
                                logRepo.update(initialLogDetails + it.out, logItem.id, true)
                            }
                        }

                    }.onFailure {
                        FileUtil.deleteConfigFiles(request)
                        withContext(Dispatchers.Main){
                            notificationUtil.cancelDownloadNotification(downloadItem.id.toInt())
                        }
                        if (this@DownloadWorker.isStopped) return@onFailure
                        if (it is YoutubeDL.CanceledException) return@onFailure
                        if (it.message?.contains("JSONDecodeError") == true) {
                            val cachePath = "${FileUtil.getCachePath(context)}infojsons"
                            val infoJsonName = MessageDigest.getInstance("MD5").digest(downloadItem.url.toByteArray()).toHexString()
                            FileUtil.deleteFile("${cachePath}/${infoJsonName}.info.json")
                        }


                        if (logDownloads){
                            logRepo.update(it.message ?: "", logItem.id)
                        }else{
                            logString.append("${it.message ?: it.stackTraceToString()}\n")
                            logItem.content = logString.toString()
                            val logID = logRepo.insert(logItem)
                            downloadItem.logID = logID
                        }


                        tempFileDir.delete()

                        Log.e(TAG, context.getString(R.string.failed_download), it)
                        notificationUtil.cancelDownloadNotification(downloadItem.id.toInt())

                        downloadItem.status = DownloadRepository.Status.Error.toString()
                        runBlocking {
                            dao.update(downloadItem)
                        }

                        notificationUtil.createDownloadErrored(
                            downloadItem.id,
                            downloadItem.title.ifEmpty { downloadItem.url },
                            it.message,
                            downloadItem.logID,
                            resources
                        )

                        eventBus.post(WorkerProgress(100, it.toString(), downloadItem.id, downloadItem.logID))
                    }
                }
            }

            if (eligibleDownloads.isNotEmpty()){
                eligibleDownloads.forEach {
                    it.status = DownloadRepository.Status.Active.toString()
                    priorityItemIDs.remove(it.id)
                }
                dao.updateMultiple(eligibleDownloads)
            }
        }

        return Result.success()
    }



    companion object {
        val runningYTDLInstances: MutableList<Long> = mutableListOf()
        const val TAG = "DownloadWorker"
    }

    class WorkerProgress(
        val progress: Int,
        val output: String,
        val downloadItemID: Long,
        val logItemID: Long?
    )

}