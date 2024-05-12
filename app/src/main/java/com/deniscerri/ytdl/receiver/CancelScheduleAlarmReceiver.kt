package com.deniscerri.ytdl.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deniscerri.ytdl.work.CancelScheduledDownloadWorker
import com.deniscerri.ytdl.work.DownloadWorker
import java.util.concurrent.TimeUnit

class CancelScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context?, p1: Intent?) {
        ctx?.apply {
            val workConstraints = Constraints.Builder()
            val workRequest2 = OneTimeWorkRequestBuilder<CancelScheduledDownloadWorker>()
                .addTag("cancelScheduledDownload")
                .setConstraints(workConstraints.build())
                .setInitialDelay( 0L, TimeUnit.MILLISECONDS)

            WorkManager.getInstance(this).enqueueUniqueWork(
                System.currentTimeMillis().toString(),
                ExistingWorkPolicy.REPLACE,
                workRequest2.build()
            )
        }


    }
}