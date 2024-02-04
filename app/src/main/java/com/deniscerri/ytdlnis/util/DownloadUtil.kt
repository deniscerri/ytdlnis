package com.deniscerri.ytdlnis.util

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Looper
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.ObserveSourcesItem
import com.deniscerri.ytdlnis.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdlnis.receiver.ObserveAlarmReceiver
import com.deniscerri.ytdlnis.util.Extensions.closestValue
import com.deniscerri.ytdlnis.work.DownloadWorker
import java.time.Month
import java.util.Calendar
import java.util.concurrent.TimeUnit


object DownloadUtil {

    @SuppressLint("RestrictedApi")
    suspend fun startDownloadWorker(queuedItems: List<DownloadItem>, context: Context) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val allowMeteredNetworks = sharedPreferences.getBoolean("metered_networks", true)
        val workManager = WorkManager.getInstance(context)

        val currentWork = workManager.getWorkInfosByTag("download").await()
        if (currentWork.size == 0 || currentWork.none{ it.state == WorkInfo.State.RUNNING } || (queuedItems.isNotEmpty() && queuedItems[0].downloadStartTime != 0L)){

            val currentTime = System.currentTimeMillis()
            var delay = 0L
            if (queuedItems.isNotEmpty()){
                delay = if (queuedItems[0].downloadStartTime != 0L){
                    queuedItems[0].downloadStartTime.minus(currentTime)
                } else 0
                if (delay <= 60000L) delay = 0L
            }


            val workConstraints = Constraints.Builder()
            if (!allowMeteredNetworks) workConstraints.setRequiredNetworkType(NetworkType.UNMETERED)

            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .addTag("download")
                .setConstraints(workConstraints.build())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)

            queuedItems.forEach {
                workRequest.addTag(it.id.toString())
            }

            workManager.enqueueUniqueWork(
                System.currentTimeMillis().toString(),
                ExistingWorkPolicy.REPLACE,
                workRequest.build()
            )

        }

        val isCurrentNetworkMetered = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
        if (!allowMeteredNetworks && isCurrentNetworkMetered){
            Looper.prepare().run {
                Toast.makeText(context, context.getString(R.string.metered_network_download_start_info), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun cancelObservationTaskByID(context: Context, id: Long){
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                id.toInt(),
                Intent(context, ObserveAlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

}