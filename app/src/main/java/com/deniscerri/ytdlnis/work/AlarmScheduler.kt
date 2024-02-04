package com.deniscerri.ytdlnis.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.receiver.downloadAlarmReceivers.AlarmStartReceiver
import com.deniscerri.ytdlnis.receiver.downloadAlarmReceivers.AlarmStopReceiver
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun schedule() {
        cancel()

        //schedule starting alarm
        val startingTime = preferences.getString("schedule_start", "00:00")!!
        val sTime = Calendar.getInstance()
        sTime.set(Calendar.HOUR_OF_DAY, startingTime.split(":")[0].toInt())
        sTime.set(Calendar.MINUTE, startingTime.split(":")[1].toInt())
        sTime.set(Calendar.SECOND, 0)
        val time = calculateNextTime(sTime)

        val intent = Intent(context, AlarmStartReceiver::class.java)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC,
            time.timeInMillis,
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE )
        )

        //schedule closing alarm
        val endingTime = preferences.getString("schedule_end", "05:00")!!
        val eTime = Calendar.getInstance()
        eTime.set(Calendar.HOUR_OF_DAY, endingTime.split(":")[0].toInt())
        eTime.set(Calendar.MINUTE, endingTime.split(":")[1].toInt())
        sTime.set(Calendar.SECOND, 0)
        val calendar = calculateNextTime(eTime)


        val intent2 = Intent(context, AlarmStopReceiver::class.java)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC,
            calendar.timeInMillis + 60000,
            PendingIntent.getBroadcast(context, 1, intent2, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE )
        )
    }

    fun cancel() {
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, AlarmStartReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, AlarmStopReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    fun calculateNextTime(c: Calendar) : Calendar {
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
}