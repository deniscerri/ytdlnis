package com.deniscerri.ytdl.work

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
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
import com.deniscerri.ytdl.database.dao.CommandTemplateDao
import com.deniscerri.ytdl.database.dao.DownloadDao
import com.deniscerri.ytdl.database.dao.HistoryDao
import com.deniscerri.ytdl.database.models.DownloadItem
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.security.MessageDigest
import java.util.Locale


class DownloadWorker(private val context: Context,workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    private lateinit var dao : DownloadDao
    private lateinit var notificationUtil: NotificationUtil
    private lateinit var dbManager: DBManager
    private lateinit var historyDao: HistoryDao
    private lateinit var commandTemplateDao: CommandTemplateDao
    private lateinit var logRepo: LogRepository
    private lateinit var resultRepo: ResultRepository
    private lateinit var ytdlpUtil: YTDLPUtil
    private lateinit var alarmScheduler : AlarmScheduler
    private lateinit var sharedPreferences : SharedPreferences
    private lateinit var resources: Resources
    private lateinit var workManager: WorkManager
    private lateinit var eventBus: EventBus

    private lateinit var queueState : Flow<List<DownloadItem>>
    lateinit var observeJob : Job


    private val handler = Handler(Looper.getMainLooper())

    private val openDownloadQueue: PendingIntent = PendingIntent.getActivity(
        context,
        1000000000,
        Intent(context, MainActivity::class.java).run {
            action = Intent.ACTION_VIEW
            putExtra("destination", "Queue")
        },
        PendingIntent.FLAG_IMMUTABLE
    )

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

    @SuppressLint("RestrictedApi")
    override suspend fun doWork(): Result {
        //init
        notificationUtil = NotificationUtil(App.instance)
        dbManager = DBManager.getInstance(context)
        dao = dbManager.downloadDao
        historyDao = dbManager.historyDao
        commandTemplateDao = dbManager.commandTemplateDao
        logRepo = LogRepository(dbManager.logDao)
        resultRepo = ResultRepository(dbManager.resultDao, commandTemplateDao, context)
        ytdlpUtil = YTDLPUtil(context, commandTemplateDao)
        alarmScheduler = AlarmScheduler(context)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        eventBus = EventBus.getDefault()

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
        resources = Resources(context.assets, metrics, confTmp)

        workManager = WorkManager.getInstance(context)
        if (workManager.isRunning("download")) return Result.Failure()

        setForegroundSafely()

        val priorityItemIDs = (inputData.getLongArray("priority_item_ids") ?: longArrayOf()).toMutableList()
        val continueAfterPriorityIds = inputData.getBoolean("continue_after_priority_ids", true)
        val time = System.currentTimeMillis() + 6000
        queueState = if (priorityItemIDs.isEmpty()) {
            dao.getActiveQueuedScheduledDownloadsUntil(time)
        }else {
            dao.getActiveQueuedScheduledDownloadsUntilWithPriority(time, priorityItemIDs)
        }

        val downloadJobs = mutableMapOf<Long, Job>()

        observeJob = CoroutineScope(SupervisorJob()).launch {
            queueState.collectLatest { queue ->
                val runningCount = queue.asSequence()
                    .filter { it.status == DownloadRepository.Status.Active.toString() }
                    .count()

                val queueItems = queue.asSequence()
                    .filter { it.status != DownloadRepository.Status.Active.toString() }

                val queueCount = queueItems.count()
                if (queueCount == 0 && runningCount == 0) {
                    observeJob.cancel()
                    return@collectLatest
                }

                val useScheduler = sharedPreferences.getBoolean("use_scheduler", false)
                if (useScheduler){
                    if (queueItems.none{ it.downloadStartTime > 0L } && runningCount == 0 && !alarmScheduler.isDuringTheScheduledTime()) {
                        observeJob.cancel()
                        return@collectLatest
                    }
                }

                val concurrentDownloads = sharedPreferences.getInt("concurrent_downloads", 1) - runningCount
                if (concurrentDownloads > 0) {
                    //free spots are open
                    if (priorityItemIDs.isNotEmpty()) {
                        if (!continueAfterPriorityIds && queueItems.none { priorityItemIDs.contains(it.id) }) {
                            //dont queue any more items if only required priority items
                            observeJob.cancel()
                            return@collectLatest
                        }
                    }

                    // Use supervisorScope to cancel child jobs when the downloader job is cancelled
                    supervisorScope {
                        val queuedDownloads = queueItems
                            .toList()
                            .take(concurrentDownloads)

                        val queuedIDs = queuedDownloads.map { it.id }
                        val downloadJobsToStop = downloadJobs.filter { it.key !in queuedIDs }
                        downloadJobsToStop.forEach { (download, job) ->
                            job.cancel()
                            downloadJobs.remove(download)
                        }

                        val downloadsToStart = queuedDownloads.filter { it.id !in downloadJobs }
                        downloadsToStart.forEach { downloadItem ->
                            val notification = notificationUtil.createDownloadServiceNotification(openDownloadQueue, downloadItem.title.ifEmpty { downloadItem.url })
                            notificationUtil.notify(downloadItem.id.toInt(), notification)
                            downloadJobs[downloadItem.id] = launchDownloadJob(downloadItem)
                        }
                    }
                }

            }
        }

        while (observeJob.isActive) {
            //keep alive
        }

        return Result.Success()
    }


    @OptIn(ExperimentalStdlibApi::class)
    private fun launchDownloadJob(downloadItem: DownloadItem) = CoroutineScope(Dispatchers.IO).launch {
        val writtenPath = downloadItem.format.format_note.contains("-P ")
        val noCache = writtenPath || (!sharedPreferences.getBoolean("cache_downloads", true) && File(FileUtil.formatPath(downloadItem.downloadPath)).canWrite())

        val request = ytdlpUtil.buildYoutubeDLRequest(downloadItem)

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
            downloadItem.status = DownloadRepository.Status.Active.toString()
            dao.update(downloadItem)

            CoroutineScope(Dispatchers.IO).launch {
                delay(1500)
                //update item if its incomplete
                resultRepo.updateDownloadItem(downloadItem)?.apply {
                    val status = dao.checkStatus(id)
                    if (status == DownloadRepository.Status.Active){
                        dao.updateWithoutUpsert(this)
                    }
                }
            }
        }

        runCatching {
            YoutubeDL.getInstance().destroyProcessById(downloadItem.id.toString())
            YoutubeDL.getInstance().execute(request, downloadItem.id.toString()){ progress, _, line ->
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
                        logString.append("$line\n")
                    }
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
                            FileUtil.moveFile(tempFileDir.absoluteFile,
                                context, downloadLocation, keepCache){ p ->
                                eventBus.post(WorkerProgress(p, "Moving file to ${FileUtil.formatPath(downloadLocation)}", downloadItem.id, downloadItem.logID))
                            }
                        }.filter { !it.matches("\\.(description)|(txt)\$".toRegex()) }.toMutableList()

                        if (finalPaths.isNotEmpty()){
                            eventBus.post(WorkerProgress(100, "Moved file to ${FileUtil.formatPath(downloadLocation)}", downloadItem.id, downloadItem.logID))
                        }
                    }catch (e: Exception){
                        e.printStackTrace()
                        handler.postDelayed({
                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                        }, 1000)
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
            if (isStopped) return@onFailure
            if (it is YoutubeDL.CanceledException) return@onFailure
            if (it.message?.contains("JSONDecodeError") == true) {
                val cachePath = "${FileUtil.getCachePath(context)}infojsons"
                val infoJsonName = MessageDigest.getInstance("MD5").digest(downloadItem.url.toByteArray()).toHexString()
                FileUtil.deleteFile("${cachePath}/${infoJsonName}.info.json")
            }

            if(it.message != null){
                if (logDownloads){
                    logRepo.update(it.message!!, logItem.id)
                }else{
                    logString.append("${it.message}\n")
                    logItem.content = logString.toString()
                    val logID = logRepo.insert(logItem)
                    downloadItem.logID = logID
                }
            }

            tempFileDir.delete()
            handler.postDelayed({
                Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
            }, 1000)

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

    companion object {
        const val TAG = "DownloadWorker"
    }

    class WorkerProgress(
        val progress: Int,
        val output: String,
        val downloadItemID: Long,
        val logItemID: Long?
    )

}