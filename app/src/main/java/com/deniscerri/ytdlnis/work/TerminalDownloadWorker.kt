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
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.LogItem
import com.deniscerri.ytdlnis.database.repository.LogRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.ui.more.TerminalActivity
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
        if (itemId == 0) return Result.failure()
        if (command!!.isEmpty()) return Result.failure()

        val dbManager = DBManager.getInstance(context)
        val logRepo = LogRepository(dbManager.logDao)

        val notificationUtil = NotificationUtil(context)
        val handler = Handler(Looper.getMainLooper())
        val infoUtil = InfoUtil(context)

        val intent = Intent(context, TerminalActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = notificationUtil.createDownloadServiceNotification(pendingIntent, command.take(65), itemId)
        val foregroundInfo = ForegroundInfo(itemId, notification)
        setForegroundAsync(foregroundInfo)
        
        val request = YoutubeDLRequest(emptyList())
        val sharedPreferences =  PreferenceManager.getDefaultSharedPreferences(context)

        val downloadLocation = sharedPreferences.getString("command_path", FileUtil.getDefaultCommandPath())

        val outputFile = File(context.cacheDir.absolutePath + "/$itemId.txt")
        if (! outputFile.exists()) outputFile.createNewFile()

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
        }

        request.addOption("-P", FileUtil.getDefaultCommandPath() + "/" + itemId)

        val logDownloads = sharedPreferences.getBoolean("log_downloads", false) && !sharedPreferences.getBoolean("incognito", false)

        val logItem = LogItem(
            0,
            "Terminal Download",
            "Downloading:\n" +
                    "Terminal Download\n" +
                    "Command: ${infoUtil.parseYTDLRequestString(request)}\n\n",
            Format(),
            DownloadViewModel.Type.command,
            System.currentTimeMillis(),
        )

        runCatching {
            if (logDownloads){
                runBlocking {
                    logItem.id = logRepo.insert(logItem)
                }
            }

            YoutubeDL.getInstance().execute(request, itemId.toString()){ progress, _, line ->
                setProgressAsync(workDataOf("progress" to progress.toInt(), "output" to line.chunked(5000).first().toString(), "id" to itemId, "log" to logDownloads))
                val title: String = command.take(65)
                notificationUtil.updateDownloadNotification(
                    itemId,
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
            CoroutineScope(Dispatchers.IO).launch {
                //move file from internal to set download directory
                try {
                    FileUtil.moveFile(File(FileUtil.getDefaultCommandPath() + "/" + itemId),context, downloadLocation!!, false){ p ->
                        setProgressAsync(workDataOf("progress" to p))
                    }
                }catch (e: Exception){
                    e.printStackTrace()
                    handler.postDelayed({
                        Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                    }, 1000)
                }
            }

            outputFile.appendText("${it.out}\n")
            if (logDownloads){
                CoroutineScope(Dispatchers.IO).launch {
                    logRepo.update(it.out, logItem.id)
                }
            }
            notificationUtil.cancelDownloadNotification(itemId)

        }.onFailure {
            if (it is YoutubeDL.CanceledException) {
                return Result.failure()
            }
            outputFile.appendText("\n${it.message}\n")
            if (logDownloads){
                CoroutineScope(Dispatchers.IO).launch {
                    if (it.message != null){
                        logRepo.update(it.message!!, logItem.id)
                    }
                }
            }
            File(FileUtil.getDefaultCommandPath() + "/" + itemId).deleteRecursively()

            Log.e(TAG, context.getString(R.string.failed_download), it)
            notificationUtil.cancelDownloadNotification(itemId)

            return Result.failure()
        }

        return Result.success()

    }

    companion object {
        private var itemId : Int = 0
        const val TAG = "DownloadWorker"
    }

}