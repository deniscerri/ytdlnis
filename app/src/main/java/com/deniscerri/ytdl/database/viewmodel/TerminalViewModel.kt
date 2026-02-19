package com.deniscerri.ytdl.database.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.dao.TerminalDao
import com.deniscerri.ytdl.database.models.TerminalItem
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.work.TerminalDownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch


class TerminalViewModel(private val application: Application) : AndroidViewModel(application) {
    private val dbManager: DBManager = DBManager.getInstance(application)
    private val dao: TerminalDao = dbManager.terminalDao
    private val notificationUtil = NotificationUtil(application)
    fun getCount() : Int{
        return dao.getActiveTerminalsCount()
    }

    fun getTerminals() : Flow<List<TerminalItem>> {
        return dao.getActiveTerminalDownloadsFlow()
    }

    fun getTerminal(id: Long) : Flow<TerminalItem?> {
        return dao.getActiveTerminalFlow(id)
    }

    suspend fun insert(item: TerminalItem) : Long {
        return dao.insert(item)
    }

    suspend fun delete(id: Long) = CoroutineScope(Dispatchers.IO).launch{
        dao.delete(id)
    }

    fun startTerminalDownloadWorker(item: TerminalItem) = CoroutineScope(Dispatchers.IO).launch {
        val workRequest = OneTimeWorkRequestBuilder<TerminalDownloadWorker>()
            .setInputData(
                Data.Builder()
                    .putInt("id", item.id.toInt())
                    .putString("command", item.command)
                    .build()
            )
            .addTag("terminal")
            .addTag(item.id.toString())
            .build()

        WorkManager.getInstance(application).beginUniqueWork(
            item.id.toString(),
            ExistingWorkPolicy.KEEP,
            workRequest
        ).enqueue()
    }

    fun cancelTerminalDownload(id: Long) = CoroutineScope(Dispatchers.IO).launch{
        RuntimeManager.getInstance().destroyProcessById(id.toString())
        WorkManager.getInstance(application).cancelUniqueWork(id.toString())
        Thread.sleep(200)
        notificationUtil.cancelDownloadNotification(id.toInt())
        delete(id)
    }


}