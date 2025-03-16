package com.deniscerri.ytdl.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.NotificationUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption


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