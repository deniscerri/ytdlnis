package com.deniscerri.ytdl.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.util.NotificationUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CancelDownloadNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val id = intent.getIntExtra("itemID", 0)
        if (id > 0) {
            runCatching {
                val notificationUtil = NotificationUtil(c)
                RuntimeManager.getInstance().destroyProcessById(id.toString())
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