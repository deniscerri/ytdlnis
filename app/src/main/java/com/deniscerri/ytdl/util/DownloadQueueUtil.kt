package com.deniscerri.ytdl.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.repository.HistoryRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel.AlreadyExistsIDs
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel.QueueDownloadsResult
import com.deniscerri.ytdl.util.Extensions.needsDataUpdating
import com.deniscerri.ytdl.util.extractors.ytdlp.YTDLPUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.sequences.toList
import kotlin.text.split

class DownloadQueueUtil(private val context: Context) {
    private val db = DBManager.getInstance(context)
    private val dao = db.downloadDao
    private val repository = DownloadRepository(dao)
    private val historyRepository = HistoryRepository(db.historyDao)
    private val ytdlpUtil = YTDLPUtil(context, db.commandTemplateDao)
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    data class Result(
        val queued: List<DownloadItem>,
        val duplicates: List<DownloadViewModel.AlreadyExistsIDs>,
        val message: String
    )

    suspend fun enqueue(items: List<DownloadItem>, ignoreDuplicates: Boolean = false): Result {
        val alarmScheduler = AlarmScheduler(context)
        val queuedItems = mutableListOf<DownloadItem>()

        //download id, history item id
        //history item id if the existing item is already downloaded
        //if history id is empty, it just found an existing item in the queue/active list
        val existingItemIDs = mutableListOf<AlreadyExistsIDs>()

        val downloadArchive =   runCatching {
            File(FileUtil.getDownloadArchivePath(context)).useLines { it.toList() }
        }
            .getOrElse { listOf() }
            .map { it.split(" ")[1] }

        val checkDuplicate = sharedPreferences.getString("prevent_duplicate_downloads", "")!!
        val activeAndQueuedDownloads = withContext(Dispatchers.IO){
            repository.getActiveAndQueuedDownloads()
        }

        var lastQueueOrder = withContext(Dispatchers.IO) {
            dao.getLastQueueOrder()
        }

        items.forEachIndexed { idx, it ->
            if (it.downloadStartTime > 0) {
                it.status = DownloadRepository.Status.Scheduled.toString()
            }else {
                it.status = DownloadRepository.Status.Queued.toString()
            }
            if (it.rowNumber == 0 && items.size > 1) {
                it.rowNumber = idx + 1
            }
            it.queueOrder = lastQueueOrder + 1
            lastQueueOrder++

            //CHECK DUPLICATES
            var isDuplicate = false
            if (checkDuplicate.isNotEmpty() && !ignoreDuplicates){
                when(checkDuplicate){
                    "download_archive" -> {
                        if (downloadArchive.any { d -> it.url.contains(d) }){
                            isDuplicate = true
                            if (it.id == 0L){
                                val id = runBlocking {
                                    repository.insert(it)
                                }
                                it.id = id
                            }
                            it.status = DownloadRepository.Status.Duplicate.toString()
                            repository.update(it)
                            existingItemIDs.add(AlreadyExistsIDs(it.id,null))
                        }
                    }
                    "url_type" -> {
                        val existingDownload = activeAndQueuedDownloads.firstOrNull { a -> a.type == it.type && a.url == it.url  }
                        if (existingDownload != null){
                            isDuplicate = true
                            if (it.id == 0L){
                                val id = runBlocking {
                                    repository.insert(it)
                                }
                                it.id = id
                            }
                            it.status = DownloadRepository.Status.Duplicate.toString()
                            repository.update(it)
                            existingItemIDs.add(AlreadyExistsIDs(it.id,null))
                        }else{
                            //check if downloaded and file exists
                            val history = withContext(Dispatchers.IO){
                                historyRepository.getAllByURL(it.url).filter { item -> item.downloadPath.any { path -> FileUtil.exists(path) } }
                            }

                            val existingHistoryItem = history.firstOrNull {
                                    h -> h.type == it.type
                            }

                            if (existingHistoryItem != null){
                                isDuplicate = true
                                if (it.id == 0L){
                                    val id = runBlocking {
                                        repository.insert(it)
                                    }
                                    it.id = id
                                }
                                it.status = DownloadRepository.Status.Duplicate.toString()
                                repository.update(it)
                                existingItemIDs.add(AlreadyExistsIDs(it.id,existingHistoryItem.id))
                            }
                        }
                    }
                    "config" -> {
                        val currentCommand = ytdlpUtil.buildYTDLRequest(it)
                        val parsedCurrentCommand = ytdlpUtil.parseYTDLRequestString(currentCommand)
                        val existingDownload = activeAndQueuedDownloads.firstOrNull{d ->
                            val normalized = d.copy(
                                id = 0,
                                logID = null,
                                customFileNameTemplate = it.customFileNameTemplate,
                                status = DownloadRepository.Status.Queued.toString()
                            )
                            normalized.toString() == it.toString()
                        }

                        if (existingDownload != null){
                            isDuplicate = true
                            if (it.id == 0L){
                                val id = runBlocking {
                                    repository.insert(it)
                                }
                                it.id = id
                            }
                            it.status = DownloadRepository.Status.Duplicate.toString()
                            repository.update(it)
                            existingItemIDs.add(AlreadyExistsIDs(it.id, null))
                        }else{
                            //check if downloaded and file exists
                            val history = withContext(Dispatchers.IO){
                                historyRepository.getAllByURL(it.url).filter { item -> item.downloadPath.any { path -> FileUtil.exists(path) } }
                            }

                            val existingHistoryItem = history.firstOrNull {
                                    h -> h.command.replace("(-P \"(.*?)\")|(--trim-filenames \"(.*?)\")".toRegex(), "") == parsedCurrentCommand.replace("(-P \"(.*?)\")|(--trim-filenames \"(.*?)\")".toRegex(), "")
                            }

                            if (existingHistoryItem != null){
                                isDuplicate = true
                                if (it.id == 0L){
                                    val id = runBlocking {
                                        repository.insert(it)
                                    }
                                    it.id = id
                                }
                                it.status = DownloadRepository.Status.Duplicate.toString()
                                repository.update(it)
                                existingItemIDs.add(AlreadyExistsIDs(it.id, existingHistoryItem.id))
                            }
                        }
                    }
                }
            }

            if (!isDuplicate){
                queuedItems.add(it)
            }


        }

        var queued : List<DownloadItem> = listOf()
        var resultMessage = ""

        //if scheduler is on
        val useScheduler = sharedPreferences.getBoolean("use_scheduler", false)
        if (useScheduler && !alarmScheduler.isDuringTheScheduledTime()){
            if (alarmScheduler.canSchedule()){
                repository.updateAll(queuedItems)
                alarmScheduler.schedule()
            }else{
                sharedPreferences.edit().putBoolean("use_scheduler", false).apply()
                resultMessage = context.getString(R.string.enable_alarm_permission)
            }
        }else{
            queued = repository.updateAll(queuedItems)
            resultMessage = repository.startDownloadWorker(queued, context).getOrElse { "" }
        }

        return Result(queued = queued, duplicates = existingItemIDs, message = resultMessage)
    }
}