package com.deniscerri.ytdl.ui.more.terminal

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.WorkerEventBus
import com.deniscerri.ytdl.work.download.DownloadWorker
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Exception

class TerminalService : Service() {

    private val binder = LocalBinder()
    private lateinit var notificationUtil: NotificationUtil

    inner class LocalBinder : Binder() {
        fun getService(): TerminalService = this@TerminalService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        notificationUtil = NotificationUtil(this)

        val notification = notificationUtil.createDownloadServiceNotification(
            null,
            "Terminal session active",
            NotificationUtil.DOWNLOAD_TERMINAL_RUNNING_NOTIFICATION_ID
        )
        startForeground(NotificationUtil.DOWNLOAD_TERMINAL_RUNNING_NOTIFICATION_ID, notification)
    }

    fun startNewSession(id: Long, command: String): TerminalSession {
        val client = createSessionClient(id)
        val session = TerminalProcessFactory.createRuntimeSession(applicationContext, client)

        TerminalSessionManager.putSession(id, session)

        if (command.isNotBlank()) {
            val cmd = command.trim()
            session.write("$cmd\n")
        }

        return session
    }

    private fun createSessionClient(id: Long): TerminalSessionClient {
        return object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {
                val transcript = changedSession.emulator.screen.getTranscriptText()
                val lastLine = transcript.lines().takeLast(2).firstOrNull { it.isNotBlank() } ?: ""

                WorkerEventBus.post(
                    DownloadWorker.WorkerProgress(
                        progress = parseProgress(lastLine),
                        output = transcript,
                        downloadItemID = id,
                        logItemID = 0L
                    )
                )

                CoroutineScope(Dispatchers.IO).launch {
                    val dao = DBManager.getInstance(applicationContext).terminalDao
                    dao.updateLog(transcript, id)
                }
            }

            override fun onTitleChanged(changedSession: TerminalSession) {}

            override fun onSessionFinished(finishedSession: TerminalSession) {
                TerminalSessionManager.removeSession(id)
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = DBManager.getInstance(applicationContext).terminalDao
                    dao.delete(id)
                }
                checkStopSelf()
            }

            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
            override fun onPasteTextFromClipboard(session: TerminalSession) {}
            override fun onBell(session: TerminalSession) {}
            override fun onColorsChanged(session: TerminalSession) {}
            override fun onTerminalCursorStateChange(state: Boolean) {}

            // Fix: Return null or 0 (TerminalEmulator.CURSOR_STYLE_BLOCK) instead of throwing TODO()
            override fun getTerminalCursorStyle(): Int? = null

            // Fix: Provide safe no-op or android.util.Log implementations for logging callbacks
            override fun logError(tag: String?, message: String?) {
                android.util.Log.e(tag ?: "TerminalService", message ?: "")
            }

            override fun logWarn(tag: String?, message: String?) {
                android.util.Log.w(tag ?: "TerminalService", message ?: "")
            }

            override fun logInfo(tag: String?, message: String?) {
                android.util.Log.i(tag ?: "TerminalService", message ?: "")
            }

            override fun logDebug(tag: String?, message: String?) {
                android.util.Log.d(tag ?: "TerminalService", message ?: "")
            }

            override fun logVerbose(tag: String?, message: String?) {
                android.util.Log.v(tag ?: "TerminalService", message ?: "")
            }

            override fun logStackTraceWithMessage(
                tag: String?,
                message: String?,
                e: Exception?
            ) {
                android.util.Log.e(tag ?: "TerminalService", message ?: "", e)
            }

            override fun logStackTrace(tag: String?, e: Exception?) {
                android.util.Log.e(tag ?: "TerminalService", "", e)
            }
        }
    }

    private fun parseProgress(line: String): Int {
        val match = Regex("\\[download\\]\\s+([0-9]+(\\.[0-9]+)?)%").find(line)
        return match?.groupValues?.get(1)?.toFloatOrNull()?.toInt() ?: 0
    }

    private fun checkStopSelf() {
        if (TerminalSessionManager.getAllSessions().isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
}