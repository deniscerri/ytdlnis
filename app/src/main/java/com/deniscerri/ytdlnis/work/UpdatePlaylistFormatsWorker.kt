package com.deniscerri.ytdlnis.work

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.navigation.NavDeepLinkBuilder
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.Converters
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.dao.ResultDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.models.LogItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.repository.LogRepository
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.util.UpdateUtil
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File


class UpdatePlaylistFormatsWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val dbManager = DBManager.getInstance(context)
        val resultDao = dbManager.resultDao
        val infoUtil = InfoUtil(context)
        val converters = Converters()
        val urls = inputData.getStringArray("urls")!!.toMutableList()

        infoUtil.getFormatsMultiple(urls){
            val formatStrings = mutableListOf<String>()
            it.forEach { i -> formatStrings.add(converters.formatToString(i)) }
            setProgressAsync(workDataOf("formats" to formatStrings.toTypedArray()))

            CoroutineScope(Dispatchers.IO).launch {
                val result = resultDao.getResultByURL(urls.removeFirst())
                val formats = infoUtil.getFormats(result.url)
                result.formats = ArrayList(formats)
                resultDao.update(result)
            }
        }

        return Result.success()
    }
}