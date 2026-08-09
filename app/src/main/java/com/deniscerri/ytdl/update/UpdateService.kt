package com.deniscerri.ytdl.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.deniscerri.ytdl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import java.io.File

/**
 * Foreground service that runs one APK download to completion, independently of the UI: it survives
 * the update dialog closing and the app being backgrounded. Progress is published to
 * [UpdateRegistry] (for the dialog) and mirrored to a notification with a Stop action (for the
 * shade). There is only ever one update download, so no per-item keying is needed.
 */
class UpdateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() requires startForeground() within 5s on every delivery.
        goForeground(progressNotification(UpdateRegistry.stateOf()))

        when {
            intent?.action == ACTION_CANCEL -> cancelDownload()
            job?.isActive == true           -> Unit            // busy — ignore the new request
            else                            -> startDownload()
        }
        return START_STICKY
    }

    private fun startDownload() {
        val update = UpdateRegistry.available.value ?: run { stopNow(); return }
        UpdateRegistry.update(UpdateState.Connecting)
        job = ApkDownloader.download(applicationContext, update)
            .onEach { state ->
                UpdateRegistry.update(state)
                when (state) {
                    UpdateState.Connecting, is UpdateState.Downloading ->
                        notify(NOTIF_PROGRESS, progressNotification(state))
                    else -> Unit
                }
            }
            .onCompletion { cause ->
                job = null
                // Normal completion (Downloaded/Error) leaves a dismissible result; a cancellation
                // (cause != null) leaves nothing behind.
                if (cause == null) postResult()
                stopNow()
            }
            .launchIn(scope)
    }

    private fun cancelDownload() {
        job?.cancel(); job = null
        UpdateRegistry.update(UpdateState.Idle)
        stopNow()
    }

    /** Removes the ongoing notification and stops the service. Safe to call more than once. */
    private fun stopNow() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun postResult() {
        when (val s = UpdateRegistry.stateOf()) {
            is UpdateState.Downloaded -> notify(NOTIF_RESULT, result(s.apk))
            is UpdateState.Error      -> notify(NOTIF_RESULT, error(s.message))
            else                      -> Unit
        }
    }

    private fun base(): NotificationCompat.Builder {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground_large)
            .setContentTitle(getString(R.string.update_notif_title))
            .setOnlyAlertOnce(true)
            .setSilent(true)
    }

    private fun progressNotification(state: UpdateState): Notification {
        val downloading = state as? UpdateState.Downloading
        val log = downloading?.log?.ifBlank { null } ?: getString(R.string.update_connecting)
        val progress = downloading?.progress
        return base()
            .setContentText(log)
            .setOngoing(true)
            .setProgress(100, ((progress ?: 0f) * 100).toInt(), progress == null)
            .addAction(0, getString(R.string.update_stop), cancelIntent())
            .build()
    }

    /** Completion notification: tapping it launches the system installer for the downloaded APK. */
    private fun result(apk: File): Notification =
        base()
            .setContentText(getString(R.string.update_notif_ready))
            .setContentIntent(installIntent(apk))
            .setAutoCancel(true)
            .build()

    private fun error(message: String): Notification =
        base().setContentText(message).setAutoCancel(true).build()

    private fun installIntent(apk: File): PendingIntent =
        PendingIntent.getActivity(
            this, 0, UpdateInstaller.installIntent(this, apk),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun cancelIntent(): PendingIntent {
        val intent = Intent(this, UpdateService::class.java).setAction(ACTION_CANCEL)
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.update_notif_channel),
                    NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            )
        }
    }

    private fun notify(id: Int, notification: Notification) {
        val nm = NotificationManagerCompat.from(this)
        if (nm.areNotificationsEnabled()) nm.notify(id, notification)
    }

    private fun goForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIF_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIF_PROGRESS, notification)
        }
    }

    companion object {
        private const val CHANNEL = "app_update"
        private const val NOTIF_PROGRESS = 4200
        private const val NOTIF_RESULT = 4201
        private const val ACTION_CANCEL = "com.deniscerri.ytdl.action.CANCEL_UPDATE"

        /** Starts the APK download for the update in [UpdateRegistry] (no-op if already running). */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, UpdateService::class.java))
        }
    }
}
