package com.deniscerri.ytdl.work

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.repository.DownloadRepository


class CancelScheduledDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    @SuppressLint("RestrictedApi")
    override suspend fun doWork(): Result {
        if (isStopped) return Result.success()
        val dbManager = DBManager.getInstance(context)
        val dao = dbManager.downloadDao

        val runningDownloads = dao.getActiveDownloadsList()
        WorkManager.getInstance(context).cancelAllWorkByTag("download")
        runningDownloads.forEach {
            RuntimeManager.getInstance().destroyProcessById(it.id.toString())
            it.status = DownloadRepository.Status.Queued.toString()
            dao.update(it)
        }
        return Result.success()
    }
}