package com.deniscerri.ytdlnis.work

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.navigation.NavDeepLinkBuilder
import androidx.preference.PreferenceManager
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.dao.ResultDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.models.LogItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.repository.LogRepository
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


class DownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        itemId = inputData.getLong("id", 0)
        if (itemId == 0L) return Result.failure()


        val notificationUtil = NotificationUtil(context)
        val infoUtil = InfoUtil(context)
        val dbManager = DBManager.getInstance(context)
        val dao = dbManager.downloadDao
        val repository = DownloadRepository(dao)
        val historyDao = dbManager.historyDao
        val resultDao = dbManager.resultDao
        val logRepo = LogRepository(dbManager.logDao)
        val handler = Handler(Looper.getMainLooper())

        val downloadItem: DownloadItem?
        try {
            downloadItem = repository.getItemByID(itemId)
        }catch (e: Exception){
            e.printStackTrace()
            return Result.failure()
        }

        if (downloadItem.status != DownloadRepository.Status.Queued.toString()) return Result.failure()

        val pendingIntent = NavDeepLinkBuilder(context)
            .setGraph(R.navigation.nav_graph)
            .setDestination(R.id.downloadQueueMainFragment)
            .createPendingIntent()

        val notification = notificationUtil.createDownloadServiceNotification(pendingIntent, downloadItem.title, downloadItem.id.toInt(), NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID)
        val foregroundInfo = ForegroundInfo(downloadItem.id.toInt(), notification)
        setForegroundAsync(foregroundInfo)

        Log.e(TAG, downloadItem.toString())

        runBlocking{
            repository.setDownloadStatus(downloadItem, DownloadRepository.Status.Active)
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        CoroutineScope(Dispatchers.IO).launch {
            //update item if its incomplete
            updateDownloadItem(downloadItem, infoUtil, dao, resultDao)
        }

        val request = infoUtil.buildYoutubeDLRequest(downloadItem)
        val tempFileDir = File(context.cacheDir.absolutePath + "/downloads/" + downloadItem.id)
        tempFileDir.delete()
        tempFileDir.mkdirs()

        val downloadLocation = downloadItem.downloadPath
        val keepCache = sharedPreferences.getBoolean("keep_cache", false)

        val logDownloads = sharedPreferences.getBoolean("log_downloads", false) && !sharedPreferences.getBoolean("incognito", false)

        val logItem = LogItem(
            0,
            downloadItem.title.ifEmpty { downloadItem.url },
            "Downloading:\n" +
                    "Title: ${downloadItem.title}\n" +
                    "URL: ${downloadItem.url}\n" +
                    "Type: ${downloadItem.type}\n" +
                    "Format: ${downloadItem.format}\n\n" +
                    "Command: ${java.lang.String.join(" ", request.buildCommand())}\n\n",
            downloadItem.format,
            downloadItem.type,
            System.currentTimeMillis(),
        )


        if (logDownloads){
            runBlocking {
                logItem.id = logRepo.insert(logItem)
                downloadItem.logID = logItem.id
                dao.update(downloadItem)
            }

        }
        runCatching {
            YoutubeDL.getInstance().execute(request, downloadItem.id.toString()){ progress, _, line ->
                setProgressAsync(workDataOf("progress" to progress.toInt(), "output" to line.chunked(5000).first().toString(), "id" to downloadItem.id, "log" to logDownloads))
                val title: String = downloadItem.title
                notificationUtil.updateDownloadNotification(
                    downloadItem.id.toInt(),
                    line, progress.toInt(), 0, title,
                    NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID
                )
                if (logDownloads){
                    CoroutineScope(Dispatchers.IO).launch {
                        logRepo.update(line, logItem.id)
                    }
                }
            }
        }.onSuccess {
            val wasQuickDownloaded = updateDownloadItem(downloadItem, infoUtil, dao, resultDao)

            CoroutineScope(Dispatchers.IO).launch {
                var finalPaths : List<String>?
                //move file from internal to set download directory
                setProgressAsync(workDataOf("progress" to 100, "output" to "Moving file to ${FileUtil.formatPath(downloadLocation)}", "id" to downloadItem.id, "log" to logDownloads))
                try {
                    finalPaths = withContext(Dispatchers.IO){
                        FileUtil.moveFile(tempFileDir.absoluteFile,context, downloadLocation, keepCache){ p ->
                            setProgressAsync(workDataOf("progress" to p, "output" to "Moving file to ${FileUtil.formatPath(downloadLocation)}", "id" to downloadItem.id, "log" to logDownloads))
                        }
                    }

                    if (finalPaths.isNotEmpty()){
                        setProgressAsync(workDataOf("progress" to 100, "output" to "Moved file to $downloadLocation", "id" to downloadItem.id, "log" to logDownloads))
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


                //put download in history
                val incognito = sharedPreferences.getBoolean("incognito", false)
                if (!incognito) {
                    val unixtime = System.currentTimeMillis() / 1000
                    val file = File(finalPaths?.first()!!)
                    downloadItem.format.filesize = file.length()
                    val historyItem = HistoryItem(0, downloadItem.url, downloadItem.title, downloadItem.author, downloadItem.duration, downloadItem.thumb, downloadItem.type, unixtime, finalPaths.first() , downloadItem.website, downloadItem.format, downloadItem.id)
                    historyDao.insert(historyItem)
                }

                notificationUtil.cancelDownloadNotification(downloadItem.id.toInt())
                notificationUtil.createDownloadFinished(
                    downloadItem.title,  if (finalPaths?.first().equals(context.getString(R.string.unfound_file))) null else finalPaths,
                    NotificationUtil.DOWNLOAD_FINISHED_CHANNEL_ID
                )
            }

            if (wasQuickDownloaded){
                runCatching {
                    setProgressAsync(workDataOf("progress" to 100, "output" to "Creating Result Items", "id" to downloadItem.id, "log" to false))
                    runBlocking {
                        infoUtil.getFromYTDL(downloadItem.url).forEach { res ->
                            if (res != null) {
                                resultDao.insert(res)
                            }
                        }
                    }
                }
            }

            runBlocking {
                dao.delete(downloadItem.id)
            }
        }.onFailure {
            if (it is YoutubeDL.CanceledException) {
                downloadItem.status = DownloadRepository.Status.Cancelled.toString()
                runBlocking {
                    dao.update(downloadItem)
                }
                return Result.failure(
                    Data.Builder().putString("output", "Download has been cancelled!").build()
                )
            }else{
                if (logDownloads){
                    CoroutineScope(Dispatchers.IO).launch {
                        if(it.message != null){
                            logRepo.update(it.message!!, logItem.id)
                        }
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
                    downloadItem.title, it.message,
                    if (logDownloads) logItem.id else null,
                    NotificationUtil.DOWNLOAD_FINISHED_CHANNEL_ID
                )

                return Result.failure(
                    Data.Builder().putString("output", it.toString()).build()
                )
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
                setProgressAsync(workDataOf("progress" to 0, "output" to context.getString(R.string.updating_download_data), "id" to downloadItem.id, "log" to false))
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

    override fun onStopped() {
        YoutubeDL.getInstance().destroyProcessById(itemId.toInt().toString())
        super.onStopped()
    }

    companion object {
        var itemId: Long = 0
        const val TAG = "DownloadWorker"
    }

}