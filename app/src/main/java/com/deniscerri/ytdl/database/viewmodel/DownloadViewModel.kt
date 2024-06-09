package com.deniscerri.ytdl.database.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.paging.PagingData
import androidx.preference.PreferenceManager
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.dao.CommandTemplateDao
import com.deniscerri.ytdl.database.dao.DownloadDao
import com.deniscerri.ytdl.database.models.AudioPreferences
import com.deniscerri.ytdl.database.models.CommandTemplate
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.DownloadItemSimple
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.models.VideoPreferences
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.repository.HistoryRepository
import com.deniscerri.ytdl.database.repository.ResultRepository
import com.deniscerri.ytdl.ui.downloadcard.FormatTuple
import com.deniscerri.ytdl.util.Extensions.toListString
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.InfoUtil
import com.deniscerri.ytdl.work.UpdatePlaylistFormatsWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale


class DownloadViewModel(private val application: Application) : AndroidViewModel(application) {
    private val dbManager: DBManager
    val repository : DownloadRepository
    private val sharedDownloadViewModel: SharedDownloadViewModel
    private val sharedPreferences: SharedPreferences
    private val commandTemplateDao: CommandTemplateDao
    private val infoUtil : InfoUtil
    val allDownloads : Flow<PagingData<DownloadItem>>
    val queuedDownloads : Flow<PagingData<DownloadItemSimple>>
    val activeDownloads : Flow<List<DownloadItem>>
    val allProcessingDownloads : Flow<List<DownloadItem>>
    var mostRecentProcessingDownloads: Flow<List<DownloadItem>>
    val cancelledDownloads : Flow<PagingData<DownloadItemSimple>>
    val erroredDownloads : Flow<PagingData<DownloadItemSimple>>
    val savedDownloads : Flow<PagingData<DownloadItemSimple>>
    val scheduledDownloads : Flow<PagingData<DownloadItemSimple>>

    val activeDownloadsCount : Flow<Int>
    val queuedDownloadsCount : Flow<Int>
    val activeQueuedDownloadsCount : Flow<Int>
    val cancelledDownloadsCount : Flow<Int>
    val erroredDownloadsCount : Flow<Int>
    val savedDownloadsCount : Flow<Int>
    val scheduledDownloadsCount : Flow<Int>

    val alreadyExistsUiState: MutableStateFlow<List<SharedDownloadViewModel.AlreadyExistsIDs>>

    private var bestVideoFormat : Format
    private var bestAudioFormat : Format
    private var defaultVideoFormats : MutableList<Format>

    private val videoQualityPreference: String
    private val formatIDPreference: List<String>
    private val audioFormatIDPreference: List<String>
    private val resources : Resources
    private var extraCommandsForAudio: String = ""
    private var extraCommandsForVideo: String = ""

    private var audioContainer: String?
    private var videoContainer: String?
    private var videoCodec: String?
    private var audioCodec: String?
    private val dao: DownloadDao
    private val historyRepository: HistoryRepository
    private val resultRepository: ResultRepository

    enum class Type {
        auto, audio, video, command
    }


    val processingDownloads = MediatorLiveData<List<DownloadItem>>(listOf())
    var processingItemsFlow : ProcessingItemsJob? = null
    var processingItems = MutableStateFlow(false)

