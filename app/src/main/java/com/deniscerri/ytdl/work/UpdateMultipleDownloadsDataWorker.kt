package com.deniscerri.ytdl.work

import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Build
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.repository.ResultRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.util.NotificationUtil
import kotlinx.coroutines.runBlocking


class UpdateMultipleDownloadsDataWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val dbManager = DBManager.getInstance(context)
        val dao = dbManager.downloadDao
        val resDao = dbManager.resultDao
        val resultRepo = ResultRepository(resDao, context)
        val notificationUtil = NotificationUtil(context)
        val ids = inputData.getLongArray("ids")!!.toMutableList()
        val workID = inputData.getInt("id", 0)
        if (workID == 0) return Result.failure()

        val notification = notificationUtil.createDataUpdateNotification()

        if (Build.VERSION.SDK_INT > 33) {
            setForegroundAsync(ForegroundInfo(workID, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE))
        }else{
            setForegroundAsync(ForegroundInfo(workID, notification))
        }

        var count = 0

        return try{
            ids.forEach {
                if (!isStopped){
                    val d = dao.getDownloadById(it)
                    if (d.title.isNotBlank() && d.author.isNotBlank() && d.thumb.isNotBlank()) {
                        count++
                        return@forEach
                    }

                    runCatching {
                        runBlocking {
                            resultRepo.updateDownloadItem(d)?.apply {
                                val dd = dao.getNullableDownloadById(it)
                                if (dd != null) {
                                    d.status = dd.status
                                    dao.updateWithoutUpsert(this)
                                }
                            }
                        }
                    }

                    count++
                    notificationUtil.updateDataUpdateNotification(workID, UpdateMultipleDownloadsDataWorker::class.java.name, count, ids.size)
                }else{
                    throw Exception()
                }
            }

            Result.success()
        }catch (e: Exception){
            notificationUtil.cancelDownloadNotification(workID)
            ids.clear()
            Result.failure()
        }
    }

}