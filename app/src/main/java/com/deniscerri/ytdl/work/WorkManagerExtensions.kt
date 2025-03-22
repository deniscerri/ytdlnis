package com.deniscerri.ytdl.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.delay

val Context.workManager: WorkManager
    get() = WorkManager.getInstance(this)

fun WorkManager.isRunning(tag: String): Boolean {
    val list = this.getWorkInfosByTag(tag).get()
    return list.count { it.state == WorkInfo.State.RUNNING } > 1
}

/**
 * Makes this worker run in the context of a foreground service.
 *
 * Note that this function is a no-op if the process is subject to foreground
 * service restrictions.
 *
 * Moving to foreground service context requires the worker to run a bit longer,
 * allowing Service.startForeground() to be called and avoiding system crash.
 */
suspend fun CoroutineWorker.setForegroundSafely() {
    try {
        setForeground(getForegroundInfo())
        delay(500)
    } catch (e: IllegalStateException) {
        Log.e("ERROR", "Not allowed to set foreground job")
    }
}