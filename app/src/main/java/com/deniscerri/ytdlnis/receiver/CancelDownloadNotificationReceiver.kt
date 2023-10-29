package com.deniscerri.ytdlnis.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CancelDownloadNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val id = intent.getIntExtra("itemID", 0)
        if (id > 0) {
            runCatching {
                val notificationUtil = NotificationUtil(c)
                YoutubeDL.getInstance().destroyProcessById(id.toString())
                notificationUtil.cancelDownloadNotification(id)
                val dbManager = DBManager.getInstance(c)
                CoroutineScope(Dispatchers.IO).launch{
                    runCatching {
                        val item = dbManager.downloadDao.getDownloadById(id.toLong())
                        item.status = DownloadRepository.Status.Cancelled.toString()
                        dbManager.downloadDao.update(item)
                    }
                    runCatching {
                        dbManager.terminalDao.delete(id.toLong())
                    }
                }
            }

        }
    }
}