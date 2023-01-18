package com.deniscerri.ytdlnis.receiver

import android.content.*
import androidx.work.WorkManager

class CancelDownloadNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val message = intent.getStringExtra("cancel")
        val id = intent.getIntExtra("workID", 0)
        if (message != null) {
            WorkManager.getInstance(c).cancelAllWorkByTag(id.toString())
        }
    }
}