package com.deniscerri.ytdlnis.database.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.net.ConnectivityManager
import android.os.Looper
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.deniscerri.ytdlnis.App
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.dao.CommandTemplateDao
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.dao.ResultDao
import com.deniscerri.ytdlnis.database.models.AudioPreferences
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.DownloadItemSimple
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.models.VideoPreferences
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.repository.HistoryRepository
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.work.AlarmScheduler
import com.deniscerri.ytdlnis.work.DownloadWorker
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
import java.util.concurrent.TimeUnit


class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val dbManager: DBManager
    val repository : DownloadRepository
    private val sharedPreferences: SharedPreferences
    private val commandTemplateDao: CommandTemplateDao
    private val infoUtil : InfoUtil
    val allDownloads : Flow<PagingData<DownloadItem>>
    val queuedDownloads : Flow<PagingData<DownloadItemSimple>>
    val activeDownloads : LiveData<List<DownloadItem>>
    val activeDownloadsCount : LiveData<Int>
    val cancelledDownloads : Flow<PagingData<DownloadItemSimple>>
    val erroredDownloads : Flow<PagingData<DownloadItemSimple>>
    val savedDownloads : Flow<PagingData<DownloadItemSimple>>

    private var bestVideoFormat : Format
    private var bestAudioFormat : Format
    private var defaultVideoFormats : MutableList<Format>

    private val videoQualityPreference: String
    private val formatIDPreference: List<String>
    private val audioFormatIDPreference: List<String>
    private val resources : Resources
    private var extraCommands: String = ""

    private var audioContainer: String?
    private var videoContainer: String?
    private var videoCodec: String?
    private var audioCodec: String?
    private val dao: DownloadDao
    private val historyRepository: HistoryRepository

    data class DownloadsUiState(
        var errorMessage: Pair<Int, Int>?,
        //action title, type, data
        var actions: MutableList<Triple<Int, DownloadsAction, List<DownloadItem>?>>?
    )

    enum class DownloadsAction {
       DOWNLOAD_ANYWAY
    }

    val uiState: MutableStateFlow<DownloadsUiState> = MutableStateFlow(DownloadsUiState(
        errorMessage = null,
        actions = null
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
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
        commandTemplateDao = DBManager.getInstance(application).commandTemplateDao
        infoUtil = InfoUtil(application)

        allDownloads = repository.allDownloads.flow
        queuedDownloads = repository.queuedDownloads.flow
        activeDownloads = repository.activeDownloads.asLiveData()
        activeDownloadsCount = repository.activeDownloadsCount.asLiveData()
        savedDownloads = repository.savedDownloads.flow
        cancelledDownloads = repository.cancelledDownloads.flow
        erroredDownloads = repository.erroredDownloads.flow
        viewModelScope.launch(Dispatchers.IO){
            if (sharedPreferences.getBoolean("use_extra_commands", false)){
                extraCommands = commandTemplateDao.getAllTemplatesAsExtraCommands().joinToString(" ")
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
            Format(
                "best",
                audioContainer!!,
                "",
                "",
                "",
                0,
                "best"
            )
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
            }
        }else{
            repository.update(item)
        }
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
        val addChapters = sharedPreferences.getBoolean("add_chapters", false)
        val saveThumb = sharedPreferences.getBoolean("write_thumbnail", false)
        val embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false)

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

        val audioPreferences = AudioPreferences(embedThumb, false, ArrayList(sponsorblock!!))

        val preferredAudioFormats = arrayListOf<String>()
        for (f in resultItem.formats.sortedBy { it.format_id }){
            val fId = audioFormatIDPreference.sorted().find { it.contains(f.format_id) }
            if (fId != null) {
                if (fId.split("+").all { resultItem.formats.map { f-> f.format_id }.contains(it) }){
                    preferredAudioFormats.addAll(fId.split("+"))
                    break
                }
            }
        }

        if (preferredAudioFormats.isEmpty()){
            val audioF = getFormat(resultItem.formats, Type.audio)
            if (!infoUtil.getGenericAudioFormats(resources).contains(audioF)){
                preferredAudioFormats.add(audioF.format_id)
            }
        }

        val videoPreferences = VideoPreferences(
            embedSubs,
            addChapters, false,
            ArrayList(sponsorblock),
            saveSubs,
            audioFormatIDs = preferredAudioFormats
        )

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
            downloadPath!!, resultItem.website, "", resultItem.playlistTitle, audioPreferences, videoPreferences, extraCommands, customFileNameTemplate!!, saveThumb, DownloadRepository.Status.Queued.toString(), 0, null
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
            System.currentTimeMillis()
        )

    }

    fun switchDownloadType(list: List<DownloadItem>, type: Type) : List<DownloadItem>{
        val updatedDownloadPath : String = when(type){
            Type.audio -> sharedPreferences.getString("music_path", FileUtil.getDefaultAudioPath())!!
            Type.video -> sharedPreferences.getString("video_path", FileUtil.getDefaultVideoPath())!!
            Type.command -> sharedPreferences.getString("command_path", FileUtil.getDefaultCommandPath())!!
            else -> ""
        }

        list.forEach {
            val format = getFormat(it.allFormats, type)
            it.format = format
            val currentDownloadPath = when(it.type){
                Type.audio -> sharedPreferences.getString("music_path", FileUtil.getDefaultAudioPath())
                Type.video -> sharedPreferences.getString("video_path", FileUtil.getDefaultVideoPath())
                Type.command -> sharedPreferences.getString("command_path", FileUtil.getDefaultCommandPath())
                else -> ""
            }
            if (it.downloadPath == currentDownloadPath) it.downloadPath = updatedDownloadPath

            it.type = type
        }
        return list
    }

    fun createDownloadItemFromHistory(historyItem: HistoryItem) : DownloadItem {
        val embedSubs = sharedPreferences.getBoolean("embed_subtitles", false)
        val saveSubs = sharedPreferences.getBoolean("write_subtitles", false)
        val addChapters = sharedPreferences.getBoolean("add_chapters", false)
        val saveThumb = sharedPreferences.getBoolean("write_thumbnail", false)
        val embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false)
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

        val audioPreferences = AudioPreferences(embedThumb, false, ArrayList(sponsorblock!!))
        val videoPreferences = VideoPreferences(embedSubs, addChapters, false, ArrayList(sponsorblock), saveSubs)
        val downloadPath = File(historyItem.downloadPath)
        val path = if (downloadPath.exists()) downloadPath.parent else defaultPath
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


    fun getFormat(formats: List<Format>, type: Type) : Format {
        when(type) {
            Type.audio -> {
                return cloneFormat (
                    try {
                        val theFormats = formats.filter { it.format_note.contains("audio", ignoreCase = true) }
                        val requirements: MutableList<(Format) -> Boolean> = mutableListOf()
                        requirements.add {it: Format -> audioFormatIDPreference.contains(it.format_id)}
                        requirements.add {it: Format -> it.container == audioContainer }
                        requirements.add {it: Format -> "^(${audioCodec}).+$".toRegex(RegexOption.IGNORE_CASE).matches(it.acodec)}
                        theFormats.maxByOrNull { f -> requirements.count{req -> req(f)} } ?: throw Exception()
                    }catch (e: Exception){
                        bestAudioFormat
                    }
                )

            }
            Type.video -> {
                return cloneFormat(
                    try {
                        val theFormats = formats.filter { !it.format_note.contains("audio", true) }.ifEmpty { defaultVideoFormats.sortedByDescending { it.filesize } }
                        when (videoQualityPreference) {
                            "worst" -> {
                                theFormats.last()
                            }
                            else /*best*/ -> {
                                val requirements: MutableList<(Format) -> Boolean> = mutableListOf()
                                requirements.add { it: Format -> formatIDPreference.contains(it.format_id) }
                                requirements.add { it: Format -> if (videoContainer == "mp4") it.container.equals("mpeg_4", true) else it.container.equals(videoContainer, true)}
                                requirements.add { it: Format -> it.format_note.contains(videoQualityPreference.split("_")[0].dropLast(1)) }
                                requirements.add { it: Format -> "^(${videoCodec}).+$".toRegex(RegexOption.IGNORE_CASE).matches(it.vcodec)}
                                theFormats.maxByOrNull { f -> requirements.count{ req -> req(f)} } ?: throw Exception()
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
                    commandTemplateDao.getFirst() ?: CommandTemplate(0,"","", false)
                }else{
                    commandTemplateDao.getTemplateByContent(lastUsedCommandTemplate) ?: CommandTemplate(0, "", lastUsedCommandTemplate, false)
                }
                return generateCommandFormat(c)
            }
        }
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

    fun deleteCancelled() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteCancelled()
    }

    fun deleteErrored() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteErrored()
    }

    fun deleteSaved() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteSaved()
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

    fun getPausedDownloads() : List<DownloadItem> {
        return repository.getPausedDownloads()
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
        startDownloadWorker(emptyList())
    }

    suspend fun putAtTopOfQueue(id: Long) = CoroutineScope(Dispatchers.IO).launch{
        dbManager.downloadDao.putAtTopOfTheQueue(id)
        startDownloadWorker(emptyList())
    }

    suspend fun reQueueDownloadItems(items: List<Long>) = CoroutineScope(Dispatchers.IO).launch {
        dbManager.downloadDao.reQueueDownloadItems(items)
        startDownloadWorker(emptyList())
    }

    suspend fun queueDownloads(items: List<DownloadItem>, ignoreDuplicate : Boolean = false) = CoroutineScope(Dispatchers.IO).launch  {
        val context = App.instance
        val alarmScheduler = AlarmScheduler(context)
        val activeAndQueuedDownloads = withContext(Dispatchers.IO){
            repository.getActiveAndQueuedDownloads()
        }
        val queuedItems = mutableListOf<DownloadItem>()
        val existing = mutableListOf<DownloadItem>()

        //if scheduler is on
        val useScheduler = sharedPreferences.getBoolean("use_scheduler", false)
        if (useScheduler && !alarmScheduler.isDuringTheScheduledTime()){
            alarmScheduler.schedule()
        }

        items.forEach {
            if (it.status != DownloadRepository.Status.Paused.toString()) it.status = DownloadRepository.Status.Queued.toString()
            val currentCommand = infoUtil.buildYoutubeDLRequest(it)
            val parsedCurrentCommand = infoUtil.parseYTDLRequestString(currentCommand)
            val existingDownload = activeAndQueuedDownloads.firstOrNull{d ->
                d.id = 0
                d.logID = null
                d.customFileNameTemplate = it.customFileNameTemplate
                d.status = DownloadRepository.Status.Queued.toString()
                d.toString() == it.toString()
            }
            if (existingDownload != null && !ignoreDuplicate) {
                it.id = existingDownload.id
                existing.add(it)
            }else{
                //check if downloaded and file exists
                val history = withContext(Dispatchers.IO){
                    historyRepository.getAllByURL(it.url).filter { item -> FileUtil.exists(item.downloadPath) }
                }

                val existingHistory = history.firstOrNull {
                        h -> h.command.replace("(-P \"(.*?)\")|(--trim-filenames \"(.*?)\")".toRegex(), "") == parsedCurrentCommand.replace("(-P \"(.*?)\")|(--trim-filenames \"(.*?)\")".toRegex(), "")
                }

                if (existingHistory != null && !ignoreDuplicate){
                    it.id = existingHistory.id
                    existing.add(it)
                }else{
                    if (it.id == 0L){
                        val id = withContext(Dispatchers.IO){
                            repository.insert(it)
                        }
                        it.id = id
                    }else if (it.status == DownloadRepository.Status.Queued.toString()){
                        withContext(Dispatchers.IO){
                            repository.update(it)
                        }
                    }

                    queuedItems.add(it)
                }
            }
        }

        if (existing.isNotEmpty()){
            uiState.update { u -> u.copy(
                    errorMessage = Pair(R.string.download_already_exists, R.string.download_already_exists_summary),
                    actions = mutableListOf(
                        Triple(R.string.download, DownloadsAction.DOWNLOAD_ANYWAY, existing),
                    )
                )
            }
        }

        if (!useScheduler || alarmScheduler.isDuringTheScheduledTime() || items.any { it.downloadStartTime > 0L } ){
            startDownloadWorker(queuedItems)

            if(!useScheduler){
                queuedItems.filter { it.downloadStartTime != 0L && (it.title.isEmpty() || it.author.isEmpty() || it.thumb.isEmpty()) }.forEach {
                    try{
                        updateDownloadItem(it, infoUtil, dbManager.downloadDao, dbManager.resultDao)
                    }catch (ignored: Exception){}
                }
            }else{
                queuedItems.filter { it.title.isEmpty() || it.author.isEmpty() || it.thumb.isEmpty() }.forEach {
                    try{
                        updateDownloadItem(it, infoUtil, dbManager.downloadDao, dbManager.resultDao)
                    }catch (ignored: Exception){}
                }
            }
        }
    }

    private suspend fun startDownloadWorker(queuedItems: List<DownloadItem>) {
        val context = App.instance
        val allowMeteredNetworks = sharedPreferences.getBoolean("metered_networks", true)
        val workManager = WorkManager.getInstance(context)

        val currentWork = workManager.getWorkInfosByTag("download").await()
        if (currentWork.size == 0 || currentWork.none{ it.state == WorkInfo.State.RUNNING } || (queuedItems.isNotEmpty() && queuedItems[0].downloadStartTime != 0L)){

            val currentTime = System.currentTimeMillis()
            var delay = 0L
            if (queuedItems.isNotEmpty()){
                delay = if (queuedItems[0].downloadStartTime != 0L){
                    queuedItems[0].downloadStartTime.minus(currentTime)
                } else 0
                if (delay <= 60000L) delay = 0L
            }


            val workConstraints = Constraints.Builder()
            if (!allowMeteredNetworks) workConstraints.setRequiredNetworkType(NetworkType.UNMETERED)

            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .addTag("download")
                .setConstraints(workConstraints.build())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)

            queuedItems.forEach {
                workRequest.addTag(it.id.toString())
            }

            workManager.enqueueUniqueWork(
                System.currentTimeMillis().toString(),
                ExistingWorkPolicy.REPLACE,
                workRequest.build()
            )

        }

        val isCurrentNetworkMetered = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
        if (!allowMeteredNetworks && isCurrentNetworkMetered){
            Looper.prepare().run {
                Toast.makeText(context, context.getString(R.string.metered_network_download_start_info), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun getQueuedCollectedFileSize() : Long {
        return dbManager.downloadDao.getSelectedFormatFromQueued().sumOf { it.filesize }
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

    private fun updateDownloadItem(
        downloadItem: DownloadItem,
        infoUtil: InfoUtil,
        dao: DownloadDao,
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
                    dao.update(downloadItem)
                }
            }
        }
        return wasQuickDownloaded
    }

}