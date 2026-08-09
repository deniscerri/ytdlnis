package com.deniscerri.ytdl.work.download

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.models.YTDLRequest
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.LogItem
import com.deniscerri.ytdl.database.repository.LogRepository
import com.deniscerri.ytdl.ui.more.terminal.TerminalActivity
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.WorkerEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File


class TerminalDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        itemId = inputData.getInt("id", 0)
        val command = inputData.getString("command")
        val dao = DBManager.getInstance(context).terminalDao
        if (itemId == 0) return Result.failure()
        if (command!!.isEmpty()) return Result.failure()

        val dbManager = DBManager.getInstance(context)
        val logRepo = LogRepository(dbManager.logDao)
        val notificationUtil = NotificationUtil(context)
        val handler = Handler(Looper.getMainLooper())

        val intent = Intent(context, TerminalActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = notificationUtil.createDownloadServiceNotification(pendingIntent, command.take(65), NotificationUtil.DOWNLOAD_TERMINAL_RUNNING_NOTIFICATION_ID)
        if (Build.VERSION.SDK_INT >= 33) {
            setForegroundAsync(ForegroundInfo(itemId, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        }else{
            setForegroundAsync(ForegroundInfo(itemId, notification))
        }

        val sharedPreferences =  PreferenceManager.getDefaultSharedPreferences(context)
        val downloadLocation = sharedPreferences.getString("command_path", FileUtil.getDefaultCommandPath())

        val logDownloads = sharedPreferences.getBoolean("log_downloads", false) && !sharedPreferences.getBoolean("incognito", false)
        val initialLogDetails = "Terminal Task\n" +
                "Command:\n${command.trim()}\n\n"
        val logItem = LogItem(
            0,
            "Terminal Task",
            initialLogDetails,
            Format(),
            DownloadType.command,
            System.currentTimeMillis(),
        )

        var noCache = false
        runCatching {
            if (logDownloads){
                runBlocking {
                    logItem.id = logRepo.insert(logItem)
                }
            }

            val callback : (Float, Long, String) -> Unit = { progress, _, line ->
                runBlocking {
                    WorkerEventBus.post(DownloadWorker.WorkerProgress(progress.toInt(), line, itemId.toLong(), logItem.id))
                }

                val title: String = command.take(65)
                notificationUtil.updateTerminalDownloadNotification(
                    itemId,
                    line, progress.toInt(), title,
                    NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID
                )
                CoroutineScope(Dispatchers.IO).launch {
                    if (logDownloads) logRepo.update(line, logItem.id)
                    dao.updateLog(line, itemId.toLong())
                }
            }

            val c = command.lowercase()
            if (c.startsWith("ffmpeg")) {
                RuntimeManager.getInstance().executeFFmpeg(command, itemId.toString(), callback = callback)
            } else if (c.startsWith("deno")) {
                RuntimeManager.getInstance().executeDeno(command, itemId.toString(), callback = callback)
            } else if (c.startsWith("python")) {
                RuntimeManager.getInstance().executePython(command, itemId.toString(), callback = callback)
            }
            else {
                val (resp, isNoCache) = buildYTDLPRequest(command, sharedPreferences)
                noCache = isNoCache
                RuntimeManager.getInstance().execute(resp, itemId.toString(), true, callback = callback)
            }
        }.onSuccess {
            CoroutineScope(Dispatchers.IO).launch {
                if(!noCache){
                    //move file from internal to set download directory
                    try {
                        FileUtil.moveFile(File(FileUtil.getCachePath(context) + "/TERMINAL/" + itemId),context, downloadLocation!!, false){ p ->
                            WorkerEventBus.post(DownloadWorker.WorkerProgress(p, "", itemId.toLong(), logItem.id))
                        }
                    }catch (e: Exception){
                        e.printStackTrace()
                        handler.postDelayed({
                            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                        }, 1000)
                    }
                }
            }
            if (logDownloads) logRepo.update(initialLogDetails + it.out, logItem.id, true)
            dao.updateLog(it.out, itemId.toLong())
            notificationUtil.cancelDownloadNotification(itemId)
            delay(1000)
            dao.delete(itemId.toLong())
            Result.success()
        }.onFailure {
            if (it.message != null){
                if (logDownloads) logRepo.update(it.message!!, logItem.id)
                dao.updateLog(it.message!!, itemId.toLong())
            }
            notificationUtil.cancelDownloadNotification(itemId)
            File(FileUtil.getDefaultCommandPath() + "/" + itemId).deleteRecursively()
            Log.e(TAG, context.getString(R.string.failed_download), it)
            delay(1000)
            dao.delete(itemId.toLong())
            Result.failure()

        }

        return Result.success()

    }

    private fun buildYTDLPRequest(command: String, sharedPreferences: SharedPreferences) : Pair<YTDLRequest, Boolean> {
        val request = YTDLRequest(emptyList())
        request.addOption(
            "--config-locations",
            File(context.cacheDir.absolutePath + "/config-TERMINAL[${System.currentTimeMillis()}].txt").apply {
                writeText(command)
            }.absolutePath
        )

        if (sharedPreferences.getBoolean("use_cookies", false)){
            FileUtil.getCookieFile(context){
                request.addOption("--cookies", it)
            }

            val useHeader = sharedPreferences.getBoolean("use_header", false)
            val header = sharedPreferences.getString("useragent_header", "")
            if (useHeader && !header.isNullOrBlank()){
                request.addOption("--add-header","User-Agent:${header}")
            }
        }

        val commandPath = sharedPreferences.getString("command_path", FileUtil.getDefaultCommandPath())!!
        var noCache = !sharedPreferences.getBoolean("cache_downloads", true) && File(FileUtil.formatPath(commandPath)).canWrite()

        if (command.contains("-P ")) {
            noCache = true
        }else {
            if (!noCache){
                request.addOption("-P", FileUtil.getCachePath(context) + "TERMINAL/" + itemId)
            }else if (!request.hasOption("-P")){
                request.addOption("-P", FileUtil.formatPath(commandPath))
            }
        }

        return Pair(request, noCache)
    }

    companion object {
        private var itemId : Int = 0
        const val TAG = "TerminalDownloadWorker"
    }

}