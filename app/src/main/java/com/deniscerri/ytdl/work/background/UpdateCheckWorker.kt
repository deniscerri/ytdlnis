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
import com.deniscerri.ytdl.update.UpdateChecker
import com.deniscerri.ytdl.update.UpdateRegistry
import com.deniscerri.ytdl.update.UpdateStore
import com.deniscerri.ytdl.util.NotificationUtil
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val notificationUtil = NotificationUtil(App.instance)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        // Saving the manifest here is what lets the prompt be waiting the next time the app is
        // opened, whether or not the notification was ever tapped.
        if (BuildConfig.FLAVOR == "github") {
            UpdateChecker.check()?.let {
                UpdateStore.save(context, it)
                UpdateRegistry.setAvailable(it)
                notificationUtil.showNewAppUpdate(it.versionName)
            }
        }

        val skipRemindingPackageUpdate = sharedPreferences.getStringSet("skip_reminding_package_update", setOf())!!.toMutableSet()
        RuntimeManager.packages.forEach { pkg ->
            val instance = pkg.plugin.getInstance()
            if (instance.bundledVersion.isNullOrBlank() && instance.downloadedVersion.isNullOrBlank()) return@forEach
            instance.getReleases().apply {
                val releases = this.getOrElse { listOf() }
                if (releases.isEmpty()) return@apply

                val latestRelease = releases.first()
                if (latestRelease.isBundled || latestRelease.isInstalled) return@apply
                if (skipRemindingPackageUpdate.contains(latestRelease.tag_name)) return@apply

                notificationUtil.showNewPackageUpdate(pkg.title, latestRelease.tag_name)

                skipRemindingPackageUpdate.add(latestRelease.tag_name)
                sharedPreferences.edit().putStringSet("skip_reminding_package_update", skipRemindingPackageUpdate).apply()
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