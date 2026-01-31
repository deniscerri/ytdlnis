package com.deniscerri.ytdl.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters


class AutoGenerateWebPoTokenWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        return Result.success()
    }

    companion object {
        const val TAG = "AutoGenerateWebPoTokenWorker"
    }

}