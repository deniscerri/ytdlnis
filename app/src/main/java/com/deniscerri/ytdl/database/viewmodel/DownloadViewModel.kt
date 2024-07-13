package com.deniscerri.ytdl.database.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
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
import com.deniscerri.ytdl.ui.downloadcard.MultipleItemFormatTuple
import com.deniscerri.ytdl.util.Extensions.toListString
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.FormatSorter
import com.deniscerri.ytdl.util.InfoUtil
import com.deniscerri.ytdl.work.AlarmScheduler
import com.deniscerri.ytdl.work.UpdateMultipleDownloadsFormatsWorker
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File
import java.util.Locale


class DownloadViewModel(private val application: Application) : AndroidViewModel(application) {
    private val dbManager: DBManager
    val repository : DownloadRepository
    private val sharedPreferences: SharedPreferences
    private val commandTemplateDao: CommandTemplateDao
    private val infoUtil : InfoUtil
    private val resources : Resources

    val allDownloads : Flow<PagingData<DownloadItem>>
    val queuedDownloads : Flow<PagingData<DownloadItemSimple>>
    val activeDownloads : Flow<List<DownloadItem>>
    val processingDownloads : Flow<List<DownloadItemSimple>>
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

    @Parcelize
    data class AlreadyExistsIDs(
        var downloadItemID: Long,
        var historyItemID : Long?
    ) : Parcelable

    val alreadyExistsUiState: MutableStateFlow<List<AlreadyExistsIDs>> = MutableStateFlow(
        mutableListOf()
    )

    private var extraCommandsForAudio: String = ""
    private var extraCommandsForVideo: String = ""

    private val dao: DownloadDao
    private val historyRepository: HistoryRepository
    private val resultRepository: ResultRepository

    enum class Type {
        auto, audio, video, command
    }

    private val urlsForAudioType = listOf(
        "music",
        "audio",
        "soundcloud"
    )

    var processingItems = MutableStateFlow(false)
    var processingItemsJob : Job? = null

    init {
        dbManager =  DBManager.getInstance(application)
        dao = dbManager.downloadDao
        repository = DownloadRepository(dao)
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
        processingDownloads = repository.processingDownloads
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


        val confTmp = Configuration(application.resources.configuration)
        confTmp.setLocale(Locale(sharedPreferences.getString("app_language", "en")!!))
        val metrics = DisplayMetrics()
        resources = Resources(application.assets, metrics, confTmp)
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
        var type = t

        if (type == null){
            val preferredDownloadType = sharedPreferences.getString("preferred_download_type", Type.auto.toString())
            type = if (sharedPreferences.getBoolean("remember_download_type", false)){
                Type.valueOf(sharedPreferences.getString("last_used_download_type",
                    preferredDownloadType)!!)
            }else{
                Type.valueOf(preferredDownloadType!!)
            }
        }

        return when(type){
            Type.auto -> {
                if (urlsForAudioType.any { url.contains(it) }){
                    Type.audio
                }else{
                    Type.video
                }
            }

            else -> type
        }
    }

    fun createDownloadItemFromResult(result: ResultItem?, url: String = "", givenType: Type) : DownloadItem {
        val resultItem = result ?: createEmptyResultItem(url)

        val embedSubs = sharedPreferences.getBoolean("embed_subtitles", false)
        val saveSubs = sharedPreferences.getBoolean("write_subtitles", false)
        val saveAutoSubs = sharedPreferences.getBoolean("write_auto_subtitles", false)
        val addChapters = sharedPreferences.getBoolean("add_chapters", false)
        val saveThumb = sharedPreferences.getBoolean("write_thumbnail", false)
        val embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false)
        val cropThumb = sharedPreferences.getBoolean("crop_thumbnail", false)

        var type = getDownloadType(givenType, resultItem.url)
        if(type == Type.command && commandTemplateDao.getTotalNumber() == 0) type = Type.video

        val customFileNameTemplate = when(type) {
            Type.audio -> sharedPreferences.getString("file_name_template_audio", "%(uploader).30B - %(title).170B")
            Type.video -> sharedPreferences.getString("file_name_template", "%(uploader).30B - %(title).170B")
            else -> ""
        }

        val downloadPath = when(type){
            Type.audio -> sharedPreferences.getString("music_path", FileUtil.getDefaultAudioPath())
            Type.video -> sharedPreferences.getString("video_path",  FileUtil.getDefaultVideoPath())
            else -> sharedPreferences.getString("command_path", FileUtil.getDefaultCommandPath())
        }

