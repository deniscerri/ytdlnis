package com.deniscerri.ytdlnis.work

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.regex.Pattern


class DownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        itemId = inputData.getLong("id", 0)
        if (itemId == 0L) return Result.failure()

        val notificationUtil = NotificationUtil(context)
        val dbManager = DBManager.getInstance(context)
        val dao = dbManager.downloadDao
        val repository = DownloadRepository(dao)
        val commandTemplateDao = dbManager.commandTemplateDao
        val historyDao = dbManager.historyDao
        val handler = Handler(Looper.getMainLooper())


        val downloadItem: DownloadItem?
        try {
            downloadItem = repository.getItemByID(itemId)
        }catch (e: Exception){
            e.printStackTrace()
            return Result.failure()
        }

        Log.e(TAG, downloadItem.toString())

        runBlocking{
            repository.setDownloadStatus(downloadItem, DownloadRepository.Status.Active)
        }
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = notificationUtil.createDownloadServiceNotification(pendingIntent, downloadItem.title, downloadItem.id.toInt(), NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID)
        val foregroundInfo = ForegroundInfo(downloadItem.id.toInt(), notification)
        setForegroundAsync(foregroundInfo)

        
        val url = downloadItem.url
        val request = YoutubeDLRequest(url)
        val type = downloadItem.type
        val downloadLocation = downloadItem.downloadPath

        val tempFolder = StringBuilder(context.cacheDir.absolutePath + """/${downloadItem.title}##${downloadItem.type}""")
        tempFolder.append("##${downloadItem.format.format_id}")
        val tempFileDir = File(tempFolder.toString())
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

        request.addCommands(listOf("--replace-in-metadata","title",".*.",downloadItem.title));
        request.addCommands(listOf("--replace-in-metadata","uploader",".*.",downloadItem.author));

        when(type){
            "audio" -> {
                request.addOption("-x")
                var audioQualityId : String = downloadItem.format.format_id
                if (audioQualityId == "0" || audioQualityId.isEmpty()) audioQualityId = "ba"
                request.addOption("-f", audioQualityId)

                val ext = downloadItem.format.container
                if(ext != context.getString(R.string.defaultValue)){
                    request.addOption("--audio-format", ext)
                }else{
                    request.addOption("--audio-format", sharedPreferences.getString("audio_format", "mp3")!!)
                }
                request.addOption("--embed-metadata")

                val embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false)
                if (embedThumb) {
                    request.addOption("--embed-thumbnail")
                    request.addOption("--convert-thumbnails", "png")
                    try {
                        val config = File(context.cacheDir, "config" + downloadItem.title + "##" + downloadItem.format.format_id + ".txt")
                        val configData =
                            "--ppa \"ffmpeg: -c:v png -vf crop=\\\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\\\"\""
                        config.writeText(configData)
                        request.addOption("--config", config.absolutePath)
                    } catch (ignored: Exception) {}
                }
                request.addOption("--parse-metadata", "%(release_year,upload_date)s:%(meta_date)s")
                request.addCommands(listOf("--replace-in-metadata", "title", ".*.", downloadItem.title))
                request.addCommands(listOf("--replace-in-metadata", "uploader", ".*.", downloadItem.author))

                if (downloadItem.playlistTitle.isNotEmpty()) {
                    request.addOption("--parse-metadata", "%(album,playlist,title)s:%(meta_album)s")
                    request.addOption("--parse-metadata", "%(track_number,playlist_index)d:%(meta_track)s")
                } else {
                    request.addOption("--parse-metadata", "%(album,title)s:%(meta_album)s")
                }
                request.addOption("-o", tempFileDir.absolutePath + "/%(uploader)s - %(title)s.%(ext)s")
            }
            "video" -> {
                val addChapters = sharedPreferences.getBoolean("add_chapters", false)
                if (addChapters) {
                    request.addOption("--sponsorblock-mark", "all")
                }
                val embedSubs = sharedPreferences.getBoolean("embed_subtitles", false)
                if (embedSubs) {
                    request.addOption("--embed-subs", "")
                }
                var videoFormatID = downloadItem.format.format_id
                Log.e(TAG, videoFormatID)
                var formatArgument = "bestvideo+bestaudio/best"
                if (videoFormatID.isNotEmpty()) {
                    if (videoFormatID == "Best Quality") videoFormatID = "bestvideo"
                    else if (videoFormatID == "Worst Quality") videoFormatID = "worst"
                    formatArgument = videoFormatID + if (downloadItem.removeAudio) "" else "+bestaudio"
                }
                request.addOption("-f", formatArgument)
                val format = downloadItem.format.container
                if(format.isNotEmpty()){
                    request.addOption("--merge-output-format", format)
                }
                if (format != "webm" && format != "DEFAULT") {
                    val embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false)
                    if (embedThumb) {
                        request.addOption("--embed-thumbnail")
                    }
                }
                request.addOption("-o", tempFileDir.absolutePath + "/%(uploader)s - %(title)s.%(ext)s")
            }
            "command" -> {
                val commandRegex = "\"([^\"]*)\"|(\\S+)"
                val command = commandTemplateDao.getTemplate(downloadItem.format.format_id.toLong())
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
            YoutubeDL.getInstance().execute(request, downloadItem.id.toString()){ progress, _, line ->
                setProgressAsync(workDataOf("progress" to progress.toInt()))
                setProgressAsync(workDataOf("output" to line))
                val title: String = downloadItem.title
                notificationUtil.updateDownloadNotification(
                    downloadItem.id.toInt(),
                    line, progress.toInt(), 0, title,
                    NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID
                )
            }
        }.onSuccess {
            //move file from internal to set download directory
            var finalPath : String?
            try {
                finalPath = moveFile(tempFileDir.absoluteFile, downloadLocation){ progress ->
                    setProgressAsync(workDataOf("progress" to progress))
                }
            }catch (e: Exception){
                finalPath = context.getString(R.string.unfound_file)
                handler.postDelayed({
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                }, 1000)
            }
            //put download in history
            val incognito = sharedPreferences.getBoolean("incognito", false)
            if (!incognito) {
                val unixtime = System.currentTimeMillis() / 1000
                val historyItem = HistoryItem(0, downloadItem.url, downloadItem.title, downloadItem.author, downloadItem.duration, downloadItem.thumb, downloadItem.type, unixtime, finalPath!!, downloadItem.website, downloadItem.format)
                runBlocking {
                    historyDao.insert(historyItem)
                }
            }
            runBlocking {
                dao.delete(downloadItem.id)
            }
        }.onFailure {
            if (it is YoutubeDL.CanceledException) {
                downloadItem.status = DownloadRepository.Status.Cancelled.toString()
                runBlocking {
                    dao.update(downloadItem)
                }
                return Result.failure()
            }else{
                tempFileDir.delete()
                handler.postDelayed({
                    Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                }, 1000)

                Log.e(TAG, context.getString(R.string.failed_download), it)
                notificationUtil.updateDownloadNotification(
                    downloadItem.id.toInt(),
                    context.getString(R.string.failed_download), 0, 0, downloadItem.title,
                    NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID
                )

                downloadItem.status = DownloadRepository.Status.Error.toString()
                runBlocking {
                    dao.update(downloadItem)
                }
                return Result.failure()
            }
        }
        
        return Result.success()
    }

//    private fun getDownloadLocation(type: String, context: Context): String? {
//        val sharedPreferences = context.getSharedPreferences("root_preferences",
//            Service.MODE_PRIVATE
//        )
//        val downloadsDir: String? = if (type == "audio") {
//            sharedPreferences.getString("music_path", context.getString(R.string.music_path))
//        } else {
//            sharedPreferences.getString("video_path", context.getString(R.string.video_path))
//        }
//        return downloadsDir
//    }


    @Throws(Exception::class)
    private fun moveFile(originDir: File, downLocation: String, progress: (progress: Int) -> Unit) : String{
        val fileUtil = FileUtil()
        val path = fileUtil.moveFile(originDir, context, downLocation){ p ->
            progress(p)
        }
        return path
    }

    companion object {
        var itemId: Long = 0
        const val TAG = "DownloadWorker"
    }

}