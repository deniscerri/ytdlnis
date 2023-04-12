package com.deniscerri.ytdlnis.work

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.work.*
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.*


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
        val notification = notificationUtil.createDownloadServiceNotification(pendingIntent, command.take(20), itemId, NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID)
        val foregroundInfo = ForegroundInfo(itemId, notification)
        setForegroundAsync(foregroundInfo)
        
        val request = YoutubeDLRequest(emptyList())
        val sharedPreferences = context.getSharedPreferences("root_preferences",
            Service.MODE_PRIVATE
        )

        val downloadLocation = sharedPreferences.getString("command_path", context.getString(R.string.command_path))
        val tempFileDir = File(context.cacheDir.absolutePath + "/downloads/" + itemId)
        tempFileDir.delete()
        tempFileDir.mkdirs()

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
                setProgressAsync(workDataOf("progress" to progress.toInt(), "output" to line, "id" to itemId, "log" to logDownloads))
                val title: String = command.take(20)
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
            var finalPath : String?
            try {
                finalPath = moveFile(tempFileDir.absoluteFile, downloadLocation!!){ progress ->
                    setProgressAsync(workDataOf("progress" to progress))
                }
            }catch (e: Exception){
                finalPath = context.getString(R.string.unfound_file)
                e.printStackTrace()
                handler.postDelayed({
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                }, 1000)
            }

            val chunks = it.out.chunked(5000)
            val dataBuilder = Data.Builder();
            dataBuilder.putString("output", chunks[0])


            return Result.success(
                dataBuilder.build()
            )

        }.onFailure {
            if (it is YoutubeDL.CanceledException) {
                return Result.failure()
            }

            if (logDownloads && logFile.exists()){
                logFile.appendText("${it.message}\n")
            }
            tempFileDir.delete()

            Log.e(TAG, context.getString(R.string.failed_download), it)
            notificationUtil.updateDownloadNotification(
                itemId,
                context.getString(R.string.failed_download), 0, 0, command.take(20),
                NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID
            )

            if (logDownloads && logFile.exists()){
                logFile.appendText("${it.message}\n")
            }

            return Result.failure(
              Data.Builder().putString("output", it.message.toString()).build()
            )
        }

        return Result.success()

    }
    @Throws(Exception::class)
    private fun moveFile(originDir: File, downLocation: String, progress: (progress: Int) -> Unit) : String{
        val fileUtil = FileUtil()
        val path = fileUtil.moveFile(originDir, context, downLocation){ p ->
            progress(p)
        }
        return path
    }

    companion object {
        const val TAG = "DownloadWorker"
    }

}