package com.deniscerri.ytdl.work

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AlarmScheduler(private val context: Context) {

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

        //schedule starting work
        val workConstraints = Constraints.Builder()
        val workRequest = OneTimeWorkRequestBuilder<ObserveSourceWorker>()
            .addTag("scheduledDownload")
            .addTag("download")
            .setConstraints(workConstraints.build())
            .setInitialDelay(System.currentTimeMillis() - time.timeInMillis, TimeUnit.MILLISECONDS)

        WorkManager.getInstance(context).enqueueUniqueWork(
            System.currentTimeMillis().toString(),
            ExistingWorkPolicy.REPLACE,
            workRequest.build()
        )

        //schedule closing work
        val endingTime = preferences.getString("schedule_end", "05:00")!!
        val eTime = Calendar.getInstance()
        eTime.set(Calendar.HOUR_OF_DAY, endingTime.split(":")[0].toInt())
        eTime.set(Calendar.MINUTE, endingTime.split(":")[1].toInt())
        sTime.set(Calendar.SECOND, 0)
        val calendar = calculateNextTime(eTime)


        val workRequest2 = OneTimeWorkRequestBuilder<CancelScheduledDownloadWorker>()
            .addTag("cancelScheduledDownload")
            .setConstraints(workConstraints.build())
            .setInitialDelay(System.currentTimeMillis() - calendar.timeInMillis + 60000, TimeUnit.MILLISECONDS)

        WorkManager.getInstance(context).enqueueUniqueWork(
            System.currentTimeMillis().toString(),
            ExistingWorkPolicy.REPLACE,
            workRequest2.build()
        )

    }

    fun cancel() {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag("scheduledDownload")
        wm.cancelAllWorkByTag("cancelScheduledDownload")
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