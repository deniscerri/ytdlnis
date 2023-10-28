package com.deniscerri.ytdlnis.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

class CancelWorkReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val id = intent.getStringExtra("workTag")
        if (!id.isNullOrBlank()){
            WorkManager.getInstance(c).cancelAllWorkByTag(id)
        }
    }
}