package com.deniscerri.ytdlnis.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.work.DownloadWorker
import com.deniscerri.ytdlnis.work.ObserveSourceWorker
import java.util.concurrent.TimeUnit

class ObserveAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(p0: Context?, p1: Intent?) {
        p1?.apply {
            val sourceID = p1.getLongExtra("id", 0)
            if (sourceID == 0L) return

            val workConstraints = Constraints.Builder()
            val workRequest = OneTimeWorkRequestBuilder<ObserveSourceWorker>()
                .addTag("observeSources")
                .addTag(sourceID.toString())
                .setConstraints(workConstraints.build())
                .setInitialDelay(1000L, TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putLong("id", sourceID).build())

            WorkManager.getInstance(p0!!).enqueueUniqueWork(
                sourceID.toString(),
                ExistingWorkPolicy.REPLACE,
                workRequest.build()
            )
        }

    }

}