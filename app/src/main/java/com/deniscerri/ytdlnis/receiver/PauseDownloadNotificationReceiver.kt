package com.deniscerri.ytdlnis.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.yausername.youtubedl_android.YoutubeDL

class PauseDownloadNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val id = intent.getIntExtra("workID", 0)
        if (id != 0) {
            runCatching {
                val title = intent.getStringExtra("title")
                val notificationUtil = NotificationUtil(c)
                notificationUtil.cancelDownloadNotification(id)
                YoutubeDL.getInstance().destroyProcessById(id.toString())
                notificationUtil.createResumeDownload(id, title, NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID)
            }
        }
    }
}