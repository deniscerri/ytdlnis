package com.deniscerri.ytdl.work.background

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.UpdateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val updateUtil = UpdateUtil(App.instance)
        val notificationUtil = NotificationUtil(App.instance)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        val checkUpdatesApp = sharedPreferences.getBoolean("update_app", true)
        val checkUpdatesYTDL = sharedPreferences.getBoolean("auto_update_ytdlp", true)

        if (checkUpdatesApp) {
            val newRelease = updateUtil.tryGetNewVersion()
            if (newRelease.isSuccess && BuildConfig.FLAVOR == "github") {
                notificationUtil.showNewAppUpdate(newRelease.getOrNull()!!.tag_name)
            }
        }

        if (checkUpdatesYTDL) {
            val res = updateUtil.updateYTDL()
            when (res.status) {
                UpdateUtil.YTDLPUpdateStatus.DONE -> {
                    withContext(Dispatchers.IO) {
                        File(FileUtil.getInfoJsonPath(context)).deleteRecursively()
                    }
                }

                else -> {}
            }
        }

        return Result.success()
    }

    companion object {

        const val WORK_NAME = "ytdlnis_update_check"

        fun schedule(context: Context, intervalMinutes: Long = 24 * 60L) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

}