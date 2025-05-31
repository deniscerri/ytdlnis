package com.deniscerri.ytdl.work

import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
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


class UpdateMultipleDownloadsFormatsWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val dbManager = DBManager.getInstance(context)
        val dao = dbManager.downloadDao
        val resDao = dbManager.resultDao
        val commandTemplateDao = dbManager.commandTemplateDao
        val resultRepo = ResultRepository(resDao, commandTemplateDao, context)
        val vm = DownloadViewModel(App.instance)
        val notificationUtil = NotificationUtil(context)
        val ids = inputData.getLongArray("ids")!!.toMutableList()
        val otherIdsInBundle = inputData.getLongArray("other_ids_in_bundle")!!.toMutableList()
        val workID = inputData.getInt("id", 0)
        if (workID == 0) return Result.failure()

        val notification = notificationUtil.createFormatsUpdateNotification()

        if (Build.VERSION.SDK_INT > 33) {
            setForegroundAsync(ForegroundInfo(workID, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        }else{
            setForegroundAsync(ForegroundInfo(workID, notification))
        }

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
                notificationUtil.showFormatsUpdatedNotification(ids + otherIdsInBundle)
            }
        }
    }

}