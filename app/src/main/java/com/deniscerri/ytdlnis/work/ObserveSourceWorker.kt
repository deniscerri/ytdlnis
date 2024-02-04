package com.deniscerri.ytdlnis.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.deniscerri.ytdlnis.App
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.dao.ResultDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.ObserveSourcesItem
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.repository.HistoryRepository
import com.deniscerri.ytdlnis.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdlnis.receiver.ObserveAlarmReceiver
import com.deniscerri.ytdlnis.util.DownloadUtil
import com.deniscerri.ytdlnis.util.Extensions.closestValue
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
        val historyRepo = HistoryRepository(dbManager.historyDao)
        val downloadRepo = DownloadRepository(dbManager.downloadDao)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val infoUtil = InfoUtil(context)

        val item = repo.getByID(sourceID)
        if (item.status == ObserveSourcesRepository.SourceStatus.STOPPED){
            DownloadUtil.cancelObservationTaskByID(context, item.id)
            return Result.failure()
        }

        val workerID = System.currentTimeMillis().toInt()
        val notification = notificationUtil.createObserveSourcesNotification(item.name)
        val foregroundInfo = ForegroundInfo(workerID, notification)
        setForegroundAsync(foregroundInfo)

        item.runCount = item.runCount + 1

        var res = runCatching { infoUtil.getFromYTDL(item.url).toList() }.getOrElse { listOf() }
        res = if (!item.retryMissingDownloads){
            res.filter { result ->
                //all items that are not present in history
                !historyRepo.getAllByURL(result!!.url).filter {  hi ->
                    hi.downloadPath.any { path -> FileUtil.exists(path) }
                }.map { it.url }.contains(result.url)
            }
        }else{
            //all items that are not already processed
            res.filter { !item.alreadyProcessedLinks.contains(it!!.url) }
        }


        val items = mutableListOf<DownloadItem>()

        res.forEach {
            val string = Gson().toJson(item.downloadItemTemplate, DownloadItem::class.java)
            val downloadItem = Gson().fromJson(string, DownloadItem::class.java)
            downloadItem.url = it!!.url
            downloadItem.id = 0L
            items.add(downloadItem)
        }

        withContext(Dispatchers.IO){
            repo.update(item)
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

            runBlocking {
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
            }


            if (!useScheduler || alarmScheduler.isDuringTheScheduledTime() || queuedItems.any { it.downloadStartTime > 0L } ){
                DownloadUtil.startDownloadWorker(queuedItems, context)

                if(!useScheduler){
                    queuedItems.filter { it.downloadStartTime != 0L || (it.title.isEmpty() || it.author.isEmpty() || it.thumb.isEmpty()) }.forEach {
                        try{
                            updateDownloadItem(it, infoUtil, downloadRepo, dbManager.resultDao)
                        }catch (ignored: Exception){}
                    }
                }else{
                    queuedItems.filter { it.title.isEmpty() || it.author.isEmpty() || it.thumb.isEmpty() }.forEach {
                        try{
                            updateDownloadItem(it, infoUtil, downloadRepo, dbManager.resultDao)
                        }catch (ignored: Exception){}
                    }
                }
            }

            item.alreadyProcessedLinks.addAll(items.map { it.url })
        }

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
            DownloadUtil.cancelObservationTaskByID(context, item.id)
            return Result.success()
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

    private fun updateDownloadItem(
        downloadItem: DownloadItem,
        infoUtil: InfoUtil,
        repository: DownloadRepository,
        resultDao: ResultDao
    ) : Boolean {
        var wasQuickDownloaded = false
        if (downloadItem.title.isEmpty() || downloadItem.author.isEmpty() || downloadItem.thumb.isEmpty()){
            runCatching {
                val info = infoUtil.getMissingInfo(downloadItem.url)
                if (downloadItem.title.isEmpty()) downloadItem.title = info?.title.toString()
                if (downloadItem.author.isEmpty()) downloadItem.author = info?.author.toString()
                downloadItem.duration = info?.duration.toString()
                downloadItem.website = info?.website.toString()
                if (downloadItem.thumb.isEmpty()) downloadItem.thumb = info?.thumb.toString()
                runBlocking {
                    wasQuickDownloaded = resultDao.getCountInt() == 0
                    repository.updateWithoutUpsert(downloadItem)
                }
            }
        }
        return wasQuickDownloaded
    }

}