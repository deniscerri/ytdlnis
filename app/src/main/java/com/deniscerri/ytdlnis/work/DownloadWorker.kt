package com.deniscerri.ytdlnis.work

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.navigation.NavDeepLinkBuilder
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import com.deniscerri.ytdlnis.App
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.dao.ResultDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.models.LogItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.repository.LogRepository
import com.deniscerri.ytdlnis.util.Extensions.getMediaDuration
import com.deniscerri.ytdlnis.util.Extensions.toStringDuration
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.random.Random


class DownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    @SuppressLint("RestrictedApi")
    override suspend fun doWork(): Result {
        if (isStopped) return Result.success()

        val notificationUtil = NotificationUtil(App.instance)
        val infoUtil = InfoUtil(context)
        val dbManager = DBManager.getInstance(context)
        val dao = dbManager.downloadDao
        val historyDao = dbManager.historyDao
        val resultDao = dbManager.resultDao
        val logRepo = LogRepository(dbManager.logDao)
        val handler = Handler(Looper.getMainLooper())
        val alarmScheduler = AlarmScheduler(context)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val time = System.currentTimeMillis() + 6000
        val queuedItems = dao.getQueuedDownloadsThatAreNotScheduledChunked(time)
        val currentWork = WorkManager.getInstance(context).getWorkInfosByTag("download").await()
        if (currentWork.count{it.state == WorkInfo.State.RUNNING} > 1) return Result.success()

        val confTmp = Configuration(context.resources.configuration)
        val currLang = sharedPreferences.getString("app_language", "")!!.ifEmpty { Locale.getDefault().language }.split("-")
        confTmp.setLocale(if (currLang.size == 1) Locale(currLang[0]) else Locale(currLang[0], currLang[1]))
        val metrics = DisplayMetrics()
        val resources = Resources(context.assets, metrics, confTmp)

        val pendingIntent = NavDeepLinkBuilder(context)
            .setGraph(R.navigation.nav_graph)
            .setDestination(R.id.downloadQueueMainFragment)
            .createPendingIntent()

        val workNotif = notificationUtil.createDefaultWorkerNotification()
        val workNotifID = Random.nextInt(900000000, 1000000000)
        val foregroundInfo = ForegroundInfo(workNotifID, workNotif)
        setForegroundAsync(foregroundInfo)

        queuedItems.collect { items ->
            runningYTDLInstances.clear()
            dao.getActiveDownloadsList().forEach {
                runningYTDLInstances.add(it.id)
            }

            val running = ArrayList(runningYTDLInstances)
            val useScheduler = sharedPreferences.getBoolean("use_scheduler", false)
            if (items.isEmpty() && running.isEmpty()) WorkManager.getInstance(context).cancelWorkById(this@DownloadWorker.id)

            if (useScheduler){
                if (items.none{it.downloadStartTime > 0L} && running.isEmpty() && !alarmScheduler.isDuringTheScheduledTime()) {
                    WorkManager.getInstance(context).cancelWorkById(this@DownloadWorker.id)
                }
            }
            val concurrentDownloads = sharedPreferences.getInt("concurrent_downloads", 1) - running.size
            val eligibleDownloads = items.take(if (concurrentDownloads < 0) 0 else concurrentDownloads).filter {  it.id !in running }

            eligibleDownloads.forEach{downloadItem ->
                val notification = notificationUtil.createDownloadServiceNotification(pendingIntent, downloadItem.title.ifEmpty { downloadItem.url }, downloadItem.id.toInt())
                notificationUtil.notify(downloadItem.id.toInt(), notification)

                CoroutineScope(Dispatchers.IO).launch {
                    val noCache = !sharedPreferences.getBoolean("cache_downloads", true) && File(FileUtil.formatPath(downloadItem.downloadPath)).canWrite()

                    val request = infoUtil.buildYoutubeDLRequest(downloadItem)
                    downloadItem.status = DownloadRepository.Status.Active.toString()
                    CoroutineScope(Dispatchers.IO).launch {
                        //update item if its incomplete
                        updateDownloadItem(downloadItem, infoUtil, dao, resultDao)
                    }

                    val cacheDir = FileUtil.getCachePath(context)
                    val tempFileDir = File(cacheDir, downloadItem.id.toString())
                    tempFileDir.delete()
                    tempFileDir.mkdirs()

                    val downloadLocation = downloadItem.downloadPath
                    val keepCache = sharedPreferences.getBoolean("keep_cache", false)
                    val logDownloads = sharedPreferences.getBoolean("log_downloads", false) && !sharedPreferences.getBoolean("incognito", false)


                    val commandString = infoUtil.parseYTDLRequestString(request)
                    val logString = StringBuilder("\n ${commandString}\n\n")
                    val logItem = LogItem(
                        0,
                        downloadItem.title.ifBlank { downloadItem.url },
                        "Downloading:\n" +
                                "Title: ${downloadItem.title}\n" +
                                "URL: ${downloadItem.url}\n" +
                                "Type: ${downloadItem.type}\n" +
                                "Command: $logString",
                        downloadItem.format,
                        downloadItem.type,
                        System.currentTimeMillis(),
                    )


                    runBlocking {
                        if (logDownloads) logItem.id = logRepo.insert(logItem)
                        downloadItem.logID = logItem.id
                        dao.update(downloadItem)
                    }

                    runCatching {
                        YoutubeDL.getInstance().execute(request, downloadItem.id.toString()){ progress, _, line ->
                            setProgressAsync(workDataOf("progress" to progress.toInt(), "output" to line.chunked(5000).first().toString(), "id" to downloadItem.id))
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
                        val wasQuickDownloaded = updateDownloadItem(downloadItem, infoUtil, dao, resultDao)
                        runBlocking {
                            var finalPaths : List<String>?

                            if (noCache){
                                setProgressAsync(workDataOf("progress" to 100, "output" to "Scanning Files", "id" to downloadItem.id))
                                finalPaths = it.out.split("\n")
                                    .asSequence()
                                    .filter { it.startsWith("'/storage") }
                                    .map { it.removePrefix("'") }
                                    .map { it.removeSuffix("\n") }
                                    .map { it.removeSuffix("'") }
                                    .sortedBy { File(it).lastModified() }
                                    .toList()
//                                finalPaths = File(FileUtil.formatPath(downloadLocation))
//                                    .walkTopDown()
//                                    .filter { it.isFile && p.any { f -> f.contains(it.nameWithoutExtension) }}
//                                    .sortedByDescending { it.length() }
//                                    .map { it.absolutePath }
//                                    .toList()
                                FileUtil.scanMedia(finalPaths, context)
                                if (finalPaths.isEmpty()){
                                    finalPaths = listOf(context.getString(R.string.unfound_file))
                                }
                            }else{
                                //move file from internal to set download directory
                                setProgressAsync(workDataOf("progress" to 100, "output" to "Moving file to ${FileUtil.formatPath(downloadLocation)}", "id" to downloadItem.id))
                                try {
                                    finalPaths = withContext(Dispatchers.IO){
                                        FileUtil.moveFile(tempFileDir.absoluteFile,context, downloadLocation, keepCache){ p ->
                                            setProgressAsync(workDataOf("progress" to p, "output" to "Moving file to ${FileUtil.formatPath(downloadLocation)}", "id" to downloadItem.id))
                                        }
                                    }.filter { !it.matches("\\.(description)|(txt)\$".toRegex()) }

                                    if (finalPaths.isNotEmpty()){
                                        setProgressAsync(workDataOf("progress" to 100, "output" to "Moved file to $downloadLocation", "id" to downloadItem.id))
                                    }else{
                                        finalPaths = listOf(context.getString(R.string.unfound_file))
                                    }
                                }catch (e: Exception){
                                    finalPaths = listOf(context.getString(R.string.unfound_file))
                                    e.printStackTrace()
                                    handler.postDelayed({
                                        Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                    }, 1000)
                                }
                            }

                            finalPaths = finalPaths?.filter { !it.matches("(\\.description)|(\\.srt)|(\\.ass)|(\\.lrc)|(\\.vtt)|(\\.txt)|(\\.jpg)|(\\.png)\$".toRegex()) }
                            FileUtil.deleteConfigFiles(request)

                            //put download in history
                            val incognito = sharedPreferences.getBoolean("incognito", false)
                            if (!incognito) {
                                if (request.hasOption("--download-archive") && finalPaths == listOf(context.getString(R.string.unfound_file))) {
                                    Looper.prepare().run {
                                        Toast.makeText(context, resources.getString(R.string.download_already_exists), Toast.LENGTH_LONG).show()
                                    }
                                }else{
                                    val unixTime = System.currentTimeMillis() / 1000
                                    finalPaths?.apply {
                                        this.first().apply {
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
                                            this,
                                            downloadItem.website,
                                            downloadItem.format,
                                            downloadItem.id,
                                            commandString)
                                        historyDao.insert(historyItem)
                                    }

                                }
                            }

                            notificationUtil.cancelDownloadNotification(downloadItem.id.toInt())
                            notificationUtil.createDownloadFinished(
                                downloadItem.title,  if (finalPaths?.first().equals(context.getString(R.string.unfound_file))) null else finalPaths, resources
                            )

                            if (wasQuickDownloaded){
                                runCatching {
                                    setProgressAsync(workDataOf("progress" to 100, "output" to "Creating Result Items", "id" to downloadItem.id))
                                    runBlocking {
                                        infoUtil.getFromYTDL(downloadItem.url).forEach { res ->
                                            if (res != null) {
                                                resultDao.insert(res)
                                            }
                                        }
                                    }
                                }
                            }

                            dao.delete(downloadItem.id)

                            if (logDownloads){
                                logRepo.update(it.out, logItem.id)
                            }
                        }

                    }.onFailure {
                        FileUtil.deleteConfigFiles(request)

                        withContext(Dispatchers.Main){
                            notificationUtil.cancelDownloadNotification(downloadItem.id.toInt())
                        }
                        if (it is YoutubeDL.CanceledException) {

                        }else{
                            if(it.message != null){
                                if (logDownloads){
                                    logRepo.update(it.message!!, logItem.id)
                                }else{
                                    logString.append("${it.message}\n")
                                    logItem.content = logString.toString()
                                    val logID = withContext(Dispatchers.IO){
                                        logRepo.insert(logItem)
                                    }
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
                                downloadItem.title.ifEmpty { downloadItem.url }, it.message,
                                downloadItem.logID,
                                resources
                            )

                            setProgressAsync(workDataOf("progress" to 100, "output" to it.toString(), "id" to downloadItem.id))
                        }
                        if (items.size <= 1) WorkManager.getInstance(context).cancelWorkById(this@DownloadWorker.id)
                    }
                }
            }

            if (eligibleDownloads.isNotEmpty()){
                eligibleDownloads.forEach { it.status = DownloadRepository.Status.Active.toString() }
                dao.updateMultiple(eligibleDownloads)
            }
        }


        return Result.success()
    }

    private fun updateDownloadItem(
        downloadItem: DownloadItem,
        infoUtil: InfoUtil,
        dao: DownloadDao,
        resultDao: ResultDao
    ) : Boolean {
        var wasQuickDownloaded = false
        if (downloadItem.title.isEmpty() || downloadItem.author.isEmpty() || downloadItem.thumb.isEmpty()){
            runCatching {
                if (isStopped) YoutubeDL.getInstance().destroyProcessById(downloadItem.id.toString())
                setProgressAsync(workDataOf("progress" to 0, "output" to context.getString(R.string.updating_download_data), "id" to downloadItem.id))
                val info = infoUtil.getMissingInfo(downloadItem.url)
                if (downloadItem.title.isEmpty()) downloadItem.title = info?.title.toString()
                if (downloadItem.author.isEmpty()) downloadItem.author = info?.author.toString()
                downloadItem.duration = info?.duration.toString()
                downloadItem.website = info?.website.toString()
                if (downloadItem.thumb.isEmpty()) downloadItem.thumb = info?.thumb.toString()
                runBlocking {
                    wasQuickDownloaded = resultDao.getCountInt() == 0
                    dao.update(downloadItem)
                }
            }
        }
        return wasQuickDownloaded
    }



    companion object {
        val runningYTDLInstances: MutableList<Long> = mutableListOf()
        const val TAG = "DownloadWorker"
    }

}