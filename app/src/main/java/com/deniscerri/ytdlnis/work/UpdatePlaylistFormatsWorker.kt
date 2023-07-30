package com.deniscerri.ytdlnis.work

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDeepLinkBuilder
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.deniscerri.ytdlnis.App
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.Converters
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.dao.ResultDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.models.LogItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.repository.LogRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.util.NotificationUtil.Companion.FORMAT_UPDATING_NOTIFICATION_ID
import com.deniscerri.ytdlnis.util.UpdateUtil
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File


class UpdatePlaylistFormatsWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val dbManager = DBManager.getInstance(context)
        val dao = dbManager.downloadDao
        val resDao = dbManager.resultDao
        val infoUtil = InfoUtil(context)
        val vm = DownloadViewModel(App.instance)
        val notificationUtil = NotificationUtil(context)
        val ids = inputData.getLongArray("ids")!!.toMutableList()
        val workID = inputData.getInt("id", 0)
        if (workID == 0) return Result.failure()

        val notification = notificationUtil.createFormatsUpdateNotification(workID)
        val foregroundInfo = ForegroundInfo(FORMAT_UPDATING_NOTIFICATION_ID, notification)
        setForeground(foregroundInfo)

        var count = 0
        ids.forEach {
            if (!isStopped){
                val d = dao.getDownloadById(it)
                val r = resDao.getResultByURL(d.url)

                if (d.allFormats.isNotEmpty()){
                    count++
                    return@forEach
                }

                runCatching {
                    d.allFormats.clear()
                    d.allFormats.addAll(infoUtil.getFormats(d.url))
                    d.format = vm.getFormat(d.allFormats,d.type)

                    r.formats.clear()
                    r.formats.addAll(d.allFormats)

                    dao.update(d)
                    resDao.update(r)
                }


                count++
                notificationUtil.updateFormatUpdateNotification(FORMAT_UPDATING_NOTIFICATION_ID, count, ids.size)
            }else{
                notificationUtil.cancelDownloadNotification(FORMAT_UPDATING_NOTIFICATION_ID)
            }
        }



        runBlocking {
            notificationUtil.showFormatsUpdatedNotification(ids)
        }

        return Result.success()
    }
}