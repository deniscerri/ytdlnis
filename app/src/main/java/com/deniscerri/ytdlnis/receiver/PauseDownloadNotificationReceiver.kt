package com.deniscerri.ytdlnis.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.yausername.youtubedl_android.YoutubeDL

class PauseDownloadNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val id = intent.getIntExtra("workID", 0)
        if (id != 0) {
            val title = intent.getStringExtra("title")
            val notificationUtil = NotificationUtil(c)
            YoutubeDL.getInstance().destroyProcessById(id.toString())

            notificationUtil.cancelDownloadNotification(id)
            notificationUtil.createResumeDownload(id, title, NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID)
        }
    }
}