        val container = when(type){
            Type.audio -> sharedPreferences.getString("audio_format", "")
            else -> sharedPreferences.getString("video_format", "")
        }


        val sponsorblock = sharedPreferences.getStringSet("sponsorblock_filters", emptySet())

        val audioPreferences = AudioPreferences(embedThumb, cropThumb,false, ArrayList(sponsorblock!!))


        val preferredAudioFormats = getPreferredAudioFormats(resultItem.formats)

        val videoPreferences = VideoPreferences(
            embedSubs,
            addChapters, false,
            ArrayList(sponsorblock),
            saveSubs,
            saveAutoSubs,
            audioFormatIDs = preferredAudioFormats
        )

        val extraCommands = when(type){
            Type.audio -> extraCommandsForAudio
            Type.video -> extraCommandsForVideo
            else -> ""
        }

        return DownloadItem(0,
            resultItem.url,
            resultItem.title,
            resultItem.author,
            resultItem.thumb,
            resultItem.duration,
            type,
            getFormat(resultItem.formats, type),
            container!!,
            "",
            resultItem.formats,
            downloadPath!!, resultItem.website,
            "",
            if (resultItem.playlistTitle == resultRepository.YTDLNIS_SEARCH) "" else resultItem.playlistTitle,
            audioPreferences,
            videoPreferences,
            extraCommands,
            customFileNameTemplate!!,
            saveThumb,
            DownloadRepository.Status.Queued.toString(),
            0,
            null,
            playlistURL = resultItem.playlistURL,
            playlistIndex = resultItem.playlistIndex,
            incognito = sharedPreferences.getBoolean("incognito", false)
        )
    }

    fun createResultItemFromDownload(downloadItem: DownloadItem) : ResultItem {
        return ResultItem(
            0,
            downloadItem.url,
            downloadItem.title,
            downloadItem.author,
            downloadItem.duration,
            downloadItem.thumb,
            downloadItem.website,
            downloadItem.playlistTitle,
            downloadItem.allFormats,
            "",
            arrayListOf(),
            downloadItem.playlistURL,
            downloadItem.playlistIndex,
            System.currentTimeMillis()
        )
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
        return ResultItem(
            0,
            url,
            "",
            "",
            "",
            "",
            "",
            "",
            arrayListOf(),
            "",
            arrayListOf(),
            "",
            null,
            System.currentTimeMillis()
        )
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
            path,
            historyItem.website,
            "",
            "",
            audioPreferences,
            videoPreferences,
            extraCommands,
            customFileNameTemplate!!,
            saveThumb,
            DownloadRepository.Status.Queued.toString(),
            0,
            null,
            incognito = sharedPreferences.getBoolean("incognito", false)
        )

    }


    fun getFormat(formats: List<Format>, type: Type) : Format {
        when(type) {
            Type.audio -> {
                return cloneFormat (
                    try {
                        val theFormats = formats.filter { it.vcodec.isBlank() || it.vcodec == "none" }
                        FormatSorter(application).sortAudioFormats(theFormats).first()
                    }catch (e: Exception){
                        infoUtil.getGenericAudioFormats(resources).first()
                    }
                )

            }
            Type.video -> {
                return cloneFormat(
                    try {
                        val theFormats = formats.filter { it.vcodec.isNotBlank() && it.vcodec != "none" }.ifEmpty {
                            infoUtil.getGenericVideoFormats(resources).sortedByDescending { it.filesize }
                        }

                        FormatSorter(application).sortVideoFormats(theFormats).first()
                    }catch (e: Exception){
                        infoUtil.getGenericVideoFormats(resources).first()
                    }
                )
            }
            else -> {
                val lastUsedCommandTemplate = sharedPreferences.getString("lastCommandTemplateUsed", "")!!
                val c = if (lastUsedCommandTemplate.isBlank()){
                    commandTemplateDao.getFirst() ?: CommandTemplate(0,"","", useAsExtraCommand = false, useAsExtraCommandAudio = false, useAsExtraCommandVideo = false)
                }else{
                    commandTemplateDao.getTemplateByContent(lastUsedCommandTemplate) ?: CommandTemplate(0, "", lastUsedCommandTemplate, useAsExtraCommand = false, useAsExtraCommandAudio = false, useAsExtraCommandVideo = false)
                }
                return generateCommandFormat(c)
            }
        }
    }

    private fun cloneFormat(item: Format) : Format {
        val string = Gson().toJson(item, Format::class.java)
        return Gson().fromJson(string, Format::class.java)
    }

    fun getPreferredAudioFormats(formats: List<Format>) : ArrayList<String>{
        val preferredAudioFormats = arrayListOf<String>()
        val audioFormatIDPreference = sharedPreferences.getString("format_id_audio", "").toString().split(",").filter { it.isNotEmpty() }
        for (f in formats.sortedBy { it.format_id }){
            val fId = audioFormatIDPreference.sorted().find { it.contains(f.format_id) }
            if (fId != null) {
                if (fId.split("+").all { formats.map { f-> f.format_id }.contains(it) }){
                    preferredAudioFormats.addAll(fId.split("+"))
                    break
                }
            }
        }
        if (preferredAudioFormats.isEmpty()){
            val audioF = getFormat(formats, Type.audio)
            if (!infoUtil.getGenericAudioFormats(resources).contains(audioF)){
                preferredAudioFormats.add(audioF.format_id)
            }
        }
        return preferredAudioFormats
    }

    fun generateCommandFormat(c: CommandTemplate) : Format {
        return Format(
            c.title,
            c.id.toString(),
            "",
            "",
            "",
            0,
            c.content.replace("\n", " ")
        )
    }


    fun turnDownloadItemsToProcessingDownloads(itemIDs: List<Long>, deleteExisting : Boolean = false) = viewModelScope.launch(Dispatchers.IO){
        val job = viewModelScope.launch(Dispatchers.IO) {
            repository.deleteProcessing()
            processingItems.emit(true)
            try {
                itemIDs.forEach {
                    val item = repository.getItemByID(it)
                    if (processingItemsJob?.isCancelled == true) throw CancellationException()
                    if (!deleteExisting) item.id = 0
                    item.status = DownloadRepository.Status.Processing.toString()
                    repository.update(item)
                }
                processingItems.emit(false)
            } catch (e: Exception) {
                deleteProcessing()
                processingItems.emit(false)
            }
        }
        processingItemsJob = job
    }


    fun turnResultItemsToProcessingDownloads(itemIDs: List<Long>, downloadNow: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        val job = viewModelScope.launch(Dispatchers.IO) {
            repository.deleteProcessing()
            processingItems.emit(true)
            try {
                val toInsert = mutableListOf<DownloadItem>()
                itemIDs.forEach { id ->
                    val item = resultRepository.getItemByID(id) ?: return@forEach
                    val preferredType = getDownloadType(url = item.url).toString()
                    val downloadItem = createDownloadItemFromResult(result = item, givenType = Type.valueOf(
                        preferredType
                    ))
                    downloadItem.status = DownloadRepository.Status.Processing.toString()

                    if (processingItemsJob?.isCancelled == true) {
                        throw CancellationException()
                    }

                    if (downloadNow) {
                        downloadItem.status = DownloadRepository.Status.Queued.toString()
                        queueDownloads(listOf(downloadItem))
                    }else{
                        toInsert.add(downloadItem)
                        //repository.insert(downloadItem)
                    }
                }
                repository.insertAll(toInsert)
                processingItems.emit(false)
            }catch (e: Exception) {
                deleteProcessing()
                processingItems.emit(false)
            }
        }

        processingItemsJob = job

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
        repository.deleteScheduled()
    }

    fun deleteErrored() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteErrored()
    }

    fun deleteQueued() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteQueued()
    }

    fun deleteSaved() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteSaved()
    }

    fun deleteProcessing() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteProcessing()
    }

    suspend fun deleteAllWithID(ids: List<Long>) {
        repository.deleteAllWithIDs(ids)
    }

    fun cancelActiveQueued() = viewModelScope.launch(Dispatchers.IO) {
        processingItemsJob?.apply { cancel(CancellationException()) }
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
        return dao.getDownloadsCountByStatus(listOf(DownloadRepository.Status.Active).toListString())
    }

    fun getActiveQueuedDownloadsCount() : Int {
        return dao.getDownloadsCountByStatus(listOf(DownloadRepository.Status.Active, DownloadRepository.Status.Queued).toListString())
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

    suspend fun resetActiveToQueued() {
        dbManager.downloadDao.resetActiveToQueued()
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

    suspend fun queueProcessingDownloads(){
        val processingItems = repository.getProcessingDownloads()
        queueDownloads(processingItems)
    }

    suspend fun queueDownloads(items: List<DownloadItem>, ignoreDuplicates : Boolean = false) {
        val context = App.instance
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

        items.forEach {
            if (it.status != DownloadRepository.Status.Scheduled.toString()) {
                it.status = DownloadRepository.Status.Queued.toString()
            }

            //CHECK DUPLICATES
            var alreadyExists = false
            if (checkDuplicate.isNotEmpty() && !ignoreDuplicates){
                when(checkDuplicate){
                    "download_archive" -> {
                        if (downloadArchive.any { d -> it.url.contains(d) }){
                            alreadyExists = true
                            if (it.id == 0L) {
                                it.status = DownloadRepository.Status.Processing.toString()
                                val id = runBlocking {
                                    repository.insert(it)
                                }
                                it.id = id
                            }
                            existingItemIDs.add(
                                AlreadyExistsIDs(
                                    it.id,
                                    null
                                )
                            )
                        }
                    }
                    "url_type" -> {
                        val existingDownload = activeAndQueuedDownloads.firstOrNull { a -> a.type == it.type && a.url == it.url  }
                        if (existingDownload != null){
                            it.status = DownloadRepository.Status.Processing.toString()
                            val id = runBlocking {
                                repository.insert(it)
                            }
                            it.id = id

                            alreadyExists = true
                            existingItemIDs.add(
                                AlreadyExistsIDs(
                                    it.id,
                                    null
                                )
                            )
                        }else{
                            //check if downloaded and file exists
                            val history = withContext(Dispatchers.IO){
                                historyRepository.getAllByURL(it.url).filter { item -> item.downloadPath.any { path -> FileUtil.exists(path) } }
                            }

                            val existingHistoryItem = history.firstOrNull {
                                    h -> h.type == it.type
                            }

                            if (existingHistoryItem != null){
                                alreadyExists = true
                                it.status = DownloadRepository.Status.Processing.toString()
                                val id = runBlocking {
                                    repository.insert(it)
                                }
                                existingItemIDs.add(
                                    AlreadyExistsIDs(
                                        id,
                                        existingHistoryItem.id
                                    )
                                )
                            }
                        }
                    }
                    "config" -> {
                        val currentCommand = infoUtil.buildYoutubeDLRequest(it)
                        val parsedCurrentCommand = infoUtil.parseYTDLRequestString(currentCommand)
                        val existingDownload = activeAndQueuedDownloads.firstOrNull{d ->
                            d.id = 0
                            d.logID = null
                            d.customFileNameTemplate = it.customFileNameTemplate
                            d.status = DownloadRepository.Status.Queued.toString()
                            d.toString() == it.toString()
                        }

                        if (existingDownload != null){
                            it.status = DownloadRepository.Status.Processing.toString()
                            val id = runBlocking {
                                repository.insert(it)
                            }
                            alreadyExists = true
                            existingItemIDs.add(AlreadyExistsIDs(id, null))
                        }else{
                            //check if downloaded and file exists
                            val history = withContext(Dispatchers.IO){
                                historyRepository.getAllByURL(it.url).filter { item -> item.downloadPath.any { path -> FileUtil.exists(path) } }
                            }

                            val existingHistoryItem = history.firstOrNull {
                                    h -> h.command.replace("(-P \"(.*?)\")|(--trim-filenames \"(.*?)\")".toRegex(), "") == parsedCurrentCommand.replace("(-P \"(.*?)\")|(--trim-filenames \"(.*?)\")".toRegex(), "")
                            }

                            if (existingHistoryItem != null){
                                alreadyExists = true
                                it.status = DownloadRepository.Status.Processing.toString()
                                val id = runBlocking {
                                    repository.insert(it)
                                }
                                existingItemIDs.add(
                                    AlreadyExistsIDs(
                                        id,
                                        existingHistoryItem.id
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (!alreadyExists){
                queuedItems.add(it)
            }


        }

        repository.updateAll(queuedItems)

        //if scheduler is on
        val useScheduler = sharedPreferences.getBoolean("use_scheduler", false)
        if (useScheduler && !alarmScheduler.isDuringTheScheduledTime()){
            if (alarmScheduler.canSchedule()){
                alarmScheduler.schedule()
            }else{
                sharedPreferences.edit().putBoolean("use_scheduler", false).apply()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, context.getString(R.string.enable_alarm_permission), Toast.LENGTH_LONG).show()
                }
            }
        }else{
            if (!sharedPreferences.getBoolean("paused_downloads", false)) {
                repository.startDownloadWorker(queuedItems, context)
            }

            if(!useScheduler){
                CoroutineScope(Dispatchers.IO).launch {
                    queuedItems.filter { it.downloadStartTime != 0L && (it.title.isEmpty() || it.author.isEmpty() || it.thumb.isEmpty()) }.forEach {
                        kotlin.runCatching {
                            resultRepository.updateDownloadItem(it)?.apply {
                                repository.updateWithoutUpsert(this)
                            }
                        }
                    }
                }
            }else{
                CoroutineScope(Dispatchers.IO).launch {
                    queuedItems.filter { it.title.isEmpty() || it.author.isEmpty() || it.thumb.isEmpty() }.forEach {
                        kotlin.runCatching {
                            resultRepository.updateDownloadItem(it)?.apply {
                                repository.updateWithoutUpsert(this)
                            }
                        }
                    }
                }
            }
        }


        if (existingItemIDs.isNotEmpty()){
            alreadyExistsUiState.value = existingItemIDs.toList()
        }
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

    suspend fun moveProcessingToSavedCategory(){
        dao.updateProcessingtoSavedStatus()
    }


    fun updateAllProcessingFormats(formatTuples : List<MultipleItemFormatTuple>) = viewModelScope.launch(Dispatchers.IO) {
        val items = repository.getProcessingDownloads()
        items.forEach {
            val ft = formatTuples.first { ft -> ft.url == it.url }.formatTuple
            ft.format?.apply {
                it.format = this
            }

            if (it.type == Type.video) {
                ft.audioFormats?.map { a -> a.format_id }?.let { list ->
                    it.videoPreferences.audioFormatIDs.clear()
                    it.videoPreferences.audioFormatIDs.addAll(list)
                }
            }

            repository.update(it)
        }

    }

    suspend fun updateProcessingCommandFormat(format: Format){
        val items = repository.getProcessingDownloads()
        items.forEach {
            it.format = format
            repository.update(it)
        }
    }

    suspend fun updateProcessingDownloadPath(path: String){
        dao.updateProcessingDownloadPath(path)
    }

    fun getProcessingDownloads() : List<DownloadItem> {
        return repository.getProcessingDownloads()
    }

    fun updateDownloadItemFormats(id: Long, list: List<Format>) = viewModelScope.launch(Dispatchers.IO) {
        val item = repository.getItemByID(id)
        item.allFormats.clear()
        item.allFormats.addAll(list)
        item.format = getFormat(list, item.type)

        runCatching {
            resultRepository.getAllByURL(item.url).forEach {
                it.formats.clear()
                it.formats.addAll(list)
                resultRepository.update(it)
            }
        }
    }

    fun updateProcessingFormatByUrl(url: String, list: List<Format>) = viewModelScope.launch(Dispatchers.IO) {
        val items = repository.getProcessingDownloadsByUrl(url)
        items.forEach { item ->
            item.allFormats.clear()
            item.allFormats.addAll(list)
            item.format = getFormat(list, item.type)
            repository.update(item)
        }

        kotlin.runCatching {
            resultRepository.getAllByURL(url).forEach {
                it.formats.clear()
                it.formats.addAll(list)
                resultRepository.update(it)
            }
        }
    }

    fun removeUnavailableDownloadAndResultByURL(url: String) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteProcessingByUrl(url)
        resultRepository.deleteByUrl(url)
    }

    suspend fun continueUpdatingFormatsOnBackground(){
        val ids = repository.getProcessingDownloads().map { it.id }
        dao.updateProcessingtoSavedStatus()

        val id = System.currentTimeMillis().toInt()
        val workRequest = OneTimeWorkRequestBuilder<UpdateMultipleDownloadsFormatsWorker>()
            .setInputData(
                Data.Builder()
                    .putLongArray("ids", ids.toLongArray())
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

    suspend fun updateProcessingType(newType: Type) {
        val processing = repository.getProcessingDownloads()
        processing.apply {
            val new = switchDownloadType(this, newType)
            new.forEach {
                repository.update(it)
            }
        }
    }

    suspend fun updateProcessingDownloadTimeAndQueueScheduled(time: Long) {
        val processing = repository.getProcessingDownloads()
        processing.forEach {
            it.downloadStartTime = time
            it.status = DownloadRepository.Status.Scheduled.toString()
        }
        queueDownloads(processing)
    }

    fun checkIfAllProcessingItemsHaveSameType() : Pair<Boolean, Type> {
        val types = dao.getProcessingDownloadTypes()
        if (types.isEmpty()) {
            return Pair(false, Type.command)
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

    suspend fun updateProcessingIncognito(incognito: Boolean) {
        dao.updateProcessingIncognito(incognito)
    }

    fun areAllProcessingIncognito() : Boolean {
        return dao.getProcessingAsIncognitoCount() > 0
    }


}