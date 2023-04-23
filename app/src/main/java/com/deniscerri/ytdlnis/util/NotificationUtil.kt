package com.deniscerri.ytdlnis.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.receiver.CancelDownloadNotificationReceiver
import com.deniscerri.ytdlnis.receiver.OpenDownloadNotificationReceiver
import com.deniscerri.ytdlnis.receiver.SharedDownloadNotificationReceiver
import com.deniscerri.ytdlnis.ui.more.downloadLogs.DownloadLogActivity

class NotificationUtil(var context: Context) {
    private val downloadNotificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context, DOWNLOAD_SERVICE_CHANNEL_ID)
    private val commandDownloadNotificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context, COMMAND_DOWNLOAD_SERVICE_CHANNEL_ID)
    private val finishedDownloadNotificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context, DOWNLOAD_FINISHED_CHANNEL_ID)
    private val notificationManager: NotificationManager = context.getSystemService(NotificationManager::class.java)

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(
                NotificationManager::class.java
            )

            //gui downloads
            var name: CharSequence = context.getString(R.string.download_notification_channel_name)
            var description = context.getString(R.string.download_notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            var channel = NotificationChannel(DOWNLOAD_SERVICE_CHANNEL_ID, name, importance)
            channel.description = description
            notificationManager.createNotificationChannel(channel)

            //command downloads
            name = context.getString(R.string.command_download_notification_channel_name)
            description =
                context.getString(R.string.command_download_notification_channel_description)
            channel = NotificationChannel(COMMAND_DOWNLOAD_SERVICE_CHANNEL_ID, name, importance)
            channel.description = description
            notificationManager.createNotificationChannel(channel)

            //finished or errored downloads
            name = context.getString(R.string.finished_download_notification_channel_name)
            description =
                context.getString(R.string.finished_download_notification_channel_description)
            channel = NotificationChannel(DOWNLOAD_FINISHED_CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH)
            channel.description = description
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getBuilder(channel: String) : NotificationCompat.Builder {
        when(channel) {
            DOWNLOAD_SERVICE_CHANNEL_ID -> { return downloadNotificationBuilder}
            COMMAND_DOWNLOAD_SERVICE_CHANNEL_ID -> { return commandDownloadNotificationBuilder }
            DOWNLOAD_FINISHED_CHANNEL_ID -> { return finishedDownloadNotificationBuilder }
        }
        return downloadNotificationBuilder
    }


    fun createDownloadServiceNotification(
        pendingIntent: PendingIntent?,
        title: String?,
        workID: Int,
        channel: String
    ): Notification {
        val notificationBuilder = getBuilder(channel)

        val intent = Intent(context, CancelDownloadNotificationReceiver::class.java)
        intent.putExtra("cancel", "")
        intent.putExtra("workID", workID)
        val cancelNotificationPendingIntent = PendingIntent.getBroadcast(
            context,
            workID,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        return notificationBuilder
            .setContentTitle(title)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    context.resources,
                    android.R.drawable.stat_sys_download
                )
            )
            .setContentText("")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(PROGRESS_MAX, PROGRESS_CURR, false)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .clearActions()
            .addAction(0, context.getString(R.string.cancel), cancelNotificationPendingIntent)
            .build()
    }

    fun createDownloadFinished(title: String?,
        filepath: String?,
        channel: String
    ) {
        val notificationBuilder = getBuilder(channel)

        val openIntent = Intent(context, OpenDownloadNotificationReceiver::class.java)
        openIntent.putExtra("open", "")
        openIntent.putExtra("path", filepath)
        openIntent.putExtra("notificationID", DOWNLOAD_FINISHED_NOTIFICATION_ID)
        val openNotificationPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val shareIntent = Intent(context, SharedDownloadNotificationReceiver::class.java)
        shareIntent.putExtra("share", "")
        shareIntent.putExtra("path", filepath)
        openIntent.putExtra("notificationID", DOWNLOAD_FINISHED_NOTIFICATION_ID)
        val shareNotificationPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            shareIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        notificationBuilder
            .setContentTitle("${context.getString(R.string.downloaded)} $title")
            .setSmallIcon(R.drawable.ic_app_icon)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.ic_app_icon
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .clearActions()
        if (filepath != null){
            notificationBuilder.addAction(0, context.getString(R.string.Open_File), openNotificationPendingIntent)
            notificationBuilder.addAction(0, context.getString(R.string.share), shareNotificationPendingIntent)
        }
        notificationManager.notify(DOWNLOAD_FINISHED_NOTIFICATION_ID, notificationBuilder.build())
    }

    fun createDownloadErrored(title: String?,
                               error: String?,
                               logFilePath: String?,
                               channel: String
    ) {
        val notificationBuilder = getBuilder(channel)

        val intent = Intent(context, DownloadLogActivity::class.java)
        intent.putExtra("path", logFilePath)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val errorPendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        notificationBuilder
            .setContentTitle("${context.getString(R.string.failed_download)}: $title")
            .setContentText(error)
            .setContentIntent(errorPendingIntent)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.ic_app_icon
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .clearActions()
        if (logFilePath != null){
            notificationBuilder.addAction(0, context.getString(R.string.logs), errorPendingIntent)
        }
        notificationManager.notify(DOWNLOAD_FINISHED_NOTIFICATION_ID, notificationBuilder.build())
    }

    fun updateDownloadNotification(
        id: Int,
        desc: String,
        progress: Int,
        queue: Int,
        title: String?,
        channel : String
    ) {

        val notificationBuilder = getBuilder(channel)
        var contentText = ""
        if (queue > 1) contentText += """${queue - 1} ${context.getString(R.string.items_left)}""" + "\n"
        contentText += desc.replace("\\[.*?\\] ".toRegex(), "")
        try {
            notificationBuilder.setProgress(100, progress, false)
                .setContentTitle(title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            notificationManager.notify(id, notificationBuilder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelDownloadNotification(id: Int) {
        notificationManager.cancel(id)
    }

    companion object {
        const val DOWNLOAD_SERVICE_CHANNEL_ID = "1"
        const val COMMAND_DOWNLOAD_SERVICE_CHANNEL_ID = "2"
        const val DOWNLOAD_FINISHED_CHANNEL_ID = "3"
        const val DOWNLOAD_FINISHED_NOTIFICATION_ID = 3
        const val FILE_TRANSFER_CHANNEL_ID = "3"
        private const val PROGRESS_MAX = 100
        private const val PROGRESS_CURR = 0
    }
}