    init {
        dbManager =  DBManager.getInstance(application)
        dao = dbManager.downloadDao
        repository = DownloadRepository(dao)
        sharedDownloadViewModel = SharedDownloadViewModel(application)
        alreadyExistsUiState = sharedDownloadViewModel.alreadyExistsUiState
        historyRepository = HistoryRepository(dbManager.historyDao)
        resultRepository = ResultRepository(dbManager.resultDao, application)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
        commandTemplateDao = DBManager.getInstance(application).commandTemplateDao
        infoUtil = InfoUtil(application)

        activeDownloadsCount = repository.activeDownloadsCount
        queuedDownloadsCount = repository.queuedDownloadsCount
        activeQueuedDownloadsCount = repository.activeQueuedDownloadsCount
        cancelledDownloadsCount = repository.cancelledDownloadsCount
        erroredDownloadsCount = repository.erroredDownloadsCount
        savedDownloadsCount = repository.savedDownloadsCount
        scheduledDownloadsCount = repository.scheduledDownloadsCount

        allDownloads = repository.allDownloads.flow
        queuedDownloads = repository.queuedDownloads.flow
        activeDownloads = repository.activeDownloads
        allProcessingDownloads = repository.processingDownloads
        mostRecentProcessingDownloads = repository.getProcessingItemsFromID(0)
        savedDownloads = repository.savedDownloads.flow
        scheduledDownloads = repository.scheduledDownloads.flow
        cancelledDownloads = repository.cancelledDownloads.flow
        erroredDownloads = repository.erroredDownloads.flow
        viewModelScope.launch(Dispatchers.IO){
            if (sharedPreferences.getBoolean("use_extra_commands", false)){
                extraCommandsForAudio = commandTemplateDao.getAllTemplatesAsExtraCommandsForAudio().joinToString(" ")
                extraCommandsForVideo = commandTemplateDao.getAllTemplatesAsExtraCommandsForVideo().joinToString(" ")
            }
        }

        videoQualityPreference = sharedPreferences.getString("video_quality", application.getString(R.string.best_quality)).toString()
        formatIDPreference = sharedPreferences.getString("format_id", "").toString().split(",").filter { it.isNotEmpty() }
        audioFormatIDPreference = sharedPreferences.getString("format_id_audio", "").toString().split(",").filter { it.isNotEmpty() }

        val confTmp = Configuration(application.resources.configuration)
        confTmp.setLocale(Locale(sharedPreferences.getString("app_language", "en")!!))
        val metrics = DisplayMetrics()
        resources = Resources(application.assets, metrics, confTmp)


        videoContainer = sharedPreferences.getString("video_format",  "Default")
        defaultVideoFormats = infoUtil.getGenericVideoFormats(resources)
        bestVideoFormat = defaultVideoFormats.first()

        audioContainer = sharedPreferences.getString("audio_format", "mp3")
        bestAudioFormat = if (audioFormatIDPreference.isEmpty()){
            infoUtil.getGenericAudioFormats(resources).first()
        }else{
            Format(
                audioFormatIDPreference.first().split("+").first(),
                audioContainer!!,
                "",
                "",
                "",
                0,
                audioFormatIDPreference.first().split("+").first()
            )
        }

        videoCodec = sharedPreferences.getString("video_codec", "")
        audioCodec = sharedPreferences.getString("audio_codec", "")
    }

    fun deleteDownload(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        repository.delete(id)
    }

    suspend fun updateDownload(item: DownloadItem){
        if (sharedPreferences.getBoolean("incognito", false)){
            if (item.status == DownloadRepository.Status.Cancelled.toString() || item.status == DownloadRepository.Status.Error.toString()){
                repository.delete(item.id)
                return
            }
        }

        repository.update(item)
    }

    fun getItemByID(id: Long) : DownloadItem {
        return repository.getItemByID(id)
    }

    fun getAllByIDs(ids: List<Long>) : List<DownloadItem> {
        return repository.getAllItemsByIDs(ids)
    }

    fun getHistoryItemById(id: Long) : HistoryItem? {
        return historyRepository.getItem(id)
    }

    fun getDownloadType(t: Type? = null, url: String) : Type {
        return sharedDownloadViewModel.getDownloadType(t, url)
    }

    fun createDownloadItemFromResult(result: ResultItem?, url: String = "", givenType: Type) : DownloadItem {
        return sharedDownloadViewModel.createDownloadItemFromResult(result, url, givenType)
    }

    fun createResultItemFromDownload(downloadItem: DownloadItem) : ResultItem {
        return sharedDownloadViewModel.createResultItemFromDownload(downloadItem)
    }

