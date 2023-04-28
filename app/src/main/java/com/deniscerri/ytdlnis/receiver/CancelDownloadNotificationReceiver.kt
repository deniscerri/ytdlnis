package com.deniscerri.ytdlnis.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.yausername.youtubedl_android.YoutubeDL

class CancelDownloadNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val message = intent.getStringExtra("cancel")
        val id = intent.getIntExtra("workID", 0)
        if (message != null) {
            val notificationUtil = NotificationUtil(c)

            YoutubeDL.getInstance().destroyProcessById(id.toString())
            notificationUtil.cancelDownloadNotification(id)
        }
    }
}