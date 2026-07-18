package com.deniscerri.ytdl.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deniscerri.ytdl.work.background.ObserveSourceWorker
import com.deniscerri.ytdl.work.networkConstraints

class ObserveSourceAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context?, intent: Intent?) {
        val ctx = ctx ?: return
        val id = intent?.getLongExtra("id", 0L) ?: 0L
        if (id == 0L) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val work = OneTimeWorkRequestBuilder<ObserveSourceWorker>()
            .addTag("observeSources")
            .addTag(id.toString())
            .setConstraints(networkConstraints(prefs))
            .setInputData(Data.Builder().putLong("id", id).build())
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniqueWork("OBSERVE$id", ExistingWorkPolicy.REPLACE, work)
    }
}