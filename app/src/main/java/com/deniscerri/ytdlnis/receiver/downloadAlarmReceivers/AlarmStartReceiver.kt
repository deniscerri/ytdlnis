package com.deniscerri.ytdlnis.receiver.downloadAlarmReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.work.DownloadWorker
import java.util.concurrent.TimeUnit

class AlarmStartReceiver : BroadcastReceiver() {
    override fun onReceive(p0: Context?, p1: Intent?) {
        val workConstraints = Constraints.Builder()
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .addTag("download")
            .setConstraints(workConstraints.build())
            .setInitialDelay(1000L, TimeUnit.MILLISECONDS)

        WorkManager.getInstance(p0!!).enqueueUniqueWork(
            System.currentTimeMillis().toString(),
            ExistingWorkPolicy.REPLACE,
            workRequest.build()
        )
    }

}