    fun createResultItemFromHistory(downloadItem: HistoryItem) : ResultItem {
        return ResultItem(
            0,
            downloadItem.url,
            downloadItem.title,
            downloadItem.author,
            downloadItem.duration,
            downloadItem.thumb,
            downloadItem.website,
            "",
            arrayListOf(),
            "",
            arrayListOf(),
            "",
            null,
            System.currentTimeMillis()
        )

    }

    fun createEmptyResultItem(url: String) : ResultItem {
        return sharedDownloadViewModel.createEmptyResultItem(url)
    }

    fun switchDownloadType(list: List<DownloadItem>, type: Type) : List<DownloadItem>{

        list.forEach {
            val format = getFormat(it.allFormats, type)
            it.format = format

            var updatedDownloadPath: String = ""
            var container: String = ""

            when(type){
                Type.audio -> {
                    updatedDownloadPath = sharedPreferences.getString("music_path", FileUtil.getDefaultAudioPath())!!
                    container = sharedPreferences.getString("audio_format", "")!!
                }
                Type.video -> {
                    updatedDownloadPath = sharedPreferences.getString("music_path", FileUtil.getDefaultAudioPath())!!
                    container = sharedPreferences.getString("audio_format", "")!!
                }
                Type.command -> {
                    updatedDownloadPath = sharedPreferences.getString("music_path", FileUtil.getDefaultAudioPath())!!
                    container = ""
                }
                else -> {
                    updatedDownloadPath = ""
                }
            }

            it.downloadPath = updatedDownloadPath
            it.type = type
            it.container = container
        }
        return list
    }

    fun createDownloadItemFromHistory(historyItem: HistoryItem) : DownloadItem {
        val embedSubs = sharedPreferences.getBoolean("embed_subtitles", false)
        val saveSubs = sharedPreferences.getBoolean("write_subtitles", false)
        val saveAutoSubs = sharedPreferences.getBoolean("write_auto_subtitles", false)
        val addChapters = sharedPreferences.getBoolean("add_chapters", false)
        val saveThumb = sharedPreferences.getBoolean("write_thumbnail", false)
        val embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false)
        val cropThumb = sharedPreferences.getBoolean("crop_thumbnail", false)

        val customFileNameTemplate = when(historyItem.type) {
            Type.audio -> sharedPreferences.getString("file_name_template_audio", "%(uploader).30B - %(title).170B")
            Type.video -> sharedPreferences.getString("file_name_template", "%(uploader).30B - %(title).170B")
            else -> ""
        }

        val container = when(historyItem.type){
            Type.audio -> sharedPreferences.getString("audio_format", "Default")!!
            Type.video -> sharedPreferences.getString("video_format", "Default")!!
            else -> ""
        }

        val defaultPath = when(historyItem.type) {
            Type.audio -> FileUtil.getDefaultAudioPath()
            Type.video -> FileUtil.getDefaultVideoPath()
            Type.command -> FileUtil.getDefaultCommandPath()
            else -> ""
        }

        val sponsorblock = sharedPreferences.getStringSet("sponsorblock_filters", emptySet())

        val extraCommands = when(historyItem.type){
            Type.audio -> extraCommandsForAudio
            Type.video -> extraCommandsForVideo
            else -> ""
        }

