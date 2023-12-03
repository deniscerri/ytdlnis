package com.deniscerri.ytdlnis.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.LogItem
import com.deniscerri.ytdlnis.database.repository.LogRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.ui.more.terminal.TerminalActivity
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File


class TerminalDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        itemId = inputData.getInt("id", 0)
        val command = inputData.getString("command")
        val dao = DBManager.getInstance(context).terminalDao
        if (itemId == 0) return Result.failure()
        if (command!!.isEmpty()) return Result.failure()

        val dbManager = DBManager.getInstance(context)
        val logRepo = LogRepository(dbManager.logDao)
        val notificationUtil = NotificationUtil(context)
        val handler = Handler(Looper.getMainLooper())

        val intent = Intent(context, TerminalActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = notificationUtil.createDownloadServiceNotification(pendingIntent, command.take(65), itemId)
        val foregroundInfo = ForegroundInfo(itemId, notification)
        setForegroundAsync(foregroundInfo)
        
        val request = YoutubeDLRequest(emptyList())
        val sharedPreferences =  PreferenceManager.getDefaultSharedPreferences(context)

        val downloadLocation = sharedPreferences.getString("command_path", FileUtil.getDefaultCommandPath())
        request.addOption(
            "--config-locations",
            File(context.cacheDir.absolutePath + "/config-TERMINAL[${System.currentTimeMillis()}].txt").apply {
                writeText(command)
            }.absolutePath
        )

        if (sharedPreferences.getBoolean("use_cookies", false)){
            val cookiesFile = File(context.cacheDir, "cookies.txt")
            if (cookiesFile.exists()){
                request.addOption("--cookies", cookiesFile.absolutePath)
            }

            val useHeader = sharedPreferences.getBoolean("use_header", false)
            val header = sharedPreferences.getString("useragent_header", "")
            if (useHeader && !header.isNullOrBlank()){
                request.addOption("--add-header","User-Agent:${header}")
            }
        }

        val commandPath = sharedPreferences.getString("command_path", FileUtil.getDefaultCommandPath())!!
        val noCache = !sharedPreferences.getBoolean("cache_downloads", true) && File(FileUtil.formatPath(commandPath)).canWrite()

        if (!noCache){
            request.addOption("-P", FileUtil.getCachePath(context) + "/TERMINAL/" + itemId)
        }else if (!request.hasOption("-P")){
            request.addOption("-P", FileUtil.formatPath(commandPath))
        }


        val logDownloads = sharedPreferences.getBoolean("log_downloads", false) && !sharedPreferences.getBoolean("incognito", false)

        val logItem = LogItem(
            0,
            "Terminal Task",
            "Terminal Task\n" +
                    "Command: ${command}\n\n",
            Format(),
            DownloadViewModel.Type.command,
            System.currentTimeMillis(),
        )

        kotlin.runCatching {
            if (logDownloads){
                runBlocking {
                    logItem.id = logRepo.insert(logItem)
                }
            }

            YoutubeDL.getInstance().execute(request, itemId.toString()){ progress, _, line ->
                runBlocking {
                    line.chunked(10000).forEach {
                        setProgress(workDataOf("progress" to progress.toInt(), "output" to it, "id" to itemId, "log" to logDownloads))
                        Thread.sleep(100)
                    }
                }

                val title: String = command.take(65)
                notificationUtil.updateTerminalDownloadNotification(
                    itemId,
                    line, progress.toInt(), title,
                    NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID
                )
                CoroutineScope(Dispatchers.IO).launch {
                    if (logDownloads) logRepo.update(line, logItem.id)
                    dao.updateLog(line, itemId.toLong())
                }
            }
        }.onSuccess {
            CoroutineScope(Dispatchers.IO).launch {
                if(!noCache){
                    //move file from internal to set download directory
                    try {
                        FileUtil.moveFile(File(FileUtil.getCachePath(context) + "/TERMINAL/" + itemId),context, downloadLocation!!, false){ p ->
                            setProgressAsync(workDataOf("progress" to p))
                        }
                    }catch (e: Exception){
                        e.printStackTrace()
                        handler.postDelayed({
                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                        }, 1000)
                    }
                }
            }

            return runBlocking {
                if (logDownloads) logRepo.update(it.out, logItem.id)
                dao.updateLog(it.out, itemId.toLong())
                Thread.sleep(1000)
                dao.delete(itemId.toLong())
                notificationUtil.cancelDownloadNotification(itemId)

                Result.success()
            }
        }.onFailure {
            return runBlocking {
                CoroutineScope(Dispatchers.IO).launch {
                    if (it.message != null){
                        if (logDownloads) logRepo.update(it.message!!, logItem.id)
                        dao.updateLog(it.message!!, itemId.toLong())
                        Thread.sleep(1000)
                        dao.delete(itemId.toLong())
                    }
                }
                notificationUtil.cancelDownloadNotification(itemId)
                File(FileUtil.getDefaultCommandPath() + "/" + itemId).deleteRecursively()
                Log.e(TAG, context.getString(R.string.failed_download), it)
                Result.failure()
            }


        }

        return Result.success()

    }

    companion object {
        private var itemId : Int = 0
        const val TAG = "DownloadWorker"
    }

}