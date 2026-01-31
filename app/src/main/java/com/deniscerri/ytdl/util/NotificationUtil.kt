package com.deniscerri.ytdl.util

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavDeepLinkBuilder
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.receiver.CancelDownloadNotificationReceiver
import com.deniscerri.ytdl.receiver.CancelWorkReceiver
import com.deniscerri.ytdl.receiver.PauseDownloadNotificationReceiver
import com.deniscerri.ytdl.receiver.ResumeActivity
import com.deniscerri.ytdl.util.Extensions.toBitmap
import java.io.File
import kotlin.random.Random


class NotificationUtil(var context: Context) {
    private val downloadNotificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context, DOWNLOAD_SERVICE_CHANNEL_ID)
    private val workerNotificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context, DOWNLOAD_WORKER_CHANNEL_ID)
    private val commandDownloadNotificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context, COMMAND_DOWNLOAD_SERVICE_CHANNEL_ID)
    private val finishedDownloadNotificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context, DOWNLOAD_FINISHED_CHANNEL_ID)
    private val erroredDownloadNotificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context, DOWNLOAD_ERRORED_CHANNEL_ID)
    private val miscDownloadNotificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context, DOWNLOAD_MISC_CHANNEL_ID)

    private val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(context)
    private val resources: Resources = context.resources

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //downloading worker notification
            var name: CharSequence = resources.getString(R.string.downloading)
            var description = "WorkManager Default Notification"
            val importance = NotificationManager.IMPORTANCE_LOW
            var channel = NotificationChannel(DOWNLOAD_WORKER_CHANNEL_ID, name, importance)
            channel.description = description
            channel.setSound(null, null)
            notificationManager.createNotificationChannel(channel)

            //gui downloads
            name = resources.getString(R.string.download_notification_channel_name)
            description = resources.getString(R.string.download_notification_channel_description)
            channel = NotificationChannel(DOWNLOAD_SERVICE_CHANNEL_ID, name, importance)
            channel.description = description
            notificationManager.createNotificationChannel(channel)

            //command downloads
            name = resources.getString(R.string.command_download_notification_channel_name)
            description =
                resources.getString(R.string.command_download_notification_channel_description)
            channel = NotificationChannel(COMMAND_DOWNLOAD_SERVICE_CHANNEL_ID, name, importance)
            channel.description = description
            notificationManager.createNotificationChannel(channel)

            //finished downloads
            name = resources.getString(R.string.finished_download_notification_channel_name)
            description =
                resources.getString(R.string.finished_download_notification_channel_description)
            channel = NotificationChannel(DOWNLOAD_FINISHED_CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH)
            channel.description = description
            notificationManager.createNotificationChannel(channel)

            //errored downloads
            name = resources.getString(R.string.errored_downloads)
            description =
                resources.getString(R.string.errored_download_notification_channel_description)
            channel = NotificationChannel(DOWNLOAD_ERRORED_CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH)
            channel.description = description
            notificationManager.createNotificationChannel(channel)

            //misc
            name = resources.getString(R.string.misc)
            description = ""
            channel = NotificationChannel(DOWNLOAD_MISC_CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH)
            channel.description = description
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getBuilder(channel: String) : NotificationCompat.Builder {
        when(channel) {
            DOWNLOAD_SERVICE_CHANNEL_ID -> { return downloadNotificationBuilder}
            COMMAND_DOWNLOAD_SERVICE_CHANNEL_ID -> { return commandDownloadNotificationBuilder }
            DOWNLOAD_FINISHED_CHANNEL_ID -> { return finishedDownloadNotificationBuilder }
            DOWNLOAD_WORKER_CHANNEL_ID -> { return workerNotificationBuilder }
            DOWNLOAD_ERRORED_CHANNEL_ID -> { return erroredDownloadNotificationBuilder }
            DOWNLOAD_MISC_CHANNEL_ID -> { return miscDownloadNotificationBuilder }
        }
        return downloadNotificationBuilder
    }

    fun createDefaultWorkerNotification() : Notification {
        val notificationBuilder = getBuilder(DOWNLOAD_WORKER_CHANNEL_ID)

        return notificationBuilder
            .setContentTitle(resources.getString(R.string.downloading))
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources,
                    android.R.drawable.stat_sys_download
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup(DOWNLOAD_RUNNING_NOTIFICATION_ID.toString())
            .setGroupSummary(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .clearActions()
            .build()
    }

    fun createObserveSourcesNotification(title: String) : Notification {
        val notificationBuilder = getBuilder(DOWNLOAD_WORKER_CHANNEL_ID)

        return notificationBuilder
            .setContentTitle(resources.getString(R.string.observe_sources))
            .setContentText(title)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_launcher_foreground_large)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.ic_launcher_foreground_large
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .clearActions()
            .build()
    }

    fun createDownloadServiceNotification(
        pendingIntent: PendingIntent?,
        title: String?,
        group : Int = DOWNLOAD_RUNNING_NOTIFICATION_ID
    ): Notification {
        val notificationBuilder = getBuilder(DOWNLOAD_SERVICE_CHANNEL_ID)

        return notificationBuilder
            .setContentTitle(title)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources,
                    android.R.drawable.stat_sys_download
                )
            )
            .setContentText("")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(PROGRESS_MAX, PROGRESS_CURR, true)
            .setContentIntent(pendingIntent)
            .setGroup(group.toString())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .clearActions()
            .build()
    }

    @SuppressLint("MissingPermission")
    fun createResumeDownload(itemID: Int, title: String?){
        val notificationBuilder = getBuilder(DOWNLOAD_SERVICE_CHANNEL_ID)

        notificationBuilder
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_launcher_foreground_large)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.ic_launcher_foreground_large
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .clearActions()

        val intent = Intent(context, ResumeActivity::class.java)
        intent.putExtra("itemID", itemID)
        val resumeNotificationPendingIntent = PendingIntent.getActivity(
            context,
            itemID,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        notificationBuilder.addAction(0, resources.getString(R.string.resume), resumeNotificationPendingIntent)
        notificationManager.notify(DOWNLOAD_RESUME_NOTIFICATION_ID + itemID, notificationBuilder.build())
    }

    @SuppressLint("MissingPermission")
    fun createUpdatingItemNotification(channel: String){
        val notificationBuilder = getBuilder(channel)

        notificationBuilder
            .setContentTitle(resources.getString(R.string.updating_download_data))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources,
                    android.R.drawable.stat_sys_download
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .clearActions()
        notificationManager.notify(DOWNLOAD_UPDATING_NOTIFICATION_ID, notificationBuilder.build())
    }

    @SuppressLint("MissingPermission")
    fun createDownloadFinished(
        id: Long,
        title: String?,
        downloadType: DownloadType,
        filepath: List<String>?,
        res: Resources
    ) {
        val notificationBuilder = getBuilder(DOWNLOAD_FINISHED_CHANNEL_ID)

        val iconType = when(downloadType){
            DownloadType.audio -> {
                R.drawable.ic_music
            }
            DownloadType.video -> {
                R.drawable.ic_video
            }
            DownloadType.command -> {
                R.drawable.ic_terminal
            }

            else -> R.drawable.ic_launcher_foreground_large
        }

        val contentText = StringBuilder("$title")

        val bitmap = iconType.toBitmap(context)
        notificationBuilder
            .setContentTitle("${res.getString(R.string.downloaded)} $title")
            .setSmallIcon(R.drawable.ic_launcher_foreground_large)
            .setLargeIcon(bitmap)
            .setGroup(DOWNLOAD_FINISHED_NOTIFICATION_ID.toString())
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .clearActions()
        if (filepath != null){
            contentText.append("\n\n"+ filepath.joinToString("\n"))
            try{
                val uris = filepath.mapNotNull {
                    runCatching {
                        DocumentFile.fromSingleUri(context, Uri.parse(it)).run{
                            if (this?.exists() == true){
                                this.uri
                            }else if (File(it).exists()){
                                FileProvider.getUriForFile(context, context.packageName + ".fileprovider",
                                    File(it))
                            }else null
                        }
                    }.getOrNull()
                }

                val openFileIntent = Intent()
                val shareFileIntent = Intent()

                if (uris.isNotEmpty()){
                    openFileIntent.apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        action = Intent.ACTION_VIEW
                        data = uris.first()
                    }

                    shareFileIntent.apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        action = Intent.ACTION_SEND_MULTIPLE
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                        type = if (uris.size == 1) uris[0].let { context.contentResolver.getType(it) } ?: "media/*" else "*/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                }

                val openNotificationPendingIntent: PendingIntent = TaskStackBuilder.create(context).run {
                    addNextIntentWithParentStack(openFileIntent)
                    getPendingIntent(id.toInt(),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                }

                //share intent
                val shareNotificationPendingIntent: PendingIntent = PendingIntent.getActivity(
                    context,
                    id.toInt(),
                    Intent.createChooser(shareFileIntent, res.getString(R.string.share)),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                notificationBuilder.addAction(0, res.getString(R.string.Open_File), openNotificationPendingIntent)
                notificationBuilder.addAction(0, res.getString(R.string.share), shareNotificationPendingIntent)
            }catch (_: Exception){}
        }
        notificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(contentText.toString().trimIndent()))
        notificationManager.notify(DOWNLOAD_FINISHED_NOTIFICATION_ID + id.toInt(), notificationBuilder.build())

        if (
            !notificationManager.activeNotifications.any { it.id == DOWNLOAD_FINISHED_NOTIFICATION_ID }
            && Build.VERSION.SDK_INT > 24
            && isNotificationChannelEnabled(DOWNLOAD_FINISHED_CHANNEL_ID)
        ) {
            //make summary notification
            val summaryNotification = getBuilder(DOWNLOAD_WORKER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground_large)
                .setLargeIcon(bitmap)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setGroup(DOWNLOAD_FINISHED_NOTIFICATION_ID.toString())
                .setGroupSummary(true)
                .build()
            notificationManager.notify(DOWNLOAD_FINISHED_NOTIFICATION_ID, summaryNotification)
        }
    }

    @SuppressLint("MissingPermission")
    fun createDownloadErrored(
        id: Long,
        title: String?,
        error: String?,
        logID: Long?,
        res: Resources
    ) {
        val notificationBuilder = getBuilder(DOWNLOAD_ERRORED_CHANNEL_ID)

        val bundle = Bundle()
        if (logID != null){
            bundle.putLong("logID", logID)
        }

        val errorPendingIntent = NavDeepLinkBuilder(context)
            .setGraph(R.navigation.nav_graph)
            .setDestination(R.id.downloadLogFragment)
            .setArguments(bundle)
            .createPendingIntent()

        val intent = Intent(context, MainActivity::class.java)
        intent.setAction(Intent.ACTION_VIEW)
        intent.putExtra("destination", "Queue")
        intent.putExtra("tab", "error")
        val errorTabPendingIntent = PendingIntent.getActivity(
            context,
            Random.nextInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val intent2 = Intent(context, MainActivity::class.java)
        intent2.setAction(Intent.ACTION_VIEW)
        intent2.putExtra("reconfigure", id)
        intent2.putExtra("tab", "error")
        intent2.putExtra("destination", "Queue")
        val reconfigurePendingItent = PendingIntent.getActivity(
            context,
            Random.nextInt(),
            intent2,
            PendingIntent.FLAG_IMMUTABLE
        )

        notificationBuilder
            .setContentTitle("${res.getString(R.string.failed_download)}: $title")
            .setContentText(error)
            .setSmallIcon(R.drawable.baseline_error_24)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    res,
                    R.drawable.baseline_error_24
                )
            )
            .setGroup(DOWNLOAD_ERRORED_NOTIFICATION_ID.toString())
            .setContentIntent(errorTabPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .clearActions()

        notificationBuilder.addAction(0, res.getString(R.string.configure_download), reconfigurePendingItent)
        if (logID != null){
            notificationBuilder.addAction(0, res.getString(R.string.logs), errorPendingIntent)
        }
        notificationManager.notify(DOWNLOAD_ERRORED_NOTIFICATION_ID + id.toInt(), notificationBuilder.build())

        if (
            !notificationManager.activeNotifications.any { it.id == DOWNLOAD_ERRORED_NOTIFICATION_ID }
            && Build.VERSION.SDK_INT > 24
            && isNotificationChannelEnabled(DOWNLOAD_ERRORED_CHANNEL_ID)
        ) {
            //make summary notification
            val summaryNotification = getBuilder(DOWNLOAD_WORKER_CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_error_24)
                .setLargeIcon(BitmapFactory.decodeResource(
                    res,
                    R.drawable.baseline_error_24
                ))
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setGroup(DOWNLOAD_ERRORED_NOTIFICATION_ID.toString())
                .setGroupSummary(true)
                .build()
            notificationManager.notify(DOWNLOAD_ERRORED_NOTIFICATION_ID, summaryNotification)
        }

    }


    @SuppressLint("MissingPermission")
    fun notify(id: Int, notification: Notification){
        notificationManager.notify(id, notification)
    }

    @SuppressLint("MissingPermission")
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
        if (queue > 1) contentText += """${queue - 1} ${resources.getString(R.string.items_left)}""" + "\n"
        contentText += desc.replace("\\[.*?\\] ".toRegex(), "")

        val pauseIntent = Intent(context, PauseDownloadNotificationReceiver::class.java)
        pauseIntent.putExtra("itemID", id)
        pauseIntent.putExtra("title", title)
        val pauseNotificationPendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            pauseIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(context, CancelDownloadNotificationReceiver::class.java)
        cancelIntent.putExtra("itemID", id)
        val cancelNotificationPendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        try {
            notificationBuilder.setProgress(100, progress, (progress == 0 || progress == 100))
                .setContentTitle(title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setGroup(DOWNLOAD_RUNNING_NOTIFICATION_ID.toString())
                .clearActions()
                .addAction(0, resources.getString(R.string.pause), pauseNotificationPendingIntent)
                .addAction(0, resources.getString(R.string.cancel), cancelNotificationPendingIntent)
            notificationManager.notify(id, notificationBuilder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    fun updateTerminalDownloadNotification(
        id: Int,
        desc: String,
        progress: Int,
        title: String?,
        channel : String
    ) {

        val notificationBuilder = getBuilder(channel)
        var contentText = ""
        contentText += desc.replace("\\[.*?\\] ".toRegex(), "")

        val cancelIntent = Intent(context, CancelDownloadNotificationReceiver::class.java)
        cancelIntent.putExtra("itemID", id)
        val cancelNotificationPendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        try {
            notificationBuilder.setProgress(100, progress, progress == 0)
                .setContentTitle(title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setGroup(DOWNLOAD_TERMINAL_RUNNING_NOTIFICATION_ID.toString())
                .clearActions()
                .addAction(0, resources.getString(R.string.cancel), cancelNotificationPendingIntent)
            notificationManager.notify(id, notificationBuilder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelDownloadNotification(id: Int) {
        notificationManager.cancel(id)
    }

    fun cancelErroredNotification(id: Int) {
        notificationManager.cancel(DOWNLOAD_ERRORED_NOTIFICATION_ID + id)
    }

    fun createDeletingLeftoverDownloadsNotification() : Notification {
        val notificationBuilder = getBuilder(DOWNLOAD_MISC_CHANNEL_ID)

        return notificationBuilder
            .setContentTitle(resources.getString(R.string.cleanup_leftover_downloads))
            .setCategory(Notification.CATEGORY_EVENT)
            .setSmallIcon(R.drawable.ic_launcher_foreground_large)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.ic_launcher_foreground_large
                )
            )
            .setContentText("")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .clearActions()
            .build()
    }

    fun createMoveCacheFilesNotification(pendingIntent: PendingIntent?, downloadMiscChannelId: String): Notification {
        val notificationBuilder = getBuilder(downloadMiscChannelId)

        return notificationBuilder
            .setContentTitle(resources.getString(R.string.move_temporary_files))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources,
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
            .build()
    }


    @SuppressLint("MissingPermission")
    fun updateCacheMovingNotification(id: Int, progress: Int, totalFiles: Int) {
        val notificationBuilder = getBuilder(DOWNLOAD_MISC_CHANNEL_ID)
        val contentText = "${progress}/${totalFiles}"
        try {
            notificationBuilder.setProgress(100, progress, progress == 0)
                .setContentTitle(resources.getString(R.string.move_temporary_files))
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            notificationManager.notify(id, notificationBuilder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createYTDLUpdateNotification() : Notification{
        val notificationBuilder = getBuilder(DOWNLOAD_MISC_CHANNEL_ID)
        notificationBuilder.setContentTitle("Updating YT-DLP...")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_EVENT)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources,
                    android.R.drawable.stat_sys_download
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .clearActions()
        return notificationBuilder.build()
    }

    fun createFormatsUpdateNotification(): Notification {
        val notificationBuilder = getBuilder(DOWNLOAD_MISC_CHANNEL_ID)
        return notificationBuilder
            .setContentTitle(resources.getString(R.string.update_formats_background))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources,
                    android.R.drawable.stat_sys_download
                )
            )
            .setContentText("")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(PROGRESS_MAX, PROGRESS_CURR, false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .clearActions()
            .build()
    }

    @SuppressLint("MissingPermission")
    fun updateFormatUpdateNotification(
        workID: Int,
        workTag: String,
        progress: Int,
        queue: Int,
    ) {

        val notificationBuilder = getBuilder(DOWNLOAD_MISC_CHANNEL_ID)
        val contentText = """${queue - progress} ${resources.getString(R.string.items_left)}"""


        val cancelIntent = Intent(context, CancelWorkReceiver::class.java)
        cancelIntent.putExtra("workTag", workTag)
        val cancelNotificationPendingIntent = PendingIntent.getBroadcast(
            context,
            workID,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE
        )


        try {
            notificationBuilder.setProgress(queue, progress, progress == 0)
                .setContentTitle(resources.getString(R.string.update_formats_background))
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .clearActions()
                .addAction(0, resources.getString(R.string.cancel), cancelNotificationPendingIntent)
            notificationManager.notify(workID, notificationBuilder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    fun createDataUpdateNotification(): Notification {
        val notificationBuilder = getBuilder(DOWNLOAD_MISC_CHANNEL_ID)
        return notificationBuilder
            .setContentTitle(resources.getString(R.string.updating_download_data))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setSmallIcon(R.drawable.ic_launcher_foreground_large)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.ic_launcher_foreground_large
                )
            )
            .setContentText("")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .clearActions()
            .build()
    }

    @SuppressLint("MissingPermission")
    fun updateDataUpdateNotification(
        workID: Int,
        workTag: String,
        progress: Int,
        queue: Int,
    ) {

        val notificationBuilder = getBuilder(DOWNLOAD_MISC_CHANNEL_ID)
        val contentText = """${queue - progress} ${resources.getString(R.string.items_left)}"""


        val cancelIntent = Intent(context, CancelWorkReceiver::class.java)
        cancelIntent.putExtra("workTag", workTag)
        val cancelNotificationPendingIntent = PendingIntent.getBroadcast(
            context,
            workID,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE
        )


        try {
            notificationBuilder.setProgress(queue, progress, progress == 0)
                .setContentTitle(resources.getString(R.string.updating_download_data))
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .clearActions()
                .addAction(0, resources.getString(R.string.cancel), cancelNotificationPendingIntent)
            notificationManager.notify(workID, notificationBuilder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    @SuppressLint("MissingPermission")
    fun showFormatsUpdatedNotification(downloadIds: List<Long>) {
        val notificationBuilder = getBuilder(DOWNLOAD_FINISHED_CHANNEL_ID)

        val bundle = Bundle()
        bundle.putBoolean("showDownloadsWithUpdatedFormats", true)
        bundle.putLongArray("downloadIds", downloadIds.toLongArray())

        val openMultipleDownloads = NavDeepLinkBuilder(context)
            .setGraph(R.navigation.nav_graph)
            .setDestination(R.id.homeFragment)
            .setArguments(bundle)
            .createPendingIntent()

        notificationBuilder
            .setContentTitle(resources.getString(R.string.finished_download_notification_channel_name))
            .setSmallIcon(R.drawable.ic_launcher_foreground_large)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.ic_launcher_foreground_large
                )
            )
            .setContentIntent(openMultipleDownloads)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .clearActions()

        notificationManager.notify(FORMAT_UPDATING_FINISHED_NOTIFICATION_ID, notificationBuilder.build())
    }


    @SuppressLint("MissingPermission")
    fun showQueriesFinished() {
        val notificationBuilder = getBuilder(DOWNLOAD_MISC_CHANNEL_ID)

        val openMultipleDownloads = NavDeepLinkBuilder(context)
            .setGraph(R.navigation.nav_graph)
            .setDestination(R.id.homeFragment)
            .createPendingIntent()

        notificationBuilder
            .setContentTitle(resources.getString(R.string.all_queries_finished))
            .setSmallIcon(R.drawable.ic_launcher_foreground_large)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.ic_launcher_foreground_large
                )
            )
            .setContentIntent(openMultipleDownloads)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .clearActions()

        notificationManager.notify(QUERY_PROCESS_FINISHED_NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun isNotificationChannelEnabled(channelId: String?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!TextUtils.isEmpty(channelId)) {
                val manager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = manager.getNotificationChannel(channelId)
                return channel.importance != NotificationManager.IMPORTANCE_NONE
            }
            return false
        } else {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    companion object {
        const val DOWNLOAD_SERVICE_CHANNEL_ID = "1"
        const val COMMAND_DOWNLOAD_SERVICE_CHANNEL_ID = "2"
        const val DOWNLOAD_FINISHED_CHANNEL_ID = "3"
        const val DOWNLOAD_WORKER_CHANNEL_ID = "5"
        const val DOWNLOAD_MISC_CHANNEL_ID = "4"
        const val DOWNLOAD_ERRORED_CHANNEL_ID = "6"

        const val DOWNLOAD_FINISHED_NOTIFICATION_ID =           30000
        const val DOWNLOAD_RESUME_NOTIFICATION_ID =             40000
        const val DOWNLOAD_UPDATING_NOTIFICATION_ID =           50000
        const val DOWNLOAD_ERRORED_NOTIFICATION_ID =            60000
        const val FORMAT_UPDATING_FINISHED_NOTIFICATION_ID =    70000
        const val QUERY_PROCESS_FINISHED_NOTIFICATION_ID =      80000
        const val DOWNLOAD_RUNNING_NOTIFICATION_ID =            90000
        const val DOWNLOAD_TERMINAL_RUNNING_NOTIFICATION_ID =   99000

        private const val PROGRESS_MAX = 100
        private const val PROGRESS_CURR = 0
    }
}