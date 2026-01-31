package com.deniscerri.ytdl.work

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.receiver.CancelScheduleAlarmReceiver
import com.deniscerri.ytdl.receiver.ScheduleAlarmReceiver
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager


    fun scheduleAt(at: Long) {
        alarmManager?.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            at,
            PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, ScheduleAlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }


    @SuppressLint("ScheduleExactAlarm")
    fun schedule() {
        cancel()

        //schedule starting alarm
        val startingTime = preferences.getString("schedule_start", "00:00")!!
        val sTime = Calendar.getInstance()
        sTime.set(Calendar.HOUR_OF_DAY, startingTime.split(":")[0].toInt())
        sTime.set(Calendar.MINUTE, startingTime.split(":")[1].toInt())
        sTime.set(Calendar.SECOND, 0)
        val time = calculateNextTime(sTime)

        //schedule starting work
        alarmManager?.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            time.timeInMillis,
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, ScheduleAlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        //schedule closing work
        val endingTime = preferences.getString("schedule_end", "05:00")!!
        val eTime = Calendar.getInstance()
        eTime.set(Calendar.HOUR_OF_DAY, endingTime.split(":")[0].toInt())
        eTime.set(Calendar.MINUTE, endingTime.split(":")[1].toInt())
        sTime.set(Calendar.SECOND, 0)
        val calendar = calculateNextTime(eTime)

        alarmManager?.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, CancelScheduleAlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    fun cancel() {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getService(context, 0, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)


        val cancelIntent = Intent(context, CancelScheduleAlarmReceiver::class.java)
        val cancelPendingIntent = PendingIntent.getService(context, 0, cancelIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)

        kotlin.runCatching {
            alarmManager?.cancel(pendingIntent)
            alarmManager?.cancel(cancelPendingIntent)
        }
    }

    private fun calculateNextTime(c: Calendar) : Calendar {
        val calendar = Calendar.getInstance()
        if (c.get(Calendar.HOUR_OF_DAY) < calendar.get(Calendar.HOUR_OF_DAY)){
            c.add(Calendar.DATE, 1)
        }else if (
            c.get(Calendar.HOUR_OF_DAY) == calendar.get(Calendar.HOUR_OF_DAY) &&
            c.get(Calendar.MINUTE) < calendar.get(Calendar.MINUTE)
            ){
            c.add(Calendar.DATE, 1)
        }
        return c
    }

    fun isDuringTheScheduledTime(): Boolean{
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)

        val startingTime = preferences.getString("schedule_start", "00:00")!!
        val startingHour = startingTime.split(":")[0].toInt()
        val startingMinute = startingTime.split(":")[1].toInt()

        val endingTime = preferences.getString("schedule_end", "05:00")!!
        var endingHour = endingTime.split(":")[0].toInt()
        if (endingHour < 12 && endingHour < startingHour){
            endingHour += 24
        }
        val endingMinute = endingTime.split(":")[1].toInt()

        if (currentHour in startingHour..endingHour){
            if (currentHour == endingHour){
                if (currentMinute > endingMinute) {
                    return false
                }
            }else if(currentHour == startingHour){
                if (currentMinute < startingMinute){
                    return false
                }
            }
            return true
        }

        return false
    }

    fun canSchedule() : Boolean {
        return if (Build.VERSION.SDK_INT >= 31){
            alarmManager?.canScheduleExactAlarms() == true
        }else {
            false
        }
    }
}