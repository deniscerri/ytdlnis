package com.deniscerri.ytdlnis.database.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.net.ConnectivityManager
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.deniscerri.ytdlnis.App
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.dao.CommandTemplateDao
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.dao.ResultDao
import com.deniscerri.ytdlnis.database.dao.TerminalDao
import com.deniscerri.ytdlnis.database.models.AudioPreferences
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.DownloadItemSimple
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.models.TerminalItem
import com.deniscerri.ytdlnis.database.models.VideoPreferences
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.work.AlarmScheduler
import com.deniscerri.ytdlnis.work.DownloadWorker
import com.deniscerri.ytdlnis.work.TerminalDownloadWorker
import com.google.gson.Gson
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit


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

    fun getTerminal(id: Long) : Flow<TerminalItem> {
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
        YoutubeDL.getInstance().destroyProcessById(id.toString())
        WorkManager.getInstance(application).cancelUniqueWork(id.toString())
        Thread.sleep(200)
        notificationUtil.cancelDownloadNotification(id.toInt())
        delete(id)
    }


}