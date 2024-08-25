package com.deniscerri.ytdl.work

import android.content.Context
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.repository.ResultRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.util.NotificationUtil
import kotlinx.coroutines.runBlocking


class UpdateMultipleDownloadsFormatsWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val dbManager = DBManager.getInstance(context)
        val dao = dbManager.downloadDao
        val resDao = dbManager.resultDao
        val resultRepo = ResultRepository(resDao, context)
        val vm = DownloadViewModel(App.instance)
        val notificationUtil = NotificationUtil(context)
        val ids = inputData.getLongArray("ids")!!.toMutableList()
        val workID = inputData.getInt("id", 0)
        if (workID == 0) return Result.failure()

        val notification = notificationUtil.createFormatsUpdateNotification()
        val foregroundInfo = ForegroundInfo(workID, notification)
        setForegroundAsync(foregroundInfo)

        var count = 0

        return try{
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
                        d.allFormats.addAll(resultRepo.getFormats(d.url))
                        d.format = vm.getFormat(d.allFormats,d.type)

                        r?.formats?.clear()
                        r?.formats?.addAll(d.allFormats)

                        runBlocking {
                            r?.apply { resDao.update(this) }
                            dao.update(d)
                        }
                    }


                    count++
                    notificationUtil.updateFormatUpdateNotification(workID, UpdateMultipleDownloadsFormatsWorker::class.java.name, count, ids.size)
                }else{
                    throw Exception()
                }
            }

            Result.success()
        }catch (e: Exception){
            notificationUtil.cancelDownloadNotification(workID)
            ids.clear()
            Result.failure()
        }finally {
            if (ids.isNotEmpty()){
                notificationUtil.showFormatsUpdatedNotification(ids)
            }
        }
    }

}