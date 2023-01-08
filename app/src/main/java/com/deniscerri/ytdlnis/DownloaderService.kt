package com.deniscerri.ytdlnis

import android.app.Activity
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.deniscerri.ytdlnis.database.Video
import com.deniscerri.ytdlnis.ui.CustomCommandActivity
import com.deniscerri.ytdlnis.service.DownloadInfo
import com.deniscerri.ytdlnis.service.IDownloaderListener
import com.deniscerri.ytdlnis.service.IDownloaderService
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.work.FileTransferWorker
import com.deniscerri.ytdlnis.work.FileTransferWorker.Companion.originDir
import com.deniscerri.ytdlnis.work.FileTransferWorker.Companion.title
import com.deniscerri.ytdlnis.work.FileTransferWorker.Companion.downLocation
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern


class DownloaderService : Service() {
    private val binder = LocalBinder()
    private val activities: MutableMap<Activity, ArrayList<IDownloaderListener>?> =
        ConcurrentHashMap()
    private val fileUtil = FileUtil()
    private val downloadInfo = DownloadInfo()
    private var downloadQueue = LinkedList<Video>()
    private val compositeDisposable = CompositeDisposable()
    private val notificationUtil = App.notificationUtil
    private var context: Context? = null
    var downloadProcessID = "processID"
    private var downloadNotificationID = 0
    private lateinit var notificationChannelID : String
    private lateinit var workManager : WorkManager

    private val callback: (Float, Long?, String?) -> Unit =
        { progress: Float, _: Long?, line: String? ->
            downloadInfo.progress = progress.toInt()
            downloadInfo.outputLine = line
            downloadInfo.downloadQueue = downloadQueue
            var title: String? = getString(R.string.running_ytdlp_command)
            if (!downloadQueue.isEmpty()) {
                title = downloadQueue.peek()?.title
            }
            notificationUtil.updateDownloadNotification(
                downloadNotificationID,
                line!!, progress.toInt(), downloadQueue.size, title,
                notificationChannelID
            )
            try {
                for (activity in activities.keys) {
                    activity.runOnUiThread {
                        if (activities[activity] != null) {
                            for (i in activities[activity]!!.indices) {
                                val callback = activities[activity]!![i]
                                callback.onDownloadProgress(downloadInfo)
                            }
                        }
                    }
                }
            } catch (ignored: Exception) {
            }
        }

    override fun onCreate() {
        super.onCreate()
        context = this
        this.workManager = WorkManager.getInstance(context!!.applicationContext)
    }

