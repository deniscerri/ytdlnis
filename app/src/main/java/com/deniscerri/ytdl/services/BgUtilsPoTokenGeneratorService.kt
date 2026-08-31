package com.deniscerri.ytdl.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.ui.more.terminal.TerminalActivity
import com.deniscerri.ytdl.util.BgUtilsPoTokenGeneratorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class BgUtilsPoTokenGeneratorService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentRunningProcess = "bgutils_po_token_generator"
    private val notificationCode = 44444

    inner class SessionBinder : Binder() {
        fun getService(): BgUtilsPoTokenGeneratorService = this@BgUtilsPoTokenGeneratorService
    }

    private val binder = SessionBinder()
    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        RuntimeManager.getInstance().destroyProcessById(currentRunningProcess)
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(notificationCode, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(notificationCode, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runtimeManager = RuntimeManager.getInstance()

        if (intent?.action == "ACTION_EXIT") {
            runtimeManager.destroyProcessById(currentRunningProcess)
            stopSelf()
            return super.onStartCommand(intent, flags, startId)
        }

        serviceScope.launch {
            runCatching {
                val serverFolder = BgUtilsPoTokenGeneratorUtil.getServerFolder(App.instance)
                runtimeManager.destroyProcessById(currentRunningProcess)

                if (runtimeManager.nodeLocation.isAvailable) {
                    runtimeManager.executeNode(
                        command = "build/main.js",
                        processId = currentRunningProcess,
                        executeDirectory = File(serverFolder, "server")
                    ) { _, _, line ->
                        Log.e("BGUTILS_POT", line)
                        val notification = createNotification(line)
                        notificationManager.notify(notificationCode, notification)
                    }

                } else {
                    runtimeManager.executeDeno(
                        command = "run -A src/main.ts",
                        processId = currentRunningProcess,
                        executeDirectory = File(serverFolder, "server")
                    ) { _, _, line ->
                        Log.e("BGUTILS_POT", line)
                        val notification = createNotification(line)
                        notificationManager.notify(notificationCode, notification)
                    }
                }


            }.onFailure { err ->
                Log.e("BGUTILS_POT", err.message ?: "")
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun createNotification(description: String = ""): Notification {
        val intent = Intent(this, TerminalActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val exitIntent = Intent(this, BgUtilsPoTokenGeneratorService::class.java).apply {
            action = "ACTION_EXIT"
        }
        val exitPendingIntent = PendingIntent.getService(
            this, 1, exitIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BgUtils POT Provider\n")
            .setContentText(description)
            .setSmallIcon(R.drawable.ic_terminal)
            .setContentIntent(pendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    "EXIT",
                    exitPendingIntent
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    private val CHANNEL_ID = "bgutils_potoken_service_channel"

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BgUtils POT Provider Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for BgUtils POT Generator Server"
        }
        notificationManager.createNotificationChannel(channel)
    }
}