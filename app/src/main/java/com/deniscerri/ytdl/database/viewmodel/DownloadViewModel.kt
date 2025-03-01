package com.deniscerri.ytdl.database.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Parcelable
import android.util.DisplayMetrics
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player.Command
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map
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
import com.deniscerri.ytdl.database.models.DownloadItemConfigureMultiple
import com.deniscerri.ytdl.database.models.DownloadItemSimple
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.models.VideoPreferences
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.repository.HistoryRepository
import com.deniscerri.ytdl.database.repository.ResultRepository
import com.deniscerri.ytdl.ui.downloadcard.MultipleItemFormatTuple
import com.deniscerri.ytdl.util.Extensions.needsDataUpdating
import com.deniscerri.ytdl.util.Extensions.toListString
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.FormatUtil
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.extractors.YTDLPUtil
import com.deniscerri.ytdl.work.AlarmScheduler
import com.deniscerri.ytdl.work.UpdateMultipleDownloadsDataWorker
import com.deniscerri.ytdl.work.UpdateMultipleDownloadsFormatsWorker
import com.google.gson.Gson
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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
    private val formatUtil = FormatUtil(application)
    private val notificationUtil = NotificationUtil(application)
    private val ytdlpUtil: YTDLPUtil
    private val resources : Resources

    val allDownloads : Flow<PagingData<DownloadItem>>
    val queuedDownloads : Flow<PagingData<DownloadItemSimple>>
    val activeDownloads : Flow<List<DownloadItem>>
    val activePausedDownloads : Flow<List<DownloadItem>>
    val processingDownloads : Flow<List<DownloadItemConfigureMultiple>>
    val cancelledDownloads : Flow<PagingData<DownloadItemSimple>>
    val erroredDownloads : Flow<PagingData<DownloadItemSimple>>
    val savedDownloads : Flow<PagingData<DownloadItemSimple>>
    val scheduledDownloads : Flow<PagingData<DownloadItemSimple>>

    val activeDownloadsCount : Flow<Int>
    val activePausedDownloadsCount : Flow<Int>
    val queuedDownloadsCount : Flow<Int>
    val pausedDownloadsCount : Flow<Int>
    val cancelledDownloadsCount : Flow<Int>
    val erroredDownloadsCount : Flow<Int>
    val savedDownloadsCount : Flow<Int>
    val scheduledDownloadsCount : Flow<Int>

    val pausedAllDownloads = MediatorLiveData(PausedAllDownloadsState.HIDDEN)
    private val pausedAllDownloadsFlow : Flow<PausedAllDownloadsState>
    private var isPausingResuming = false
    enum class PausedAllDownloadsState {
        PAUSE, RESUME, PROCESSING, HIDDEN
    }

    @Parcelize
    data class AlreadyExistsIDs(
        var downloadItemID: Long,
        var historyItemID : Long?
    ) : Parcelable

    val alreadyExistsUiState: MutableStateFlow<List<AlreadyExistsIDs>> = MutableStateFlow(
        mutableListOf()
    )

    private var extraCommandsForAudio: List<CommandTemplate> = listOf()
    private var extraCommandsForVideo: List<CommandTemplate> = listOf()

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
        commandTemplateDao = DBManager.getInstance(application).commandTemplateDao
        repository = DownloadRepository(dao)
        historyRepository = HistoryRepository(dbManager.historyDao)
        resultRepository = ResultRepository(dbManager.resultDao, commandTemplateDao, application)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
        ytdlpUtil = YTDLPUtil(application, commandTemplateDao)

        activeDownloadsCount = repository.activeDownloadsCount
        activePausedDownloadsCount = repository.activePausedDownloadsCount
        queuedDownloadsCount = repository.queuedDownloadsCount
        pausedDownloadsCount = repository.pausedDownloadsCount
        cancelledDownloadsCount = repository.cancelledDownloadsCount
        erroredDownloadsCount = repository.erroredDownloadsCount
        savedDownloadsCount = repository.savedDownloadsCount
        scheduledDownloadsCount = repository.scheduledDownloadsCount

        allDownloads = repository.allDownloads.flow
        queuedDownloads = repository.queuedDownloads.flow
        activeDownloads = repository.activeDownloads
        activePausedDownloads = repository.activePausedDownloads
        processingDownloads = repository.processingDownloads
        savedDownloads = repository.savedDownloads.flow
        scheduledDownloads = repository.scheduledDownloads.flow
        cancelledDownloads = repository.cancelledDownloads.flow
        erroredDownloads = repository.erroredDownloads.flow
        viewModelScope.launch(Dispatchers.IO){
            if (sharedPreferences.getBoolean("use_extra_commands", false)){
                extraCommandsForAudio = commandTemplateDao.getAllTemplatesAsExtraCommandsForAudio()
                extraCommandsForVideo = commandTemplateDao.getAllTemplatesAsExtraCommandsForVideo()
            }
        }

        pausedAllDownloadsFlow = combine(activeDownloadsCount, queuedDownloadsCount, pausedDownloadsCount) { active, queued, paused ->
            if (isPausingResuming) {
                return@combine PausedAllDownloadsState.PROCESSING
            }

            if (active == 0 && queued == 0 && paused == 0) {
                return@combine PausedAllDownloadsState.HIDDEN
            }else if (paused > 1 || (active == 0 && queued > 0) || (paused > 0 && active > 0)) {
                return@combine PausedAllDownloadsState.RESUME
            }else if (active > 1 || (active > 0 && queued > 0)) {
                return@combine PausedAllDownloadsState.PAUSE
            }else{
                return@combine PausedAllDownloadsState.HIDDEN
            }
        }

        pausedAllDownloads.addSource(pausedAllDownloadsFlow.asLiveData()) {
            pausedAllDownloads.value = it
        }

        val confTmp = Configuration(application.resources.configuration)
        val locale = if (Build.VERSION.SDK_INT < 33) {
            sharedPreferences.getString("app_language", "")!!.ifEmpty { Locale.getDefault().language }
        }else{
            Locale.getDefault().language
        }.run {
            split("-")
        }.run {
            if (this.size == 1) Locale(this[0]) else Locale(this[0], this[1])
        }
        confTmp.setLocale(locale)
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

    suspend fun putToSaved(item: DownloadItem) {
        item.status = DownloadRepository.Status.Saved.toString()
        val id = repository.update(item)
        if (item.needsDataUpdating()) {
            continueUpdatingDataInBackground(listOf(id))
        }
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
        val recodeVideo = sharedPreferences.getBoolean("recode_video", false)
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
        val subsLanguages = sharedPreferences.getString("subs_lang", "en.*,.*-orig")!!

        val videoPreferences = VideoPreferences(
            embedSubs,
            addChapters, false,
            ArrayList(sponsorblock),
            saveSubs,
            saveAutoSubs,
            subsLanguages,
            audioFormatIDs = preferredAudioFormats,
            recodeVideo = recodeVideo
        )

        val extraCommands = when(type){
            Type.audio -> extraCommandsForAudio
            Type.video -> extraCommandsForVideo
            else -> listOf()
        }.filter {
            it.urlRegex.isEmpty() || it.urlRegex.any { u ->
                Regex(u).containsMatchIn(resultItem.url)
            }
        }.joinToString(" ") { it.content }

        return DownloadItem(0,
            resultItem.url,
            resultItem.title,
            resultItem.author,
            resultItem.thumb,
            resultItem.duration,
            type,
            getFormat(resultItem.formats, type, resultItem.url),
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
            val format = getFormat(it.allFormats, type, it.url)
            it.format = format

            var updatedDownloadPath = ""
            var container = ""

            when(type){
                Type.audio -> {
                    updatedDownloadPath = sharedPreferences.getString("music_path", FileUtil.getDefaultAudioPath())!!
                    container = sharedPreferences.getString("audio_format", "")!!
                }
                Type.video -> {
                    updatedDownloadPath = sharedPreferences.getString("video_path", FileUtil.getDefaultVideoPath())!!
                    container = sharedPreferences.getString("video_format", "")!!
                }
                Type.command -> {
                    updatedDownloadPath = sharedPreferences.getString("command_path", FileUtil.getDefaultCommandPath())!!
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
        val recodeVideo = sharedPreferences.getBoolean("recode_video", false)
        val addChapters = sharedPreferences.getBoolean("add_chapters", false)
        val saveThumb = sharedPreferences.getBoolean("write_thumbnail", false)
        val embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false)
        val cropThumb = sharedPreferences.getBoolean("crop_thumbnail", false)
        val subsLanguages = sharedPreferences.getString("subs_lang", "en.*,.*-orig")!!

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

        val extraCommands = when (historyItem.type) {
            Type.audio -> extraCommandsForAudio
            Type.video -> extraCommandsForVideo
            else -> listOf()
        }.filter {
            it.urlRegex.isEmpty() || it.urlRegex.any { u ->
                Regex(u).containsMatchIn(historyItem.url)
            }
        }.joinToString(" ") { it.content }

        val audioPreferences = AudioPreferences(embedThumb, cropThumb,false, ArrayList(sponsorblock!!))
        val videoPreferences = VideoPreferences(embedSubs, addChapters, false, ArrayList(sponsorblock), saveSubs, saveAutoSubs, subsLanguages, recodeVideo = recodeVideo)
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


    fun getFormat(formats: List<Format>, type: Type, url: String? = null) : Format {
        when(type) {
            Type.audio -> {
                return cloneFormat (
                    try {
                        val theFormats = formats.filter { it.vcodec.isBlank() || it.vcodec == "none" }
                        FormatUtil(application).sortAudioFormats(theFormats).first()
                    }catch (e: Exception){
                        formatUtil.getGenericAudioFormats(resources).first()
                    }
                )

            }
            Type.video -> {
                return cloneFormat(
                    try {
                        val theFormats = formats.filter { it.vcodec.isNotBlank() && it.vcodec != "none" }.ifEmpty {
                            formatUtil.getGenericVideoFormats(resources).sortedByDescending { it.filesize }
                        }

                        FormatUtil(application).sortVideoFormats(theFormats).first()
                    }catch (e: Exception){
                        formatUtil.getGenericVideoFormats(resources).first()
                    }
                )
            }
            else -> {
                val preferredCommandTemplates = commandTemplateDao.getPreferredCommandTemplates()
                var template : CommandTemplate? = null
                if (url != null) {
                    template = preferredCommandTemplates.firstOrNull { it.urlRegex.isEmpty() || it.urlRegex.any { u ->
                        Regex(u).containsMatchIn(url)
                    } }
                }

                if (template == null) {
                    template = commandTemplateDao.getFirst()
                }
                return generateCommandFormat(
                    template ?: CommandTemplate(
                        0,
                        "",
                        sharedPreferences.getString("lastCommandTemplateUsed", "") ?: "",
                        useAsExtraCommand = false,
                        useAsExtraCommandAudio = false,
                        useAsExtraCommandVideo = false,
                        useAsExtraCommandDataFetching = false
                    )
                )
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
            if (!formatUtil.getGenericAudioFormats(resources).contains(audioF)){
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

    fun turnHistoryItemsToProcessingDownloads(itemIDs: List<Long>, downloadNow: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        val job = viewModelScope.launch(Dispatchers.IO) {
            repository.deleteProcessing()
            processingItems.emit(true)
            try {
                val toInsert = mutableListOf<DownloadItem>()
                itemIDs.forEach {
                    val item = historyRepository.getItem(it)
                    val downloadItem = createDownloadItemFromHistory(item)
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
                toInsert.chunked(500).forEach { chunked ->
                    repository.insertAll(chunked)
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
                toInsert.chunked(500).forEach { chunked ->
                    repository.insertAll(chunked)
                }
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

    fun deleteWithDuplicateStatus() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteWithDuplicateStatus()
    }

    suspend fun deleteAllWithID(ids: List<Long>) {
        repository.deleteAllWithIDs(ids)
    }

    private fun cancelActiveQueued() = viewModelScope.launch(Dispatchers.IO) {
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
        return repository.getSavedDownloads()
    }

    fun getActiveDownloads() : List<DownloadItem>{
        return repository.getActiveDownloads()
    }

    fun getActiveDownloadsCount() : Int {
        return repository.getActiveDownloadsCount()
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

    suspend fun resetScheduleItemForAllScheduledItemsAndStartDownload() = CoroutineScope(Dispatchers.IO).launch {
        dbManager.downloadDao.resetScheduleTimeForAllScheduledItems()
        repository.startDownloadWorker(emptyList(), application)
    }

    suspend fun putAtTopOfQueue(ids: List<Long>) = CoroutineScope(Dispatchers.IO).launch{
        val downloads = dao.getQueuedDownloadsListIDs()
        val lastID = ids.maxOf { it }
        ids.forEach { dao.updateDownloadID(it, -it) }
        val newIDs = downloads.take(ids.size)

        //other ids that need to move around
        val takenPositions = mutableListOf<Long>()
        downloads.filter { !ids.contains(it) && it < lastID }.toMutableList().apply {
            this.reverse()
            this.forEach { dID ->
                val newID = downloads.last { !newIDs.contains(it) && !takenPositions.contains(it) && it <= lastID }
                takenPositions.add(newID)
                dao.updateDownloadID(dID, newID)
            }
        }
        ids.forEachIndexed { idx, it ->
            dao.updateDownloadID(-it, newIDs[idx])
        }
    }


    suspend fun putAtBottomOfQueue(ids: List<Long>) = CoroutineScope(Dispatchers.IO).launch{
        val downloads = dao.getQueuedDownloadsListIDs()
        ids.forEach { dao.updateDownloadID(it, -it)}
        val newIDs = downloads.takeLast(ids.size)

        //other ids that need to move around
        val takenPositions = mutableListOf<Long>()
        for (dID in downloads.filter { !ids.contains(it) }){
            val newID = downloads.first { !newIDs.contains(it) && !takenPositions.contains(it) }
            takenPositions.add(newID)
            dao.updateDownloadID(dID, newID)
        }

        ids.toMutableList().apply {
            this.reverse()
            this.forEachIndexed { idx, it ->
                dao.updateDownloadID(-it, newIDs[idx])
            }
        }
    }


    fun putAtPosition(current: Long, id: Long) = CoroutineScope(Dispatchers.IO).launch {
        val downloads = dao.getQueuedDownloadsListIDs()
        dao.updateDownloadID(current, -current)

        if (current > id){
            downloads.filter { it in id until current }.toMutableList().apply {
                this.reverse()
                this.forEach { dID ->
                    val index = downloads.indexOf(dID)
                    dao.updateDownloadID(dID, downloads[index + 1])
                }
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

    suspend fun queueProcessingDownloads() : QueueDownloadsResult {
        val processingItems = repository.getProcessingDownloads()
        return queueDownloads(processingItems)
    }

    data class QueueDownloadsResult(
        var message: String,
        var duplicateDownloadIDs : List<AlreadyExistsIDs>
    )

    suspend fun queueDownloads(items: List<DownloadItem>, ignoreDuplicates : Boolean = false) : QueueDownloadsResult {
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
            if (it.downloadStartTime > 0) {
                it.status = DownloadRepository.Status.Scheduled.toString()
            }else {
                it.status = DownloadRepository.Status.Queued.toString()
            }

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
                        val currentCommand = ytdlpUtil.buildYoutubeDLRequest(it)
                        val parsedCurrentCommand = ytdlpUtil.parseYTDLRequestString(currentCommand)
                        val existingDownload = activeAndQueuedDownloads.firstOrNull{d ->
                            d.id = 0
                            d.logID = null
                            d.customFileNameTemplate = it.customFileNameTemplate
                            d.status = DownloadRepository.Status.Queued.toString()
                            d.toString() == it.toString()
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

        val result = QueueDownloadsResult("", listOf())

        //if scheduler is on
        val useScheduler = sharedPreferences.getBoolean("use_scheduler", false)
        if (useScheduler && !alarmScheduler.isDuringTheScheduledTime()){
            if (alarmScheduler.canSchedule()){
                repository.updateAll(queuedItems)
                alarmScheduler.schedule()
            }else{
                sharedPreferences.edit().putBoolean("use_scheduler", false).apply()
                result.message = context.getString(R.string.enable_alarm_permission)
            }
        }else{
            val queued = repository.updateAll(queuedItems)
            println(queued.size)

            result.message = repository.startDownloadWorker(queued, context).getOrElse { "" }

            val ids = queued.filter { it.needsDataUpdating() }.map { it.id }
            continueUpdatingDataInBackground(ids)
        }


        if (existingItemIDs.isNotEmpty()){
            alreadyExistsUiState.value = existingItemIDs.toList()
            result.duplicateDownloadIDs = existingItemIDs.toList()
        }

        return result
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

    suspend fun updateProcessingContainer(cont: String) {
        var container = ""
        if (cont != resources.getString(R.string.defaultValue)) {
            container = cont
        }
        dao.updateProcessingContainer(container)
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
        item.format = getFormat(list, item.type, item.url)

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
            item.format = getFormat(list, item.type, item.url)
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

    private fun continueUpdatingDataInBackground(ids: List<Long>){
        val id = System.currentTimeMillis().toInt()
        val workRequest = OneTimeWorkRequestBuilder<UpdateMultipleDownloadsDataWorker>()
            .setInputData(
                Data.Builder()
                    .putLongArray("ids", ids.toLongArray())
                    .putInt("id", id)
                    .build())
            .addTag("updateData")
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

    suspend fun updateProcessingDownloadTimeAndQueueScheduled(time: Long) : QueueDownloadsResult {
        val processing = repository.getProcessingDownloads()
        processing.forEach {
            it.downloadStartTime = time
            it.status = DownloadRepository.Status.Scheduled.toString()
        }
        return queueDownloads(processing)
    }

    fun checkIfAllProcessingItemsHaveSameType() : Pair<Boolean, Type> {
        val types = dao.getProcessingDownloadTypes()
        if (types.isEmpty()) {
            return Pair(false, Type.command)
        }

        return Pair(types.size == 1, Type.valueOf(types.first()))
    }

    fun checkIfAllProcessingItemsHaveSameContainer() : Pair<Boolean, String> {
        val containers = dao.getProcessingDownloadContainers()
        return Pair(containers.size == 1, containers.first())
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

    fun getIDsByStatus(list: List<DownloadRepository.Status>) : List<Long> {
        return dao.getIDsByStatus(list.map { it.toString() })
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


    fun pauseAllDownloads() = viewModelScope.launch {
        pausedAllDownloads.value = PausedAllDownloadsState.PROCESSING
        isPausingResuming = true
        WorkManager.getInstance(application).cancelAllWorkByTag("download")
        val activeDownloadsList = withContext(Dispatchers.IO){
            getActiveDownloads()
        }
        activeDownloadsList.forEach {
            YoutubeDL.getInstance().destroyProcessById(it.id.toString())
            notificationUtil.cancelDownloadNotification(it.id.toInt())
        }
        delay(1000)
        isPausingResuming = false
        repository.setDownloadStatusMultiple(activeDownloadsList.map { it.id }, DownloadRepository.Status.Paused)
        pausedAllDownloads.value = PausedAllDownloadsState.RESUME
    }

    fun resumeAllDownloads() = viewModelScope.launch {
        pausedAllDownloads.value = PausedAllDownloadsState.PROCESSING
        isPausingResuming = true
        WorkManager.getInstance(application).cancelAllWorkByTag("download")
        val paused = withContext(Dispatchers.IO) {
            dao.getPausedDownloadsList()
        }

        withContext(Dispatchers.IO){
            dbManager.downloadDao.resetPausedToQueued()
            repository.startDownloadWorker(paused, application)
        }
        delay(1000)
        isPausingResuming = false
        withContext(Dispatchers.Main) {
            pausedAllDownloads.value = PausedAllDownloadsState.PAUSE
        }
    }

    fun deleteAll() = viewModelScope.launch {
        cancelAllDownloadsImpl()
        repository.deleteAll()
    }

    fun cancelAllDownloads() = viewModelScope.launch {
        cancelAllDownloadsImpl()
    }

    private suspend fun cancelAllDownloadsImpl() {
        WorkManager.getInstance(application).cancelAllWorkByTag("download")
        val activeAndQueued = withContext(Dispatchers.IO){
            repository.getActiveAndQueuedDownloadIDs()
        }
        activeAndQueued.forEach { id ->
            YoutubeDL.getInstance().destroyProcessById(id.toString())
            notificationUtil.cancelDownloadNotification(id.toInt())
        }
        cancelActiveQueued()
    }

    fun resumeDownload(itemID: Long) = viewModelScope.launch {
        kotlin.runCatching {
            val item = withContext(Dispatchers.IO){
                repository.getItemByID(itemID)
            }
            item.status = DownloadRepository.Status.Queued.toString()
            withContext(Dispatchers.IO){
                updateDownload(item)
            }
            putAtTopOfQueue(listOf(itemID))
            withContext(Dispatchers.IO){
                repository.startDownloadWorker(listOf(item), application, false)
            }
        }
    }
}