package com.deniscerri.ytdl.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.content.IntentCompat
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.repository.ResultRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.SharedDownloadViewModel
import com.deniscerri.ytdl.util.NotificationUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


class ProcessDownloadsInBackgroundService : Service() {

    private val binder: IBinder = LocalBinder()
    private val queueProcessingDownloadsJobList = mutableListOf<Job>()
    private lateinit var repository: DownloadRepository
    private lateinit var resultRepository: ResultRepository
    private lateinit var downloadViewModel: SharedDownloadViewModel
    inner class LocalBinder: Binder() {
        val service: ProcessDownloadsInBackgroundService
            get() = this@ProcessDownloadsInBackgroundService
    }

    override fun onCreate() {
        super.onCreate()
        val dbManager = DBManager.getInstance(this)
        repository = DownloadRepository(dbManager.downloadDao)
        resultRepository = ResultRepository(dbManager.resultDao, this)
        downloadViewModel = SharedDownloadViewModel(this)
    }


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        val binding = intent.getBooleanExtra("binding", false)
        if (binding) return super.onStartCommand(intent, flags, startId)

        val notificationUtil = NotificationUtil(this)
        startForeground(System.currentTimeMillis().toInt(), notificationUtil.createProcessingDownloads())


        val itemType = intent.getStringExtra("itemType")!!
        val itemIDs = intent.getLongArrayExtra("itemIDs")!!.toList()
        val processingItemIDs = intent.getLongArrayExtra("processingItemIDs")!!.toList()
        val timeInMillis = intent.getLongExtra("timeInMillis", 0)
        val processingFinished = intent.getBooleanExtra("processingFinished", true)

        val job = CoroutineScope(SupervisorJob()).launch(Dispatchers.IO) {
            if (!processingFinished) {
                when(itemType) {
                    ResultItem::class.java.toString() -> {
                        itemIDs.chunked(100).map { ids ->
                            resultRepository.getAllByIDs(ids).map {
                                downloadViewModel.createDownloadItemFromResult(
                                    result = it, givenType = DownloadViewModel.Type.valueOf(
                                        downloadViewModel.getDownloadType(url = it.url).toString()
                                    )
                                )
                            }.apply {
                                if (timeInMillis > 0) {
                                    this.forEach {
                                        it.setAsScheduling(timeInMillis)
                                    }
                                }
                                if (isActive){
                                    downloadViewModel.queueDownloads(this)
                                }
                            }
                        }
                    }

                    DownloadItem::class.java.toString() -> {
                        itemIDs.chunked(100).map { ids ->
                            repository.getAllItemsByIDs(ids).apply {
                                if (timeInMillis > 0) {
                                    this.forEach {
                                        it.setAsScheduling(timeInMillis)
                                    }
                                }
                                if (isActive){
                                    downloadViewModel.queueDownloads(this)
                                }
                            }
                        }
                    }
                }
            }else {
                repository.getAllItemsByIDs(processingItemIDs).apply {
                    this.chunked(100).map {
                        if (timeInMillis > 0){
                            this.forEach { d ->
                                d.setAsScheduling(timeInMillis)
                            }
                        }

                        if (isActive){
                            downloadViewModel.queueDownloads(it)
                        }
                    }

                }
            }
        }
        job.invokeOnCompletion {
            queueProcessingDownloadsJobList.remove(job)
            if (queueProcessingDownloadsJobList.isEmpty()){
                stopForeground(true)
                stopSelf()
            }
        }
        queueProcessingDownloadsJobList.add(job)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private fun DownloadItem.setAsScheduling(timeInMillis: Long) {
        status = DownloadRepository.Status.Scheduled.toString()
        downloadStartTime = timeInMillis
    }

    fun cancelAllProcessingJobs(){
        queueProcessingDownloadsJobList.onEach { it.cancel(CancellationException()) }
    }


}