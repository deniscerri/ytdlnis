package com.deniscerri.ytdlnis.receiver

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.util.UiUtil

class ShareFileService : Service() {
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val paths = intent.getStringArrayExtra("path")
        val notificationId = intent.getIntExtra("notificationID", 0)
        NotificationUtil(this).cancelDownloadNotification(notificationId)
        UiUtil.shareFileIntent(this, paths!!.toList())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
    }
}