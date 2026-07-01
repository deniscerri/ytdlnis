package com.deniscerri.ytdl.util

import android.content.Context
import android.util.Log
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.LogItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class CrashListener(private val context: Context) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(p0: Thread, p1: Throwable) {
        Log.e("YTDLnisCrash", buildCrashMessage(p1), p1)
        CoroutineScope(SupervisorJob()).launch(Dispatchers.IO) {
            createLog(buildCrashMessage(p1))
        }
    }

    private fun buildCrashMessage(throwable: Throwable): String {
        val messages = mutableListOf<String>()
        var current: Throwable? = throwable
        while (current != null) {
            messages.add("${current::class.java.name}: ${current.message}\n${current.stackTrace.joinToString("\n")}")
            current = current.cause
        }
        return messages.joinToString("\n\nCaused by:\n")
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
                downloadType = DownloadType.command,
                downloadTime = System.currentTimeMillis()
            ))
        }
        exitProcess(0)
    }

    fun registerExceptionHandler(){
        Thread.setDefaultUncaughtExceptionHandler(this)
    }
}
