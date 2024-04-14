package com.deniscerri.ytdl.database.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import android.util.Log
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
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
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
import com.deniscerri.ytdl.work.AlarmScheduler
import com.deniscerri.ytdl.work.UpdatePlaylistFormatsWorker
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale


class DownloadViewModel(private val application: Application) : AndroidViewModel(application) {
    private val dbManager: DBManager
    val repository : DownloadRepository
    private val sharedPreferences: SharedPreferences
    private val commandTemplateDao: CommandTemplateDao
    private val infoUtil : InfoUtil
    val allDownloads : Flow<PagingData<DownloadItem>>
    val queuedDownloads : Flow<PagingData<DownloadItemSimple>>
    val activeDownloads : Flow<List<DownloadItem>>
    val processingDownloads : Flow<List<DownloadItem>>
    val cancelledDownloads : Flow<PagingData<DownloadItemSimple>>
    val erroredDownloads : Flow<PagingData<DownloadItemSimple>>
    val savedDownloads : Flow<PagingData<DownloadItemSimple>>
    val scheduledDownloads : Flow<PagingData<DownloadItemSimple>>

    val activeDownloadsCount : Flow<Int>
    val activeAndActivePausedDownloadsCount : Flow<Int>
    val queuedDownloadsCount : Flow<Int>
    val activeQueuedDownloadsCount : Flow<Int>
    val cancelledDownloadsCount : Flow<Int>
    val erroredDownloadsCount : Flow<Int>
    val savedDownloadsCount : Flow<Int>
    val scheduledDownloadsCount : Flow<Int>
    val pausedDownloadsCount: Flow<Int>

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

    data class AlreadyExistsUIState(
        var historyItems: MutableList<Long>,
        var downloadItems : MutableList<Long>
    )

    val alreadyExistsUiState: MutableStateFlow<AlreadyExistsUIState> = MutableStateFlow(AlreadyExistsUIState(
        historyItems = mutableListOf(),
        downloadItems = mutableListOf()
    ))

    enum class Type {
        auto, audio, video, command
    }

    private val urlsForAudioType = listOf(
        "music",
        "audio",
        "soundcloud"
    )

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
        activeAndActivePausedDownloadsCount = repository.activeAndActivePausedDownloadsCount
        queuedDownloadsCount = repository.queuedDownloadsCount
        activeQueuedDownloadsCount = repository.activeQueuedDownloadsCount
        cancelledDownloadsCount = repository.cancelledDownloadsCount
        erroredDownloadsCount = repository.erroredDownloadsCount
        savedDownloadsCount = repository.savedDownloadsCount
        scheduledDownloadsCount = repository.scheduledDownloadsCount
        pausedDownloadsCount = repository.pausedDownloadsCount

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
            infoUtil.getGenericAudioFormats(resources).last()
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
            Type.audio -> sharedPreferences.getString("file_name_template_audio", "%(uploader)s - %(title)s")
            Type.video -> sharedPreferences.getString("file_name_template", "%(uploader)s - %(title)s")
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
            resultItem.playlistTitle,
            audioPreferences,
            videoPreferences,
            extraCommands,
            customFileNameTemplate!!,
            saveThumb,
            DownloadRepository.Status.Queued.toString(), 0, null, playlistURL = resultItem.playlistURL, playlistIndex = resultItem.playlistIndex
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
            Type.audio -> sharedPreferences.getString("file_name_template_audio", "%(uploader)s - %(title)s")
            Type.video -> sharedPreferences.getString("file_name_template", "%(uploader)s - %(title)s")
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
        val requirements: MutableList<(Format) -> Int> = mutableListOf()
        requirements.add {it: Format -> if (audioFormatIDPreference.contains(it.format_id)) 10 else 0}

        sharedPreferences.getString("audio_language", "")?.apply {
            if (this.isNotBlank()){
                requirements.add { it: Format -> if (it.lang?.contains(this) == true) 3 else 0 }
            }
        }

