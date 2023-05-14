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
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File


class TerminalDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val itemId = inputData.getInt("id", 0)
        val command = inputData.getString("command")
        if (itemId == 0) return Result.failure()
        if (command!!.isEmpty()) return Result.failure()

        val notificationUtil = NotificationUtil(context)
        val fileUtil = FileUtil()
        val handler = Handler(Looper.getMainLooper())

        val intent = Intent(context, MainActivity::class.java)
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
        val logFolder = File(context.filesDir.absolutePath + "/logs")
        val logFile = fileUtil.getLogFileForTerminal(context, command)

        runCatching {
            if (logDownloads){
                logFolder.mkdirs()
                logFile.createNewFile()
                logFile.writeText("Downloading:\n" +
                        "Terminal Download\n" +
                        "Command: ${command}\n\n")
            }

            YoutubeDL.getInstance().execute(request, itemId.toString()){ progress, _, line ->
                setProgressAsync(workDataOf("progress" to progress.toInt(), "output" to line.chunked(5000).first().toString(), "id" to itemId, "log" to logDownloads))
                val title: String = command.take(65)
                notificationUtil.updateDownloadNotification(
                    itemId,
                    line, progress.toInt(), 0, title,
                    NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID
                )
                if (logDownloads && logFile.exists()){
                    logFile.appendText("${line}\n")
                }
            }
        }.onSuccess {
            //move file from internal to set download directory
            try {
                fileUtil.moveFile(tempFileDir.absoluteFile,context, downloadLocation!!, false){ p ->
                    setProgressAsync(workDataOf("progress" to p))
                }
            }catch (e: Exception){
                e.printStackTrace()
                handler.postDelayed({
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                }, 1000)
            }

            if (it.out.length > 200){
                outputFile.appendText("${it.out}\n")
                if (logDownloads && logFile.exists()){
                    logFile.appendText("${it.out}\n")
                }
            }
            notificationUtil.cancelDownloadNotification(itemId)

        }.onFailure {
            if (it is YoutubeDL.CanceledException) {
                return Result.failure()
            }
            outputFile.appendText("${it.message}\n")
            if (logDownloads && logFile.exists()){
                logFile.appendText("${it.message}\n")
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