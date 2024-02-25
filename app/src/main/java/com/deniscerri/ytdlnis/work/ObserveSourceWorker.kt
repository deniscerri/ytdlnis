package com.deniscerri.ytdlnis.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.deniscerri.ytdlnis.App
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.ObserveSourcesItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.repository.HistoryRepository
import com.deniscerri.ytdlnis.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdlnis.database.repository.ResultRepository
import com.deniscerri.ytdlnis.receiver.ObserveAlarmReceiver
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar


class ObserveSourceWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val notificationUtil = NotificationUtil(App.instance)
        val sourceID = inputData.getLong("id", 0)
        if (sourceID == 0L) return Result.failure()
        val dbManager = DBManager.getInstance(context)
        val repo = ObserveSourcesRepository(dbManager.observeSourcesDao)
        val resultsRepo = ResultRepository(dbManager.resultDao, App.instance)
        val historyRepo = HistoryRepository(dbManager.historyDao)
        val downloadRepo = DownloadRepository(dbManager.downloadDao)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val infoUtil = InfoUtil(context)

        val item = repo.getByID(sourceID)
        if (item.status == ObserveSourcesRepository.SourceStatus.STOPPED){
            repo.cancelObservationTaskByID(context, item.id)
            return Result.failure()
        }

        val workerID = System.currentTimeMillis().toInt()
        val notification = notificationUtil.createObserveSourcesNotification(item.name)
        val foregroundInfo = ForegroundInfo(workerID, notification)
        setForegroundAsync(foregroundInfo)

        val res = runCatching { infoUtil.getFromYTDL(item.url).toList() }.getOrElse { listOf() }
            .filter { result ->
                //if first run and get new items only is preferred then dont get anything on first run
                if (item.getOnlyNewUploads && item.runCount == 0){
                    item.alreadyProcessedLinks.add(result.url)
                    false
                }else{
                    true
                }
            }
            .filter { result ->

            val history = historyRepo.getAllByURL(result.url)
            if (!item.retryMissingDownloads){
                //all items that are not present in history
                history.none { hi -> hi.downloadPath.any { path -> FileUtil.exists(path) } }
            }else{
                //all items that are not already processed
                if(item.alreadyProcessedLinks.isEmpty()){
                    !history.map { it.url }.contains(result.url) && !item.alreadyProcessedLinks.contains(result.url)
                }else{
                    !item.alreadyProcessedLinks.contains(result.url)
                }
            }

        }

        val items = mutableListOf<DownloadItem>()

        res.forEach {
            val string = Gson().toJson(item.downloadItemTemplate, DownloadItem::class.java)
            val downloadItem = Gson().fromJson(string, DownloadItem::class.java)
            downloadItem.url = it.url
            downloadItem.title = it.title
            downloadItem.author = it.author
            downloadItem.thumb = it.thumb
            downloadItem.status = DownloadRepository.Status.Queued.toString()
            downloadItem.playlistTitle = it.playlistTitle
            downloadItem.playlistURL = it.playlistURL
            downloadItem.playlistIndex = it.playlistIndex
            downloadItem.id = 0L
            items.add(downloadItem)
        }


        if (items.isNotEmpty()){

            //QUEUE DOWNLOADS
            //COPY OF QUEUE DOWNLOADS IN DOWNLOAD VIEW MODEL. NEEDS TO BE UPDATED IF THAT IS UPDATED
            val context = App.instance
            val alarmScheduler = AlarmScheduler(context)
            val activeAndQueuedDownloads = downloadRepo.getActiveAndQueuedDownloads()
            val queuedItems = mutableListOf<DownloadItem>()
            val existing = mutableListOf<DownloadItem>()

            //if scheduler is on
            val useScheduler = sharedPreferences.getBoolean("use_scheduler", false)
            if (useScheduler && !alarmScheduler.isDuringTheScheduledTime()){
                alarmScheduler.schedule()
            }

            if (items.any { it.playlistTitle.isEmpty() } && items.size > 1){
                items.forEachIndexed { index, it -> it.playlistTitle = "Various[${index+1}]" }
            }

            items.forEach {
                if (it.status != DownloadRepository.Status.ActivePaused.toString()) it.status = DownloadRepository.Status.Queued.toString()
                val currentCommand = infoUtil.buildYoutubeDLRequest(it)
                val parsedCurrentCommand = infoUtil.parseYTDLRequestString(currentCommand)
                val existingDownload = activeAndQueuedDownloads.firstOrNull{d ->
                    d.id = 0
                    d.logID = null
                    d.customFileNameTemplate = it.customFileNameTemplate
                    d.status = DownloadRepository.Status.Queued.toString()
                    d.toString() == it.toString()
                }
                if (existingDownload != null) {
                    it.id = existingDownload.id
                    existing.add(it)
                }else{
                    //check if downloaded and file exists
                    val history = withContext(Dispatchers.IO){
                        historyRepo.getAllByURL(it.url).filter { item -> item.downloadPath.any { path -> FileUtil.exists(path) } }
                    }

                    val existingHistory = history.firstOrNull {
                            h -> h.command.replace("(-P \"(.*?)\")|(--trim-filenames \"(.*?)\")".toRegex(), "") == parsedCurrentCommand.replace("(-P \"(.*?)\")|(--trim-filenames \"(.*?)\")".toRegex(), "")
                    }

                    if (existingHistory != null){
                        it.id = existingHistory.id
                        existing.add(it)
                    }else{
                        if (it.id == 0L){
                            it.id = downloadRepo.insert(it)
                        }else if (it.status == DownloadRepository.Status.Queued.toString()){
                            downloadRepo.update(it)
                        }

                        queuedItems.add(it)
                    }
                }
            }

            if (!useScheduler || alarmScheduler.isDuringTheScheduledTime() || queuedItems.any { it.downloadStartTime > 0L } ){
                downloadRepo.startDownloadWorker(queuedItems, context, Data.Builder().putBoolean("createResultItem", false))

                if(!useScheduler){
                    queuedItems.filter { it.downloadStartTime != 0L || (it.title.isEmpty() || it.author.isEmpty() || it.thumb.isEmpty()) }.forEach {
                        runCatching {
                            resultsRepo.updateDownloadItem(it)?.apply {
                                downloadRepo.updateWithoutUpsert(this)
                            }
                        }
                    }
                }else{
                    queuedItems.filter { it.title.isEmpty() || it.author.isEmpty() || it.thumb.isEmpty() }.forEach {
                        runCatching {
                            resultsRepo.updateDownloadItem(it)?.apply {
                                downloadRepo.updateWithoutUpsert(this)
                            }
                        }
                    }
                }
            }

            item.alreadyProcessedLinks.removeAll(items.map { it.url })
            item.alreadyProcessedLinks.addAll(items.map { it.url })
        }

        item.runCount = item.runCount + 1

        if (item.runCount > item.endsAfterCount && item.endsAfterCount > 0){
            item.status = ObserveSourcesRepository.SourceStatus.STOPPED
            withContext(Dispatchers.IO){
                repo.update(item)
            }
            return Result.failure()
        }

        val currentTime = System.currentTimeMillis()
        if (item.endsDate >= currentTime || item.endsAfterCount == item.runCount){
            item.status = ObserveSourcesRepository.SourceStatus.STOPPED
            withContext(Dispatchers.IO){
                repo.update(item)
            }
            repo.cancelObservationTaskByID(context, item.id)
            return Result.success()
        }

        withContext(Dispatchers.IO){
            repo.update(item)
        }

        scheduleForNextTime(item)
        return Result.success()
    }

    private fun scheduleForNextTime(item: ObserveSourcesItem){
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ObserveAlarmReceiver::class.java)
        intent.putExtra("id", item.id)

        val c = Calendar.getInstance()
        val hourMin = Calendar.getInstance()
        hourMin.timeInMillis = item.everyTime
        c.set(Calendar.HOUR_OF_DAY, hourMin.get(Calendar.HOUR_OF_DAY))
        c.set(Calendar.MINUTE, hourMin.get(Calendar.MINUTE))

        when(item.everyCategory){
            ObserveSourcesRepository.EveryCategory.DAY -> {
                c.add(Calendar.DAY_OF_MONTH, item.everyNr)
            }
            ObserveSourcesRepository.EveryCategory.WEEK -> {
                if(item.everyWeekDay.isEmpty()){
                    c.add(Calendar.DAY_OF_MONTH, 7 * item.everyNr)
                }else{
                    val weekDayID = c.get(Calendar.DAY_OF_WEEK).toString()
                    val followingWeekDay = (item.everyWeekDay.firstOrNull { it.toInt() > weekDayID.toInt() } ?: item.everyWeekDay.minBy { it.toInt() }).toInt()
                    c.set(Calendar.DAY_OF_WEEK, followingWeekDay)
                    if (item.everyNr > 1){
                        c.add(Calendar.DAY_OF_MONTH, 7 * item.everyNr)
                    }
                }
            }
            ObserveSourcesRepository.EveryCategory.MONTH -> {
                c.add(Calendar.MONTH, item.everyNr)
                c.set(Calendar.DAY_OF_MONTH, item.everyMonthDay)
            }
        }
        alarmManager.setExact(
            AlarmManager.RTC,
            c.timeInMillis,
            PendingIntent.getBroadcast(context, item.id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
    }

}