        requirements.add {it: Format -> if ("^(${audioCodec}).+$".toRegex(RegexOption.IGNORE_CASE).matches(it.acodec)) 2 else 0}
        requirements.add {it: Format -> if (it.container == audioContainer) 1 else 0 }
        return requirements
    }

    //requirement and importance
    @SuppressLint("RestrictedApi")
    fun getPreferredVideoRequirements(): MutableList<(Format) -> Int> {
        val requirements: MutableList<(Format) -> Int> = mutableListOf()
        //format id
        requirements.add { it: Format -> if (formatIDPreference.contains(it.format_id)) 20 else 0 }
        //resolutions
        application.getStringArray(R.array.video_formats_values)
            .filter { it.contains("_") }
            .map{ it.split("_")[0].dropLast(1)
        }.toMutableList().apply {
            when(videoQualityPreference) {
                "worst" -> {
                    requirements.add { it: Format -> if (it.format_note.contains("worst", ignoreCase = true)) (15) else 0 }
                }
                "best" -> {
                    requirements.add { it: Format -> if (it.format_note.contains("best", ignoreCase = true)) (15) else 0 }
                }
                else -> {
                    val preferenceIndex = this.indexOfFirst { videoQualityPreference.contains(it) }
                    val preference = this[preferenceIndex]
                    for(i in 0..preferenceIndex){
                        removeAt(0)
                    }
                    add(0, preference)
                    forEachIndexed { index, res ->
                        requirements.add { it: Format -> if (it.format_note.contains(res, ignoreCase = true)) (15 - index - 1) else 0 }
                    }
                }
            }

        }
        requirements.add { it: Format -> if ("^(${videoCodec})(.+)?$".toRegex(RegexOption.IGNORE_CASE).matches(it.vcodec)) 5 else 0 }
        requirements.add { it: Format -> if (it.acodec == "none" || it.acodec == "") 1 else 0 }
        requirements.add { it: Format ->
            if (videoContainer == "mp4")
                if (it.container.equals("mpeg_4", true)) 1 else 0
            else
                if (it.container.equals(videoContainer, true)) 1 else 0
        }
        return  requirements
    }

    fun getFormat(formats: List<Format>, type: Type) : Format {
        when(type) {
            Type.audio -> {
                return cloneFormat (
                    try {
                        val theFormats = formats.filter { it.vcodec.isBlank() || it.vcodec == "none" }
                        val requirements = getPreferredAudioRequirements()
                        theFormats.maxByOrNull { f -> requirements.sumOf{ req -> req(f)} } ?: throw Exception()
                    }catch (e: Exception){
                        bestAudioFormat
                    }
                )

            }
            Type.video -> {
                return cloneFormat(
                    try {
                        val theFormats = formats.filter { it.vcodec.isNotBlank() && it.vcodec != "none" }.ifEmpty {
                            defaultVideoFormats.sortedByDescending { it.filesize }
                        }
                        when (videoQualityPreference) {
                            "worst" -> {
                                theFormats.last()
                            }
                            else /*best*/ -> {
                                val requirements = getPreferredVideoRequirements()
                                theFormats.run {
                                    if (sharedPreferences.getBoolean("prefer_smaller_formats", false)){
                                        sortedBy { it.filesize }.maxByOrNull { f -> requirements.sumOf { req -> req(f) } } ?: throw Exception()
                                    }else{
                                        sortedByDescending { it.filesize }.maxByOrNull { f ->
                                            val summ = requirements.sumOf { req -> req(f) }
                                            summ
                                        } ?: throw Exception()
                                    }
                                }
                            }
                        }
                    }catch (e: Exception){
                        bestVideoFormat
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

    fun getPreferredAudioFormats(formats: List<Format>) : ArrayList<String>{
        val preferredAudioFormats = arrayListOf<String>()
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



    fun getLatestCommandTemplateAsFormat() : Format {
        val t = commandTemplateDao.getFirst()!!
        return Format(t.title, "", "", "", "", 0, t.content)
    }

    fun turnResultItemsToDownloadItems(items: List<ResultItem?>) : List<DownloadItem> {
        val list : MutableList<DownloadItem> = mutableListOf()
        items.forEach {
            val preferredType = getDownloadType(url = it!!.url).toString()
            list.add(createDownloadItemFromResult(result = it, givenType = Type.valueOf(
                preferredType
            )))
        }
        return list
    }

    fun insert(item: DownloadItem) = viewModelScope.launch(Dispatchers.IO){
        repository.insert(item)
    }

    fun pauseDownloads() = viewModelScope.launch(Dispatchers.IO){
        repository.pauseDownloads()
    }

    fun unPauseDownloads() = viewModelScope.launch(Dispatchers.IO){
        repository.unPauseDownloads()
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
        repository.cancelActiveQueued()
    }

    fun getQueued() : List<DownloadItem> {
        return repository.getQueuedDownloads()
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

    private fun cloneFormat(item: Format) : Format {
        val string = Gson().toJson(item, Format::class.java)
        return Gson().fromJson(string, Format::class.java)
    }

    fun getActiveDownloads() : List<DownloadItem>{
        return repository.getActiveDownloads()
    }

    fun getActiveAndQueuedDownloads() : List<DownloadItem>{
        return repository.getActiveAndQueuedDownloads()
    }

    fun getActiveAndQueuedDownloadIDs() : List<Long>{
        return repository.getActiveAndQueuedDownloadIDs()
    }

    suspend fun resetScheduleTimeForItemsAndStartDownload(items: List<Long>) = CoroutineScope(Dispatchers.IO).launch {
        dbManager.downloadDao.resetScheduleTimeForItems(items)
        repository.startDownloadWorker(emptyList(), application)
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

    suspend fun reQueueDownloadItems(items: List<Long>) = CoroutineScope(Dispatchers.IO).launch {
        dbManager.downloadDao.reQueueDownloadItems(items)
        repository.startDownloadWorker(emptyList(), application)
    }

    suspend fun queueDownloads(items: List<DownloadItem>, ign : Boolean = false) : Pair<List<Long>, List<Long>> {
        val context = App.instance
        val alarmScheduler = AlarmScheduler(context)
        val activeAndQueuedDownloads = withContext(Dispatchers.IO){
            repository.getActiveAndQueuedDownloads()
        }
        val queuedItems = mutableListOf<DownloadItem>()
        val existingDownloads = mutableListOf<Long>()
        val existingHistory = mutableListOf<Long>()

        //if scheduler is on
        val useScheduler = sharedPreferences.getBoolean("use_scheduler", false)
        if (useScheduler && !alarmScheduler.isDuringTheScheduledTime()){
            alarmScheduler.schedule()
        }

        if (items.any { it.playlistTitle.isEmpty() } && items.size > 1){
            items.forEachIndexed { index, it -> it.playlistTitle = "Various[${index+1}]" }
        }

        val downloadArchive = runCatching { File(FileUtil.getDownloadArchivePath(application)).useLines { it.toList() } }.getOrElse { listOf() }
            .map { it.split(" ")[1] }
        items.forEach {
            if (! listOf(DownloadRepository.Status.ActivePaused, DownloadRepository.Status.Scheduled).toListString().contains(it.status))
                it.status = DownloadRepository.Status.Queued.toString()
            var alreadyExists = false

            val checkDuplicate = sharedPreferences.getString("prevent_duplicate_downloads", "")!!
            if (checkDuplicate.isNotEmpty() && !ign){
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
                            existingDownloads.add(it.id)
                        }
                    }
                    "url_type" -> {
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
                            existingDownloads.add(id)
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
                                existingHistory.add(existingHistoryItem.id)
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
                            existingDownloads.add(id)
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
                                existingHistory.add(existingHistoryItem.id)
                            }
                        }
                    }
                }
            }

            if (!alreadyExists){
                if (it.id == 0L){
                    val id = runBlocking {
                        repository.insert(it)
                    }
                    it.id = id
                }else if (listOf(DownloadRepository.Status.Queued, DownloadRepository.Status.Scheduled).toListString().contains(it.status)){
                    withContext(Dispatchers.IO){
                        repository.update(it)
                    }
                }

                queuedItems.add(it)
            }

        }

        if (existingDownloads.isNotEmpty() || existingHistory.isNotEmpty()){
            alreadyExistsUiState.update { u -> u.copy(existingHistory, existingDownloads) }
        }

        if (queuedItems.isNotEmpty()){
            if (!useScheduler || alarmScheduler.isDuringTheScheduledTime()){
                repository.startDownloadWorker(queuedItems, context)

                if(!useScheduler){
                    queuedItems.filter { it.downloadStartTime != 0L && (it.title.isEmpty() || it.author.isEmpty() || it.thumb.isEmpty()) }.forEach {
                        CoroutineScope(Dispatchers.IO).launch {
                            runCatching {
                                resultRepository.updateDownloadItem(it)?.apply {
                                    repository.updateWithoutUpsert(this)
                                }
                            }
                        }
                    }
                }else{
                    queuedItems.filter { it.title.isEmpty() || it.author.isEmpty() || it.thumb.isEmpty() }.forEach {
                        CoroutineScope(Dispatchers.IO).launch {
                            runCatching {
                                resultRepository.updateDownloadItem(it)?.apply {
                                    repository.updateWithoutUpsert(this)
                                }
                            }
                        }
                    }
                }
            }
        }

        return Pair(existingDownloads, existingHistory)
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

    fun moveProcessingToSavedCategory(){
        dao.updateProcessingtoSavedStatus()
    }

    suspend fun downloadProcessingDownloads(timeInMillis: Long = 0){
        repository.getProcessingDownloads().apply {
            if (timeInMillis > 0){
                this.forEach {
                    it.status = DownloadRepository.Status.Scheduled.toString()
                    it.downloadStartTime = timeInMillis
                }
            }

            queueDownloads(this)
        }
    }

    suspend fun updateProcessingFormat(selectedFormats: List<FormatTuple>): List<Long> {
        val items = repository.getProcessingDownloads()
        items.forEachIndexed { index, i ->
            selectedFormats[index].format?.apply {
                i.format = this
            }
            if (i.type == Type.video) selectedFormats[index].audioFormats?.map { it.format_id }?.let { i.videoPreferences.audioFormatIDs.addAll(it) }
            repository.update(i)
        }

        return items.map { it.format.filesize }
    }

    suspend fun updateProcessingCommandFormat(format: Format){
        val items = repository.getProcessingDownloads()
        items.forEach { i ->
            i.format = format
            repository.update(i)
        }
    }

    suspend fun updateProcessingDownloadPath(path: String){
        val items = repository.getProcessingDownloads()
        items.forEach { i ->
            i.downloadPath = path
            repository.update(i)
        }
    }

    fun getProcessingDownloadsCount() : Int {
        return dao.getDownloadsCountByStatus(listOf(DownloadRepository.Status.Processing.toString()))
    }

    fun getProcessingDownloads() : List<DownloadItem> {
        return repository.getProcessingDownloads()
    }


    suspend fun updateProcessingAllFormats(formatCollection: List<List<Format>>) {
        val items = repository.getProcessingDownloads()
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

    suspend fun continueUpdatingFormatsOnBackground(){
        dao.getProcessingDownloadsList().apply {
            this.forEach {
                it.status = DownloadRepository.Status.Saved.toString()
                repository.update(it)
            }

            val ids = this.map { it.id }
            val id = System.currentTimeMillis().toInt()
            val workRequest = OneTimeWorkRequestBuilder<UpdatePlaylistFormatsWorker>()
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
    }

    suspend fun updateProcessingType(newType: Type) {
        repository.getProcessingDownloads().apply {
            val new = switchDownloadType(this, newType)
            new.forEach {
                repository.update(it)
            }
        }
    }

    fun checkIfAllProcessingItemsHaveSameType() : Pair<Boolean, Type> {
        val counts = dao.getProcessingDownloadsCountByType()
        val sameType = counts.size == 1 || counts[0] == counts[1]
        val first = dao.getFirstProcessingDownload()
        return Pair(sameType, first.type)
    }


    suspend fun updateItemsWithIdsToProcessingStatus(ids: List<Long>) {
        repository.deleteProcessing()
        dao.updateItemsToProcessing(ids)
        val first = dao.getFirstProcessingDownload()
    }

    suspend fun addDownloadsToProcessing(ids: List<Long>) {
        repository.deleteProcessing()
        val items = repository.getAllItemsByIDs(ids)
        items.forEach {
            it.id = 0
            it.status = DownloadRepository.Status.Processing.toString()
            insert(it)
        }
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