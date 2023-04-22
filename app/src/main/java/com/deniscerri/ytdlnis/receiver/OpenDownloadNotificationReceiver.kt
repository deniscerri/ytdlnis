package com.deniscerri.ytdlnis.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.yausername.youtubedl_android.YoutubeDL

class OpenDownloadNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val message = intent.getStringExtra("open")
        val path = intent.getStringExtra("path")
        val notificationId = intent.getIntExtra("notificationID", 0)
        if (message != null && path != null) {
            if (notificationId != 0) NotificationUtil(c).cancelDownloadNotification(notificationId)
            val uiUtil = UiUtil(FileUtil())
            uiUtil.openFileIntent(c, path)
        }
    }
}