    override fun onBind(intent: Intent): IBinder? {
        val theIntent: Intent
        val pendingIntent: PendingIntent
        if (intent.getBooleanExtra("rebind", false)) {
            return binder
        }
        when (val id = intent.getIntExtra("id", 1)) {
            NotificationUtil.DOWNLOAD_NOTIFICATION_ID -> {
                notificationChannelID = NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID
                theIntent = Intent(this, MainActivity::class.java)
                pendingIntent =
                    PendingIntent.getActivity(this, 0, theIntent, PendingIntent.FLAG_IMMUTABLE)
                downloadNotificationID = id
                val queue: ArrayList<out Video>? = intent.getParcelableArrayListExtra("queue")
                downloadQueue = LinkedList()
                downloadQueue.addAll(queue!!)
                downloadInfo.downloadQueue = downloadQueue
                val title = downloadInfo.video.title
                val notification =
                    App.notificationUtil.createDownloadServiceNotification(pendingIntent, title, NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID)
                startForeground(downloadNotificationID, notification)
                startDownload(downloadQueue)
            }
            NotificationUtil.COMMAND_DOWNLOAD_NOTIFICATION_ID -> {
                notificationChannelID = NotificationUtil.COMMAND_DOWNLOAD_SERVICE_CHANNEL_ID
                theIntent = Intent(this, CustomCommandActivity::class.java)
                pendingIntent =
                    PendingIntent.getActivity(this, 0, theIntent, PendingIntent.FLAG_IMMUTABLE)
                downloadNotificationID = id
                val command = intent.getStringExtra("command")
                val commandNotification = App.notificationUtil.createDownloadServiceNotification(
                    pendingIntent,
                    getString(R.string.running_ytdlp_command),
                    NotificationUtil.COMMAND_DOWNLOAD_SERVICE_CHANNEL_ID
                )
                startForeground(downloadNotificationID, commandNotification)
                startCommandDownload(command)
            }
        }
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            stopForeground(STOP_FOREGROUND_REMOVE)
        }else{
            stopForeground(true)
        }
        stopSelf()
    }

    inner class LocalBinder : Binder(), IDownloaderService {
        val service: DownloaderService
            get() = this@DownloaderService

        override fun getInfo(): DownloadInfo {
            return downloadInfo
        }

        override fun addActivity(activity: Activity, callbacks: ArrayList<IDownloaderListener>) {
            if (!activities.containsKey(activity)) {
                activities[activity] = callbacks
            }
        }

        override fun removeActivity(activity: Activity) {
            activities.remove(activity)
        }

        override fun updateQueue(queue: ArrayList<Video>) {
            try {
                for (i in queue.indices) {
                    downloadQueue.add(queue[i].clone() as Video)
                }
                if (downloadQueue.size == queue.size) {
                    downloadInfo.downloadQueue = downloadQueue
                    startDownload(downloadQueue)
                } else {
                    downloadInfo.downloadQueue = downloadQueue
                    Toast.makeText(context, getString(R.string.added_to_queue), Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't update download queue! :(", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        override fun cancelDownload(cancelAll: Boolean) {
            try {
                YoutubeDL.getInstance().destroyProcessById(downloadProcessID)
                compositeDisposable.clear()
                //stopForeground(true);
                if (cancelAll) {
                    onDownloadCancelAll()
                }
            } catch (err: Exception) {
                Log.e(TAG, err.message!!)
            }
        }

        override fun removeItemFromDownloadQueue(video: Video, type: String) {
            //if its the same video with the same download type as the current downloading one
            if (downloadInfo.video.getURL() == video.getURL() && downloadInfo.video.downloadedType == video.downloadedType) {
                cancelDownload(false)
                downloadInfo.downloadType = type
                onDownloadCancel(downloadInfo)
                downloadQueue.pop()
                downloadInfo.downloadQueue = downloadQueue
                startDownload(downloadQueue)
            } else {
                downloadQueue.remove(video)
                try {
                    val info = DownloadInfo()
                    info.video = video
                    info.downloadType = type
                    onDownloadCancel(info)
                } catch (ignored: Exception) {
                }
            }
            Toast.makeText(
                context,
                video.title + " " + getString(R.string.removed_from_queue),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun finishService() {
        try {
            for (activity in activities.keys) {
                activity.runOnUiThread {
                    for (i in activities[activity]!!.indices) {
                        val callback = activities[activity]!![i]
                        callback.onDownloadServiceEnd()
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun onDownloadCancelAll() {
        try {
            for (activity in activities.keys) {
                activity.runOnUiThread {
                    for (i in activities[activity]!!.indices) {
                        val callback = activities[activity]!![i]
                        callback.onDownloadCancelAll(downloadInfo)
                    }
                }
            }
        } catch (ignored: Exception) {
        }
        onDestroy()
    }

    private fun onDownloadEnd() {
        try {
            for (activity in activities.keys) {
                activity.runOnUiThread {
                    for (i in activities[activity]!!.indices) {
                        val callback = activities[activity]!![i]
                        callback.onDownloadEnd(downloadInfo)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onDownloadCancel(cancelInfo: DownloadInfo) {
        try {
            for (activity in activities.keys) {
                activity.runOnUiThread {
                    for (i in activities[activity]!!.indices) {
                        val callback = activities[activity]!![i]
                        callback.onDownloadCancel(cancelInfo)
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun startDownload(videos: LinkedList<Video>) {
        if (videos.size == 0) {
            finishService()
            return
        }
        val video: Video = try {
            videos.peek() as Video
        } catch (e: Exception) {
            finishService()
            return
        }
        try {
            for (activity in activities.keys) {
                activity.runOnUiThread {
                    for (i in activities[activity]!!.indices) {
                        val callback = activities[activity]!![i]
                        callback.onDownloadStart(downloadInfo)
                    }
                }
            }
        } catch (err: Exception) {
            err.printStackTrace()
        }
        val url = video.getURL()
        val request = YoutubeDLRequest(url)
        val type = video.downloadedType
        val downloadLocation = getDownloadLocation(type)

        var tempFileDir = File(cacheDir.absolutePath + """/${video.title}##${video.downloadedType}""")
        tempFileDir.delete()
        tempFileDir.mkdir()

        val sharedPreferences = context!!.getSharedPreferences("root_preferences", MODE_PRIVATE)
        val aria2 = sharedPreferences.getBoolean("aria2", false)
        if (aria2) {
            request.addOption("--downloader", "libaria2c.so")
            request.addOption("--external-downloader-args", "aria2c:\"--summary-interval=1\"")
        } else {
            val concurrentFragments = sharedPreferences.getInt("concurrent_fragments", 1)
            if (concurrentFragments > 1) request.addOption("-N", concurrentFragments)
        }
        val limitRate = sharedPreferences.getString("limit_rate", "")
        if (limitRate != "") request.addOption("-r", limitRate!!)
        val writeThumbnail = sharedPreferences.getBoolean("write_thumbnail", false)
        if (writeThumbnail) {
            request.addOption("--write-thumbnail")
            request.addOption("--convert-thumbnails", "png")
        }
        request.addOption("--no-mtime")
        val sponsorBlockFilters = sharedPreferences.getStringSet("sponsorblock_filters", emptySet())
        if (sponsorBlockFilters!!.isNotEmpty()) {
            val filters = java.lang.String.join(",", sponsorBlockFilters)
            request.addOption("--sponsorblock-remove", filters)
        }
        if (type == "audio") {
            if (downloadLocation.equals(getString(R.string.music_path))){
                tempFileDir = File(getString(R.string.music_path))
            }

            request.addOption("-x")
            var format = video.audioFormat
            if (format == null) format = sharedPreferences.getString("audio_format", "")
            request.addOption("--audio-format", format!!)
            request.addOption("--embed-metadata")

            val embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false)
            if (embedThumb) {
                request.addOption("--embed-thumbnail")
                request.addOption("--convert-thumbnails", "png")
                try {
                    val config = File(cacheDir, "config.txt")
                    val config_data =
                        "--ppa \"ffmpeg: -c:v png -vf crop=\\\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\\\"\""
                    val stream = FileOutputStream(config)
                    stream.write(config_data.toByteArray())
                    stream.close()
                    request.addOption("--config", config.absolutePath)
                } catch (ignored: Exception) {
                }
            }

            request.addCommands(Arrays.asList("--replace-in-metadata", "title", ".*.", video.title))
            request.addCommands(
                Arrays.asList(
                    "--replace-in-metadata",
                    "uploader",
                    ".*.",
                    video.author
                )
            )
            request.addOption("-o", tempFileDir.absolutePath + "/%(uploader)s - %(title)s.%(ext)s")
        } else if (type == "video") {
            if (downloadLocation.equals(getString(R.string.video_path))){
                tempFileDir = File(getString(R.string.video_path))
            }

            val addChapters = sharedPreferences.getBoolean("add_chapters", false)
            if (addChapters) {
                request.addOption("--sponsorblock-mark", "all")
            }
            val embedSubs = sharedPreferences.getBoolean("embed_subtitles", false)
            if (embedSubs) {
                request.addOption("--embed-subs", "")
            }
            var videoQuality = video.videoQuality
            if (videoQuality == null) videoQuality =
                sharedPreferences.getString("video_quality", "")
            var formatArgument = "bestvideo+bestaudio/best"
            if (videoQuality == "Worst Quality") {
                formatArgument = "worst"
            } else if (!videoQuality!!.isEmpty() && videoQuality != "Best Quality") {
                formatArgument = "bestvideo[height<=" + videoQuality.substring(
                    0,
                    videoQuality.length - 1
                ) + "]+bestaudio/best"
            }
            request.addOption("-f", formatArgument)
            var format = video.videoFormat
            if (format == null) format = sharedPreferences.getString("video_format", "")
            if (format != "DEFAULT") request.addOption("--merge-output-format", format!!)
            if (format != "webm") {
                val embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false)
                if (embedThumb) {
                    request.addOption("--embed-thumbnail")
                }
            }
            request.addOption("-o", tempFileDir.absolutePath + "/%(uploader)s - %(title)s.%(ext)s")
        }

        val disposable = Observable.fromCallable {
            YoutubeDL.getInstance().execute(request, downloadProcessID, callback)
        }
            .subscribeOn(Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                val workTag = video.getURL()
                val workData = workDataOf(
                    originDir to tempFileDir.absolutePath,
                    downLocation to downloadLocation,
                    title to video.title
                )
                val fileTransferWorkRequest = OneTimeWorkRequestBuilder<FileTransferWorker>()
                    .addTag(workTag)
                    .setInputData(workData)
                    .build()
                workManager.enqueueUniqueWork(
                    workTag,
                    ExistingWorkPolicy.KEEP,
                    fileTransferWorkRequest
                )

                downloadInfo.downloadPath = fileUtil.formatPath(getDownloadLocation(type)!!)
                Log.e(TAG, downloadInfo.downloadPath)
                downloadInfo.downloadType = type

                try {
                    for (activity in activities.keys) {
                        activity.runOnUiThread {
                            for (i in activities[activity]!!.indices) {
                                val callback = activities[activity]!![i]
                                callback.onDownloadEnd(downloadInfo)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }

                // SCAN NEXT IN QUEUE
                videos.remove()
                downloadInfo.downloadQueue = videos
                startDownload(videos)
            }) { e: Throwable? ->
                tempFileDir.delete()
                if (BuildConfig.DEBUG) {
                    Toast.makeText(context, e!!.message, Toast.LENGTH_LONG).show()
                    Log.e(TAG, getString(R.string.failed_download), e)
                }
                notificationUtil.updateDownloadNotification(
                    NotificationUtil.DOWNLOAD_NOTIFICATION_ID,
                    getString(R.string.failed_download), 0, 0, downloadQueue.peek()?.title,
                    NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID
                )
                downloadInfo.downloadType = type
                try {
                    for (activity in activities.keys) {
                        activity.runOnUiThread {
                            for (i in activities[activity]!!.indices) {
                                val callback = activities[activity]!![i]
                                callback.onDownloadError(downloadInfo)
                            }
                        }
                    }
                } catch (err: Exception) {
                    err.printStackTrace()
                }

                // SCAN NEXT IN QUEUE
                videos.remove()
                downloadInfo.downloadQueue = videos
                startDownload(videos)
            }
        compositeDisposable.add(disposable)
    }

    private fun startCommandDownload(text: String?) {
        var text = text
        if (text!!.startsWith("yt-dlp ")) {
            text = text.substring(6).trim { it <= ' ' }
        }

        val sharedPreferences = context!!.getSharedPreferences("root_preferences", MODE_PRIVATE)
        val downloadLocation = sharedPreferences.getString("command_path", getString(R.string.command_path))

        var tempFileDir = File(cacheDir.absolutePath + "/command")
        tempFileDir.delete()
        tempFileDir.mkdir()

        if (downloadLocation.equals(getString(R.string.command_path))){
            tempFileDir = File(getString(R.string.command_path))
        }


        val request = YoutubeDLRequest(emptyList())
        val commandRegex = "\"([^\"]*)\"|(\\S+)"
        val m = Pattern.compile(commandRegex).matcher(text)
        while (m.find()) {
            if (m.group(1) != null) {
                request.addOption(m.group(1))
            } else {
                request.addOption(m.group(2))
            }
        }
        request.addOption("-o", tempFileDir.absolutePath + "/%(title)s.%(ext)s")
        val disposable = Observable.fromCallable {
            YoutubeDL.getInstance().execute(request, downloadProcessID, callback)
        }
            .subscribeOn(Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ youtubeDLResponse: YoutubeDLResponse ->
                downloadInfo.outputLine = youtubeDLResponse.out

                val workTag = SystemClock.uptimeMillis().toString()
                val workData = workDataOf(
                    originDir to tempFileDir.absolutePath,
                    downLocation to downloadLocation,
                    title to ""
                )
                val fileTransferWorkRequest = OneTimeWorkRequestBuilder<FileTransferWorker>()
                    .addTag(workTag)
                    .setInputData(workData)
                    .build()
                workManager.enqueueUniqueWork(
                    workTag,
                    ExistingWorkPolicy.KEEP,
                    fileTransferWorkRequest
                )

                try {
                    for (activity in activities.keys) {
                        activity.runOnUiThread {
                            for (i in activities[activity]!!.indices) {
                                val callback = activities[activity]!![i]
                                callback.onDownloadEnd(downloadInfo)
                                callback.onDownloadServiceEnd()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }) { e: Throwable ->
                downloadInfo.outputLine = e.message
                tempFileDir.delete()
                try {
                    for (activity in activities.keys) {
                        activity.runOnUiThread {
                            for (i in activities[activity]!!.indices) {
                                val callback = activities[activity]!![i]
                                callback.onDownloadError(downloadInfo)
                                callback.onDownloadServiceEnd()
                            }
                        }
                    }
                } catch (err: Exception) {
                    err.printStackTrace()
                }
            }
        compositeDisposable.add(disposable)
    }

    private fun getDownloadLocation(type: String): String? {
        val sharedPreferences = context!!.getSharedPreferences("root_preferences", MODE_PRIVATE)
        var downloadsDir: String? = if (type == "audio") {
            sharedPreferences.getString("music_path", getString(R.string.music_path))
        } else {
            sharedPreferences.getString("video_path", getString(R.string.video_path))
        }
        Log.e(TAG, downloadsDir!!)
        return downloadsDir
    }

    companion object {
        private const val TAG = "DownloaderService"
    }
}