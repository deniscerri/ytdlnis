package com.deniscerri.ytdl.work

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
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.models.LogItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.repository.LogRepository
import com.deniscerri.ytdl.database.repository.ResultRepository
import com.deniscerri.ytdl.util.Extensions.getMediaDuration
import com.deniscerri.ytdl.util.Extensions.toStringDuration
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.InfoUtil
import com.deniscerri.ytdl.util.NotificationUtil
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.random.Random


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
            YoutubeDL.getInstance().destroyProcessById(it.id.toString())
        }
        return Result.success()
    }



    companion object {
        val runningYTDLInstances: MutableList<Long> = mutableListOf()
        const val TAG = "DownloadWorker"
    }

}