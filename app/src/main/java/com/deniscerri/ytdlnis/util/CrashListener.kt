package com.deniscerri.ytdlnis.util

import android.content.Context
import com.deniscerri.ytdlnis.App
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.dao.LogDao
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.LogItem
import com.deniscerri.ytdlnis.database.repository.LogRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class CrashListener(private val context: Context) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(p0: Thread, p1: Throwable) {
        p1.message?.apply {
            CoroutineScope(SupervisorJob()).launch(Dispatchers.IO) {
                createLog(this@apply)
            }
        }
    }

    private suspend fun createLog(message: String){
        kotlin.runCatching {
            val db = DBManager.getInstance(context)
            val dao = db.logDao
            dao.insert(LogItem(
                id = 0L,
                title = "APP CRASH",
                content = message,
                format = Format("", "", "", "", "", 0, "", "", "", "", "", ""),
                downloadType = DownloadViewModel.Type.command,
                downloadTime = System.currentTimeMillis()
            ))
        }
        exitProcess(0)
    }

    fun registerExceptionHandler(){
        Thread.setDefaultUncaughtExceptionHandler(this)
    }
}