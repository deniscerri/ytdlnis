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
import com.deniscerri.ytdlnis.receiver.NotificationReceiver

class NotificationUtil(var context: Context) {
    private val downloadNotificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context, DOWNLOAD_SERVICE_CHANNEL_ID)
    private val commandDownloadNotificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context, COMMAND_DOWNLOAD_SERVICE_CHANNEL_ID)
    private val fileTransferNotificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context, FILE_TRANSFER_CHANNEL_ID)
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

            //file transfers
            name = context.getString(R.string.file_transfer_notification_channel_name)
            description = context.getString(R.string.file_transfer_notification_channel_description)
            channel = NotificationChannel(FILE_TRANSFER_CHANNEL_ID, name, importance)
            channel.description = description
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun createFileTransferNotification(
        pendingIntent: PendingIntent?,
        title: String?,
    ) : Notification {
        val intent = Intent(context, NotificationReceiver::class.java)

        return fileTransferNotificationBuilder
            .setContentTitle(title)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentText("")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(PROGRESS_MAX, PROGRESS_CURR, false)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun updateFileTransferNotification(
        id: Int,
        progress: Int
    ) {
        try {
            fileTransferNotificationBuilder.setProgress(100, progress, false)
            notificationManager.notify(id, fileTransferNotificationBuilder.build())
        } catch (ignored: Exception) {
        }
    }

    private fun getBuilder(channel: String) : NotificationCompat.Builder {
        when(channel) {
            DOWNLOAD_SERVICE_CHANNEL_ID -> { return downloadNotificationBuilder}
            COMMAND_DOWNLOAD_SERVICE_CHANNEL_ID -> { return commandDownloadNotificationBuilder }
            FILE_TRANSFER_CHANNEL_ID -> { return fileTransferNotificationBuilder }
        }
        return downloadNotificationBuilder
    }


    fun createDownloadServiceNotification(
        pendingIntent: PendingIntent?,
        title: String?,
        channel: String
    ): Notification {
        val notificationBuilder = getBuilder(channel)

        val intent = Intent(context, NotificationReceiver::class.java)
        intent.putExtra("cancel", "")
        val cancelNotificationPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
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
        const val DOWNLOAD_NOTIFICATION_ID = 1
        const val COMMAND_DOWNLOAD_SERVICE_CHANNEL_ID = "2"
        const val COMMAND_DOWNLOAD_NOTIFICATION_ID = 2
        const val FILE_TRANSFER_CHANNEL_ID = "3"
        private const val PROGRESS_MAX = 100
        private const val PROGRESS_CURR = 0
    }
}