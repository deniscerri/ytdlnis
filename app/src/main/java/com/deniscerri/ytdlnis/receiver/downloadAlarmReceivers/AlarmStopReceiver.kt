package com.deniscerri.ytdlnis.receiver.downloadAlarmReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.work.AlarmScheduler
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmStopReceiver : BroadcastReceiver() {
    override fun onReceive(p0: Context?, p1: Intent?) {
        val dbManager = DBManager.getInstance(p0!!)
        val ytdl = YoutubeDL.getInstance()
        val notificationUtil = NotificationUtil(p0)
        val preferences = PreferenceManager.getDefaultSharedPreferences(p0)
        val alarmScheduler = AlarmScheduler(p0)

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val active = dbManager.downloadDao.getActiveDownloadsList()

                val startingTime = preferences.getString("schedule_start", "00:00")!!
                val sTime = Calendar.getInstance()
                sTime.set(Calendar.HOUR_OF_DAY, startingTime.split(":")[0].toInt())
                sTime.set(Calendar.MINUTE, startingTime.split(":")[1].toInt())
                sTime.set(Calendar.SECOND, 0)
                val time = alarmScheduler.calculateNextTime(sTime)

                active.forEach {
                    ytdl.destroyProcessById(it.id.toString())
                    notificationUtil.cancelDownloadNotification(it.id.toInt())
                    it.status = DownloadRepository.Status.Queued.toString()
                    dbManager.downloadDao.update(it)
                }

                AlarmScheduler(p0).schedule()
            }
        }
    }

}