package com.deniscerri.ytdlnis.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.LogItem
import com.deniscerri.ytdlnis.database.repository.LogRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.ui.more.TerminalActivity
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Calendar


class TerminalDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val itemId = inputData.getInt("id", 0)
        val command = inputData.getString("command")
        if (itemId == 0) return Result.failure()
        if (command!!.isEmpty()) return Result.failure()

        val dbManager = DBManager.getInstance(context)
        val logRepo = LogRepository(dbManager.logDao)

        val notificationUtil = NotificationUtil(context)
        val handler = Handler(Looper.getMainLooper())

        val intent = Intent(context, TerminalActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = notificationUtil.createDownloadServiceNotification(pendingIntent, command.take(65), itemId, NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID)
        val foregroundInfo = ForegroundInfo(itemId, notification)
        setForegroundAsync(foregroundInfo)
        
        val request = YoutubeDLRequest(emptyList())
        val sharedPreferences =  PreferenceManager.getDefaultSharedPreferences(context)

        val downloadLocation = sharedPreferences.getString("command_path", context.getString(R.string.command_path))
        val tempFileDir = File(context.cacheDir.absolutePath + "/downloads/" + itemId)
        tempFileDir.delete()
        tempFileDir.mkdirs()

        val outputFile = File(context.cacheDir.absolutePath + "/$itemId.txt")
        if (! outputFile.exists()) outputFile.createNewFile()

        request.addOption(
            "--config-locations",
            File(context.cacheDir.absolutePath + "/downloads/config${System.currentTimeMillis()}.txt").apply {
                writeText(command)
            }.absolutePath
        )

        val cookiesFile = File(context.cacheDir, "cookies.txt")
        if (cookiesFile.exists()){
            request.addOption("--cookies", cookiesFile.absolutePath)
        }

        request.addOption("-P", tempFileDir.absolutePath)

        val logDownloads = sharedPreferences.getBoolean("log_downloads", false) && !sharedPreferences.getBoolean("incognito", false)

        val logItem = LogItem(
            0,
            "Terminal Download",
            "Downloading:\n" +
                    "Terminal Download\n" +
                    "Command: ${command}\n\n",
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
                    FileUtil.moveFile(tempFileDir.absoluteFile,context, downloadLocation!!, false){ p ->
                        setProgressAsync(workDataOf("progress" to p))
                    }
                }catch (e: Exception){
                    e.printStackTrace()
                    handler.postDelayed({
                        Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                    }, 1000)
                }
            }

            if (it.out.length > 200){
                outputFile.appendText("${it.out}\n")
                if (logDownloads){
                    CoroutineScope(Dispatchers.IO).launch {
                        logRepo.update(it.out, logItem.id)
                    }
                }
            }
            notificationUtil.cancelDownloadNotification(itemId)

        }.onFailure {
            if (it is YoutubeDL.CanceledException) {
                return Result.failure()
            }
            outputFile.appendText("${it.message}\n")
            if (logDownloads){
                CoroutineScope(Dispatchers.IO).launch {
                    if (it.message != null){
                        logRepo.update(it.message!!, logItem.id)
                    }
                }
            }
            tempFileDir.delete()

            Log.e(TAG, context.getString(R.string.failed_download), it)
            notificationUtil.cancelDownloadNotification(itemId)

            return Result.failure()
        }

        return Result.success()

    }
    override fun onStopped() {
        YoutubeDL.getInstance().destroyProcessById(DownloadWorker.itemId.toInt().toString())
        super.onStopped()
    }

    companion object {
        const val TAG = "DownloadWorker"
    }

}