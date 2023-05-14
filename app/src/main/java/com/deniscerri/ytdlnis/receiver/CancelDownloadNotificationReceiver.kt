package com.deniscerri.ytdlnis.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.yausername.youtubedl_android.YoutubeDL

class CancelDownloadNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val message = intent.getStringExtra("cancel")
        val id = intent.getIntExtra("workID", 0)
        if (message != null) {
            val notificationUtil = NotificationUtil(c)
            notificationUtil.cancelDownloadNotification(id)
            YoutubeDL.getInstance().destroyProcessById(id.toString())
        }
    }
}