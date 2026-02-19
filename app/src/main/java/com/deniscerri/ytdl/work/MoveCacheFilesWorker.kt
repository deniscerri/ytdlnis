package com.deniscerri.ytdl.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.NotificationUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption


class MoveCacheFilesWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val notificationUtil = NotificationUtil(App.instance)
        val id = System.currentTimeMillis().toInt()

        val cachePath = FileUtil.getCacheDownloadsPath(context)
        val downloadFolders = File(cachePath)
        val allContent = downloadFolders.walk()

        val totalFiles = allContent.count()
        val destination = File(FileUtil.getDefaultApplicationPath(), "CACHE_IMPORT")
        destination.mkdirs()

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = notificationUtil.createMoveCacheFilesNotification(pendingIntent, NotificationUtil.DOWNLOAD_MISC_CHANNEL_ID)

        if (Build.VERSION.SDK_INT >= 33) {
            setForegroundAsync(ForegroundInfo(id, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        }else{
            setForegroundAsync(ForegroundInfo(id, notification))
        }

        var progress = 0
        allContent.forEach {
            progress++
            if (progress == 1) return@forEach

            notificationUtil.updateCacheMovingNotification(id, progress, totalFiles)
            val destFile = File(destination.absolutePath + "/${it.absolutePath.removePrefix(cachePath)}")
            if (it.isDirectory) {
                destFile.mkdirs()
                return@forEach
            }

            if (Build.VERSION.SDK_INT >= 26 ){
                Files.move(it.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }else{
                it.renameTo(destFile)
            }
        }

        downloadFolders.listFiles()?.forEach { child ->
            child.deleteRecursively()
        }

        val handler = Handler(Looper.getMainLooper())
        handler.post {
            Toast.makeText(context, context.getString(R.string.ok), Toast.LENGTH_SHORT).show()
        }
        return Result.success()
    }

    companion object {
        const val TAG = "MoveCacheFilesWorker"
    }

}