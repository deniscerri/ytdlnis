package com.deniscerri.ytdlnis.receiver

import android.content.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class CancelDownloadNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val message = intent.getStringExtra("cancel")
        val id = intent.getIntExtra("workID", 0)
        if (message != null) {
            val notificationUtil = NotificationUtil(c)

            YoutubeDL.getInstance().destroyProcessById(id.toString());
            WorkManager.getInstance(c).cancelUniqueWork(id.toString())
            notificationUtil.cancelDownloadNotification(id)
        }
    }
}