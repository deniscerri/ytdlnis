package com.deniscerri.ytdlnis.work

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.deniscerri.ytdlnis.BuildConfig
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.regex.Pattern


class DownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        workID = inputData.getInt("workID", SystemClock.uptimeMillis().toInt())
        val notificationUtil = NotificationUtil(context)
        val dbManager = DBManager.getInstance(context)
        val dao = dbManager.downloadDao
        val repository = DownloadRepository(dao)
        val commandTemplateDao = dbManager.commandTemplateDao
        val historyDao = dbManager.historyDao

        queue = repository.getQueuedDownloads() as MutableList<DownloadItem>

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = notificationUtil.createDownloadServiceNotification(pendingIntent, queue[0].title, workID, NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID)
        val foregroundInfo = ForegroundInfo(workID, notification)
        setForeground(foregroundInfo)

        while (queue.isNotEmpty()){
            val item = dao.getDownloadById(queue[0].id!!)
            queue.removeFirst()
            val url = item.url
            val request = YoutubeDLRequest(url)
            val type = item.type
            val downloadLocation = item.downloadPath

            val tempFolder = StringBuilder(context.cacheDir.absolutePath + """/${item.title}##${item.type}""")
            when(type){
                "audio" -> tempFolder.append("##${item.audioQualityId}")
                "video" -> tempFolder.append("##${item.videoQualityId}")
                "command" -> tempFolder.append("##${item.customTemplateId}")
            }
            var tempFileDir = File(tempFolder.toString())
            tempFileDir.delete()
            tempFileDir.mkdir()

            val sharedPreferences = context.getSharedPreferences("root_preferences",
                Service.MODE_PRIVATE
            )
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

            when(type){
                "audio" -> {
                    request.addOption("-x")
                    var audioQualityId : String = item.audioQualityId
                    if (audioQualityId == "0") audioQualityId = "ba"
                    var format = item.audioFormat
                    if (format.isEmpty()){
                        format = sharedPreferences.getString("audio_format", "")!!
                    }
                    request.addOption("-f", audioQualityId)
                    request.addOption("--audio-format", format)
                    request.addOption("--embed-metadata")

                    val embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false)
                    if (embedThumb) {
                        request.addOption("--embed-thumbnail")
                        request.addOption("--convert-thumbnails", "png")
                        try {
                            val config = File(context.cacheDir, "config" + item.title + "##" + item.audioQualityId + ".txt")
                            val configData =
                                "--ppa \"ffmpeg: -c:v png -vf crop=\\\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\\\"\""
                            config.writeText(configData)
                            request.addOption("--config", config.absolutePath)
                        } catch (ignored: Exception) {}
                    }
                    request.addOption("--parse-metadata", "%(release_year,upload_date)s:%(meta_date)s")
                    request.addCommands(listOf("--replace-in-metadata", "title", ".*.", item.title))
                    request.addCommands(listOf("--replace-in-metadata", "uploader", ".*.", item.author))

                    if (item.playlistTitle.isNotEmpty()) {
                        request.addOption("--parse-metadata", "%(album,playlist,title)s:%(meta_album)s")
                        request.addOption("--parse-metadata", "%(track_number,playlist_index)d:%(meta_track)s")
                    } else {
                        request.addOption("--parse-metadata", "%(album,title)s:%(meta_album)s")
                    }
                    request.addOption("-o", tempFileDir.absolutePath + "/%(uploader)s - %(title)s.%(ext)s")
                }
                "video" -> {
                    if (downloadLocation == context.getString(R.string.video_path)){
                        tempFileDir = File(context.getString(R.string.video_path))
                    }

                    val addChapters = sharedPreferences.getBoolean("add_chapters", false)
                    if (addChapters) {
                        request.addOption("--sponsorblock-mark", "all")
                    }
                    val embedSubs = sharedPreferences.getBoolean("embed_subtitles", false)
                    if (embedSubs) {
                        request.addOption("--embed-subs", "")
                    }
                    var videoQualityId = item.videoQualityId
                    val audioQualityId = item.audioQualityId
                    if (videoQualityId.isEmpty()) videoQualityId = "bestvideo"
                    val formatArgument = StringBuilder(videoQualityId)
                    if (videoQualityId != "worst"){
                        if (audioQualityId != "0") formatArgument.append("+", audioQualityId, "/best")
                    }
                    request.addOption("-f", formatArgument.toString())
                    var format = item.videoFormat
                    if (format.isNotEmpty()) {
                        format = sharedPreferences.getString("video_format", "")!!
                        if (format != "DEFAULT") request.addOption("--merge-output-format", format)
                    }

                    if (format != "webm") {
                        val embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false)
                        if (embedThumb) {
                            request.addOption("--embed-thumbnail")
                        }
                    }
                    request.addOption("-o", tempFileDir.absolutePath + "/%(uploader)s - %(title)s.%(ext)s")
                }
                "command" -> {
                    val commandRegex = "\"([^\"]*)\"|(\\S+)"
                    val command = commandTemplateDao.getTemplateById(item.customTemplateId)
                    val m = Pattern.compile(commandRegex).matcher(command.content)
                    while (m.find()) {
                        if (m.group(1) != null) {
                            request.addOption(m.group(1)!!)
                        } else {
                            request.addOption(m.group(2)!!)
                        }
                    }
                }
            }

            runCatching {
                YoutubeDL.getInstance().execute(request, item.id.toString()){ progress, _, line ->
                    setProgressAsync(workDataOf("progress" to progress.toInt()))
                    var title: String? = context.getString(R.string.download_start)
                    if (queue.isNotEmpty()) {
                        title = queue[0].title
                    }
                    notificationUtil.updateDownloadNotification(
                        workID,
                        line, progress.toInt(), queue.size, title,
                        NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID
                    )
                }
            }.onSuccess {
                //move file from internal to set download directory
                moveFile(tempFileDir.absoluteFile, downloadLocation){ progress ->
                    setProgressAsync(workDataOf("progress" to progress))
                }
                //put download in history
                val incognito = sharedPreferences.getBoolean("incognito", false)
                if (!incognito) {
                    val unixtime = System.currentTimeMillis() / 1000
                    val historyItem = HistoryItem(item.url, item.title, item.author, item.duration, item.thumb, item.type, unixtime, item.downloadPath, item.website)
                    historyDao.insert(historyItem)
                }

            }.onFailure {
                tempFileDir.delete()
                if (BuildConfig.DEBUG) {
                    Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                    Log.e(DownloadWorker.TAG, context.getString(R.string.failed_download), it)
                }
                notificationUtil.updateDownloadNotification(
                    workID,
                    context.getString(R.string.failed_download), 0, 0, item.title,
                    NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID
                )
                return Result.failure()
            }
        }
        return Result.success()
    }

    private fun getDownloadLocation(type: String, context: Context): String? {
        val sharedPreferences = context.getSharedPreferences("root_preferences",
            Service.MODE_PRIVATE
        )
        val downloadsDir: String? = if (type == "audio") {
            sharedPreferences.getString("music_path", context.getString(R.string.music_path))
        } else {
            sharedPreferences.getString("video_path", context.getString(R.string.video_path))
        }
        return downloadsDir
    }


    private fun moveFile(originDir: File, downLocation: String, progress: (progress: Int) -> Unit){
        val destDir = Uri.parse(downLocation).run {
            DocumentsContract.buildChildDocumentsUriUsingTree(this, DocumentsContract.getTreeDocumentId(this))
        }
        val fileUtil = FileUtil()
        fileUtil.moveFile(originDir, context, destDir){ p ->
            progress(p)
        }
    }

    companion object {
        var workID: Int = 0
        const val TAG = "DownloadWorker"
        lateinit var queue: MutableList<DownloadItem>
    }

}