        val audioPreferences = AudioPreferences(embedThumb, cropThumb,false, ArrayList(sponsorblock!!))
        val videoPreferences = VideoPreferences(embedSubs, addChapters, false, ArrayList(sponsorblock), saveSubs, saveAutoSubs)
        var path = defaultPath
        historyItem.downloadPath.first().apply {
            File(this).parent?.apply {
                if (File(this).exists()){
                    path = this
                }
            }

        }
        return DownloadItem(0,
            historyItem.url,
            historyItem.title,
            historyItem.author,
            historyItem.thumb,
            historyItem.duration,
            historyItem.type,
            historyItem.format,
            container,
            "",
            ArrayList(),
            path, historyItem.website, "", "", audioPreferences, videoPreferences, extraCommands, customFileNameTemplate!!, saveThumb, DownloadRepository.Status.Queued.toString(), 0, null
        )

    }


    fun getPreferredAudioRequirements(): MutableList<(Format) -> Int> {
        return sharedDownloadViewModel.getPreferredVideoRequirements()
    }

    //requirement and importance
    @SuppressLint("RestrictedApi")
    fun getPreferredVideoRequirements(): MutableList<(Format) -> Int> {
        return sharedDownloadViewModel.getPreferredVideoRequirements()
    }

    fun getFormat(formats: List<Format>, type: Type) : Format {
        return sharedDownloadViewModel.getFormat(formats, type)
    }

    fun getPreferredAudioFormats(formats: List<Format>) : ArrayList<String>{
        return sharedDownloadViewModel.getPreferredAudioFormats(formats)
    }

    fun generateCommandFormat(c: CommandTemplate) : Format {
        return sharedDownloadViewModel.generateCommandFormat(c)
    }

    data class ProcessingItemsJob(
        var job: Job? = null,
        var originItemType: String,
        var originItemIDs: List<Long>,
        var processingDownloadItemIDs: MutableList<Long> = mutableListOf()
    )
    private fun updateProcessingJobData(item: ProcessingItemsJob?) {
        processingItemsFlow = item
    }

    fun turnDownloadItemsToProcessingDownloads(itemIDs: List<Long>) = viewModelScope.launch(Dispatchers.IO){
        updateProcessingJobData(ProcessingItemsJob(null, DownloadItem::class.java.toString(), itemIDs))
        val job = viewModelScope.launch(Dispatchers.IO) {
            processingItems.emit(true)
            try {
                itemIDs.forEachIndexed { idx, it ->
                    val item = repository.getItemByID(it)
                    if (!isActive) throw CancellationException()

                    item.id = 0
                    item.status = DownloadRepository.Status.Processing.toString()
                    processingItemsFlow?.apply {
                        val id = repository.insert(item)
                        if (idx == 0) mostRecentProcessingDownloads = repository.getProcessingItemsFromID(id)
                        processingDownloadItemIDs.add(id)
                        updateProcessingJobData(this)
                    }
                }
                processingItems.emit(false)
            } catch (e: Exception) {
                processingItemsFlow?.apply {
                    this.processingDownloadItemIDs.chunked(100).forEach {
                        repository.deleteAllWithIDs(it)
                    }
                }
                updateProcessingJobData(null)
                processingItems.emit(false)
            }
        }
        updateProcessingJobData(ProcessingItemsJob(job, DownloadItem::class.java.toString(), itemIDs))
    }


    fun turnResultItemsToProcessingDownloads(itemIDs: List<Long>, downloadNow: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        updateProcessingJobData(ProcessingItemsJob(null, ResultItem::class.java.toString(), itemIDs))
        val job = viewModelScope.launch(Dispatchers.IO) {
            processingItems.emit(true)
            try {
                itemIDs.forEach { id ->
                    val item = resultRepository.getItemByID(id) ?: return@forEach
                    val preferredType = getDownloadType(url = item.url).toString()
                    val downloadItem = createDownloadItemFromResult(result = item, givenType = Type.valueOf(
                        preferredType
                    ))
                    downloadItem.status = DownloadRepository.Status.Processing.toString()

                    if (!isActive) {
                        throw CancellationException()
                    }

                    if (downloadNow) {
                        downloadItem.status = DownloadRepository.Status.Queued.toString()
                        queueDownloads(listOf(downloadItem))
                    }else{
                        processingItemsFlow?.apply {
                            processingDownloadItemIDs.add(repository.insert(downloadItem))
                            updateProcessingJobData(this)
                        }
                    }
                }
                processingItems.emit(false)
            }catch (e: Exception) {
                processingItemsFlow?.apply {
                    this.processingDownloadItemIDs.chunked(100).forEach {
                        repository.deleteAllWithIDs(it)
                    }
                }
                updateProcessingJobData(null)
                processingItems.emit(false)
            }
        }

        updateProcessingJobData(ProcessingItemsJob(job, ResultItem::class.java.toString(), itemIDs))
    }

    fun insert(item: DownloadItem) = viewModelScope.launch(Dispatchers.IO){
        repository.insert(item)
    }

    fun insertAll(items: List<DownloadItem>)= viewModelScope.launch(Dispatchers.IO){
        items.forEach{
            repository.insert(it)
        }
    }

    fun insertToProcessing(items: List<DownloadItem>)= viewModelScope.launch(Dispatchers.IO){
        repository.deleteProcessing()
        items.forEach{
            it.status = DownloadRepository.Status.Processing.toString()
            repository.insert(it)
        }
    }

    fun deleteCancelled() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteCancelled()
    }

    fun deleteScheduled() = viewModelScope.launch(Dispatchers.IO) {
        val scheduledIds = repository.getScheduledDownloadIDs()
        scheduledIds.forEach {
            WorkManager.getInstance(application).cancelAllWorkByTag(it.toString())
        }
        repository.deleteScheduled()
    }

    fun deleteErrored() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteErrored()
    }

    fun deleteSaved() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteSaved()
    }

    fun deleteProcessing() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteProcessing()
    }

    fun deleteAllWithID(ids: List<Long>) = viewModelScope.launch(Dispatchers.IO){
        repository.deleteAllWithIDs(ids)
    }

    fun cancelActiveQueued() = viewModelScope.launch(Dispatchers.IO) {
        processingItemsFlow?.apply { this.job?.cancel(CancellationException()) }
        repository.cancelActiveQueued()
    }

    fun getQueued() : List<DownloadItem> {
        return repository.getQueuedDownloads()
    }

    fun getScheduled() : List<DownloadItem> {
        return repository.getScheduledDownloads()
    }

    fun getCancelled() : List<DownloadItem> {
        return repository.getCancelledDownloads()
    }
    fun getErrored() : List<DownloadItem> {
        return repository.getErroredDownloads()
    }

    fun getSaved() : List<DownloadItem> {
        return dao.getSavedDownloadsList()
    }

    fun getActiveDownloads() : List<DownloadItem>{
        return repository.getActiveDownloads()
    }

    fun getActiveDownloadsCount() : Int {
        return dao.getDownloadsCountByStatus(listOf(DownloadRepository.Status.Active, DownloadRepository.Status.ActivePaused).toListString())
    }

    fun getQueuedDownloadsCount() : Int {
        return dao.getDownloadsCountByStatus(listOf(DownloadRepository.Status.Queued).toListString())
    }

    fun getActiveAndQueuedDownloadIDs() : List<Long>{
        return repository.getActiveAndQueuedDownloadIDs()
    }

    suspend fun resetScheduleTimeForItemsAndStartDownload(items: List<Long>) = CoroutineScope(Dispatchers.IO).launch {
        dbManager.downloadDao.resetScheduleTimeForItems(items)
        repository.startDownloadWorker(emptyList(), application)
    }

    suspend fun resetActivePaused() {
        dbManager.downloadDao.resetActivePausedItems()
    }

    suspend fun startDownloadWorker(list: List<DownloadItem>){
        repository.startDownloadWorker(list, application)
    }

    suspend fun putAtTopOfQueue(ids: List<Long>) = CoroutineScope(Dispatchers.IO).launch{
        val downloads = dao.getQueuedDownloadsListIDs()
        val lastID = ids.maxOf { it }
        ids.forEach { dao.updateDownloadID(it, -it) }
        val newIDs = downloads.take(ids.size)

        //other ids that need to move around
        val takenPositions = mutableListOf<Long>()
        for (dID in downloads.filter { !ids.contains(it) && it < lastID }.reversed()){
            val newID = downloads.last { !newIDs.contains(it) && !takenPositions.contains(it) && it <= lastID }
            takenPositions.add(newID)
            dao.updateDownloadID(dID, newID)
        }

        ids.forEachIndexed { idx, it ->
            dao.updateDownloadID(-it, newIDs[idx])
        }
    }


    suspend fun putAtBottomOfQueue(ids: List<Long>) = CoroutineScope(Dispatchers.IO).launch{
        val downloads = dao.getQueuedDownloadsListIDs()
        ids.forEach { dao.updateDownloadID(it, -it)}
        val newIDs = downloads.sortedByDescending { it }.take(ids.size)

        //other ids that need to move around
        val takenPositions = mutableListOf<Long>()
        for (dID in downloads.filter { !ids.contains(it) }){
            val newID = downloads.first { !newIDs.contains(it) && !takenPositions.contains(it) }
            takenPositions.add(newID)
            dao.updateDownloadID(dID, newID)
        }

        ids.reversed().forEachIndexed { idx, it ->
            dao.updateDownloadID(-it, newIDs[idx])
        }
    }


    fun putAtPosition(current: Long, id: Long) = CoroutineScope(Dispatchers.IO).launch {
        val downloads = dao.getQueuedDownloadsListIDs()
        dao.updateDownloadID(current, -current)

        if (current > id){
            for (dID in downloads.filter { it in id until current }.reversed()){
                val index = downloads.indexOf(dID)
                dao.updateDownloadID(dID, downloads[index + 1])
            }
        }else{
            for (dID in downloads.filter { it in (current + 1)..id }){
                val index = downloads.indexOf(dID)
                dao.updateDownloadID(dID, downloads[index - 1])
            }
        }
        dao.updateDownloadID(-current, id)
    }

    fun reQueueDownloadItems(items: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        dbManager.downloadDao.reQueueDownloadItems(items)
        repository.startDownloadWorker(emptyList(), application)
    }

    suspend fun queueDownloads(items: List<DownloadItem>, ign : Boolean = false) : List<SharedDownloadViewModel.AlreadyExistsIDs> {
        val ids = sharedDownloadViewModel.queueDownloads(items, ign)
        return ids
    }

    fun getQueuedCollectedFileSize() : Long {
        return dbManager.downloadDao.getSelectedFormatFromQueued().filter { it.filesize > 10 }.sumOf { it.filesize }
    }

    fun getTotalSize(status: List<DownloadRepository.Status>) : LiveData<Int> {
        return dbManager.downloadDao.getDownloadsCountByStatusFlow(status.map { it.toString() }).asLiveData()
    }

    fun checkAllQueuedItemsAreScheduledAfterNow(items: List<Long>, inverted: Boolean, currentStartTime: Long) : Boolean {
        return dbManager.downloadDao.checkAllQueuedItemsAreScheduledAfterNow(items, inverted.toString(), currentStartTime)
    }

    fun getItemIDsNotPresentIn(items: List<Long>, status: List<DownloadRepository.Status>) : List<Long> {
        return dbManager.downloadDao.getDownloadIDsNotPresentInList(items.ifEmpty { listOf(-1L) }, status.map { it.toString() })
    }

    fun moveProcessingToSavedCategory(ids: List<Long>){
        ids.chunked(100).forEach {
            dao.updateProcessingtoSavedStatus(it)
        }
    }

    suspend fun updateProcessingFormat(ids: List<Long>, selectedFormats: List<FormatTuple>): List<Long> {
        return ids.chunked(100).map {
            val items = repository.getAllItemsByIDs(it)
            items.forEachIndexed { index, i ->
                selectedFormats[index].format?.apply {
                    i.format = this
                }
                if (i.type == Type.video) selectedFormats[index].audioFormats?.map { it.format_id }?.let { i.videoPreferences.audioFormatIDs.addAll(it) }
                repository.update(i)
            }

            items.map { itm -> itm.format.filesize }
        }.flatten()
    }

    suspend fun updateProcessingCommandFormat(ids: List<Long>, format: Format){
        ids.chunked(100).forEach {
            repository.getAllItemsByIDs(it).forEach { i ->
                i.format = format
                repository.update(i)
            }
        }
    }

    suspend fun updateProcessingDownloadPath(ids: List<Long>, path: String){
        ids.chunked(100).forEach {
            repository.getAllItemsByIDs(it).forEach { i ->
                i.downloadPath = path
                repository.update(i)
            }
        }
    }

    fun getProcessingDownloadsCount() : Int {
        return dao.getDownloadsCountByStatus(listOf(DownloadRepository.Status.Processing.toString()))
    }

    fun getProcessingDownloads() : List<DownloadItem> {
        return repository.getProcessingDownloads()
    }


    suspend fun updateProcessingAllFormats(ids: List<Long>, formatCollection: List<List<Format>>) {
        ids.chunked(100).forEach {
            val items = repository.getAllItemsByIDs(it)
            items.forEachIndexed { index, i ->
                i.allFormats.clear()
                if (formatCollection.size == items.size && formatCollection[index].isNotEmpty()) {
                    runCatching {
                        i.allFormats.addAll(formatCollection[index])
                    }
                }
                i.format = getFormat(i.allFormats, i.type)
                kotlin.runCatching {
                    dbManager.resultDao.getResultByURL(i.url)?.apply {
                        this.formats = formatCollection[index].toMutableList()
                        dbManager.resultDao.update(this)
                    }
                }
                repository.update(i)
            }
        }

    }

    suspend fun continueUpdatingFormatsOnBackground(itemIDs: List<Long>){
        itemIDs.chunked(100).forEach {list ->
            repository.getAllItemsByIDs(list).onEach {
                it.status = DownloadRepository.Status.Saved.toString()
                repository.update(it)
            }
        }

        val id = System.currentTimeMillis().toInt()
        val workRequest = OneTimeWorkRequestBuilder<UpdatePlaylistFormatsWorker>()
            .setInputData(
                Data.Builder()
                    .putLongArray("ids", itemIDs.toLongArray())
                    .putInt("id", id)
                    .build())
            .addTag("updateFormats")
            .build()
        val context = App.instance
        WorkManager.getInstance(context).enqueueUniqueWork(
            id.toString(),
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

    }

    suspend fun updateProcessingType(ids: List<Long>, newType: Type) {
        ids.chunked(100).forEach { _ ->
            repository.getAllItemsByIDs(ids).apply {
                val new = switchDownloadType(this, newType)
                new.forEach {
                    repository.update(it)
                }
            }
        }
    }

    fun checkIfAllProcessingItemsHaveSameType(ids: List<Long>) : Pair<Boolean, Type> {
        val types = mutableSetOf<String>()
        ids.chunked(100).forEach {
            types.addAll(dao.getProcessingDownloadTypes(it))
        }

        return Pair(types.size == 1, Type.valueOf(types.first()))
    }


    suspend fun updateItemsWithIdsToProcessingStatus(ids: List<Long>) {
        repository.deleteProcessing()
        dao.updateItemsToProcessing(ids)
        val first = dao.getFirstProcessingDownload()
    }

    suspend fun updateToStatus(id: Long, status: DownloadRepository.Status) {
        repository.setDownloadStatus(id, status)
    }

    fun getURLsByStatus(list: List<DownloadRepository.Status>) : List<String> {
        return dao.getURLsByStatus(list.map { it.toString() })
    }

    fun getURLsByIds(list: List<Long>) : List<String> {
        return dao.getURLsByID(list)
    }

    fun getIDsBetweenTwoItems(item1: Long, item2: Long, statuses: List<String>) : List<Long> {
        return dao.getIDsBetweenTwoItems(item1, item2, statuses)
    }

    fun getScheduledIDsBetweenTwoItems(item1: Long, item2: Long) : List<Long> {
        return dao.getScheduledIDsBetweenTwoItems(item1, item2)
    }


}