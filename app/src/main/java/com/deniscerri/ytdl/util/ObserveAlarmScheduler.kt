package com.deniscerri.ytdl.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.WorkManager
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.deniscerri.ytdl.receiver.ObserveSourceAlarmReceiver
import com.deniscerri.ytdl.util.Extensions.calculateNextTimeForObserving
import kotlin.jvm.java

class ObserveAlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    private fun pendingIntent(id: Long) = PendingIntent.getBroadcast(
        context, id.toInt(),
        Intent(context, ObserveSourceAlarmReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(item: ObserveSourcesItem) {
        val at = item.calculateNextTimeForObserving()
        val pi = pendingIntent(item.id)
        // exact if allowed, else fall back to inexact-but-Doze-friendly
        if (canScheduleExact()) {
            alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(id: Long) {
        runCatching { alarmManager?.cancel(pendingIntent(id)) }
        WorkManager.getInstance(context).cancelUniqueWork("OBSERVE$id")
    }

    private fun canScheduleExact() =
        Build.VERSION.SDK_INT < 31 || alarmManager?.canScheduleExactAlarms() == true
}