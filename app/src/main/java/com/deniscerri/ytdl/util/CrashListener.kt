package com.deniscerri.ytdl.util

import android.content.Context
import android.util.Log
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.LogItem
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class CrashListener(private val context: Context) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(p0: Thread, p1: Throwable) {
        p1.message?.apply {
            Log.e("ERROR", this)
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