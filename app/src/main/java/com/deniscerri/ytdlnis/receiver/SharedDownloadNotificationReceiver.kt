package com.deniscerri.ytdlnis.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.util.UiUtil

class SharedDownloadNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val message = intent.getStringExtra("share")
        val path = intent.getStringExtra("path")
        val notificationId = intent.getIntExtra("notificationID", 0)
        if (message != null && path != null) {
            if (notificationId != 0) NotificationUtil(c).cancelDownloadNotification(notificationId)
            val uiUtil = UiUtil(FileUtil())
            uiUtil.shareFileIntent(c, listOf(path))
        }
    }
}