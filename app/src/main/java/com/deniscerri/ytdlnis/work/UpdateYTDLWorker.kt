package com.deniscerri.ytdlnis.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.util.UpdateUtil


class UpdateYTDLWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val notification = NotificationUtil(context).createYTDLUpdateNotification()
        val foregroundInfo = ForegroundInfo(System.currentTimeMillis().toInt(), notification)
        setForeground(foregroundInfo)
        UpdateUtil(context).updateYoutubeDL()
        return Result.success()
    }

}