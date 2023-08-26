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
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.models.VideoPreferences
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.work.DownloadWorker
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    val queuedDownloads : Flow<PagingData<DownloadItem>>
    val activeDownloads : LiveData<List<DownloadItem>>
    val activeDownloadsCount : LiveData<Int>
    val cancelledDownloads : Flow<PagingData<DownloadItem>>
    val erroredDownloads : Flow<PagingData<DownloadItem>>
    val savedDownloads : Flow<PagingData<DownloadItem>>

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
    private val dao: DownloadDao

    enum class Type {
        audio, video, command
    }

    init {
        dbManager =  DBManager.getInstance(application)
        dao = dbManager.downloadDao
        repository = DownloadRepository(dao)
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
        confTmp.locale = Locale(sharedPreferences.getString("app_language", "en")!!)
        val metrics = DisplayMetrics()
        resources = Resources(application.assets, metrics, confTmp)


        val videoFormatValues = resources.getStringArray(R.array.video_formats_values)
        videoContainer = sharedPreferences.getString("video_format",  "Default")

        defaultVideoFormats = mutableListOf()
        videoFormatValues.forEach {
            val tmp = Format(
                it,
                videoContainer!!,
                "",
                "",
                "",
                0,
                it
            )
            defaultVideoFormats.add(tmp)
        }
        formatIDPreference.reversed().forEach {
            val tmp = Format(
                it,
                videoContainer!!,
                "",
                "",
                "",
                0,
                it
            )
            defaultVideoFormats.add(tmp)
        }

        bestVideoFormat = defaultVideoFormats.last()

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

    fun createDownloadItemFromResult(resultItem: ResultItem, givenType: Type) : DownloadItem {
        val embedSubs = sharedPreferences.getBoolean("embed_subtitles", false)
        val saveSubs = sharedPreferences.getBoolean("write_subtitles", false)
        val addChapters = sharedPreferences.getBoolean("add_chapters", false)
        val saveThumb = sharedPreferences.getBoolean("write_thumbnail", false)
        val embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false)

        var type = givenType
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
        }

        list.forEach {
            val format = getFormat(it.allFormats, type)
            it.format = format
            val currentDownloadPath = when(it.type){
                Type.audio -> sharedPreferences.getString("music_path", FileUtil.getDefaultAudioPath())
                Type.video -> sharedPreferences.getString("video_path", FileUtil.getDefaultVideoPath())
                Type.command -> sharedPreferences.getString("command_path", FileUtil.getDefaultCommandPath())
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
                        try{
                            formats.filter { it.format_note.contains("audio", ignoreCase = true)}.sortedBy { it.format_id }
                                .firstOrNull { audioFormatIDPreference.indexOfFirst { a -> a.contains(it.format_id) } >= 0 || it.container == audioContainer} ?: throw Exception()
                        }catch (e: Exception){
                            formats.last { it.format_note.contains("audio", ignoreCase = true) }
                        }
                    }catch (e: Exception){
                        bestAudioFormat
                    }
                )

            }
            Type.video -> {
                return cloneFormat(
                    try {
                        val theFormats = formats.filter { !it.format_note.contains("audio", true) }.sortedBy { it.format_id }.ifEmpty { defaultVideoFormats }
                        when (videoQualityPreference) {
                            "worst" -> {
                                theFormats.first()
                            }
                            else /*best*/ -> {
                                val requirements: MutableList<(Format) -> Boolean> = mutableListOf()
                                requirements.add { it: Format -> formatIDPreference.sorted().contains(it.format_id) }
                                requirements.add { it: Format -> if (videoContainer == "mp4") it.container.equals("mpeg_4", true) else it.container.equals(videoContainer, true)}
                                requirements.add { it: Format -> it.format_note.contains(videoQualityPreference.substring(0, videoQualityPreference.length - 1)) }
                                requirements.add { it: Format -> it.vcodec.startsWith(videoCodec!!, true) }

                                theFormats.reversed().maxByOrNull { f -> requirements.count{ req -> req(f)} } ?: throw Exception()
                            }
                        }
                    }catch (e: Exception){
                        bestVideoFormat
                    }
                )
            }
            else -> {
                val c = commandTemplateDao.getFirst()
                return generateCommandFormat(c)
            }
        }
    }

    fun generateCommandFormat(c: CommandTemplate) : Format {
        return Format(
            c.title,
            "",
            "",
            "",
            "",
            0,
            c.content.replace("\n", " ")
        )
    }

    fun getGenericAudioFormats() : MutableList<Format>{
        val audioFormats = resources.getStringArray(R.array.audio_formats)
        val formats = mutableListOf<Format>()
        val containerPreference = sharedPreferences.getString("audio_format", "")
        audioFormatIDPreference.forEach { formats.add(Format(it, containerPreference!!,"","", "",0, it)) }
        audioFormats.forEach { formats.add(Format(it, containerPreference!!,"","", "",0, it)) }
        return formats
    }

    fun getGenericVideoFormats() : MutableList<Format>{
        val videoFormats = resources.getStringArray(R.array.video_formats_values)
        val formats = mutableListOf<Format>()
        val containerPreference = sharedPreferences.getString("video_format", "")
        formatIDPreference.forEach { formats.add(Format(it, containerPreference!!,"Default","", "",0, it)) }
        videoFormats.forEach { formats.add(Format(it, containerPreference!!,"Default","", "",0, it)) }
        return formats
    }

    fun getLatestCommandTemplateAsFormat() : Format {
        val t = commandTemplateDao.getFirst()
        return Format(t.title, "", "", "", "", 0, t.content)
    }

    fun turnResultItemsToDownloadItems(items: List<ResultItem?>) : List<DownloadItem> {
        val list : MutableList<DownloadItem> = mutableListOf()
        val preferredType = sharedPreferences.getString("preferred_download_type", "video")
        items.forEach {
            list.add(createDownloadItemFromResult(it!!, Type.valueOf(preferredType!!)))
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

    fun cancelQueued() = viewModelScope.launch(Dispatchers.IO) {
        repository.cancelQueued()
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

    suspend fun queueDownloads(items: List<DownloadItem>) = CoroutineScope(Dispatchers.IO).launch {
        val context = App.instance
        val activeAndQueuedDownloads = repository.getActiveAndQueuedDownloads()
        val queuedItems = mutableListOf<DownloadItem>()
        var exists = false

        items.forEach {
            if (it.status != DownloadRepository.Status.Paused.toString()) it.status = DownloadRepository.Status.Queued.toString()
            if (activeAndQueuedDownloads.firstOrNull{d ->
                    d.id = 0
                    d.logID = null
                    d.customFileNameTemplate = it.customFileNameTemplate
                    d.status = DownloadRepository.Status.Queued.toString()
                    d.toString() == it.toString()
            } != null) {
                exists = true
            }else{
                if (it.id == 0L){
                    val id = runBlocking {repository.insert(it)}
                    it.id = id
                }else if (it.status == DownloadRepository.Status.Queued.toString()){
                    repository.update(it)
                }

                queuedItems.add(it)
            }
        }

        if (exists){
            Looper.prepare().run {
                Toast.makeText(context, context.getString(R.string.download_already_exists), Toast.LENGTH_LONG).show()
            }
        }

        startDownloadWorker(queuedItems)
    }

    private suspend fun startDownloadWorker(queuedItems: List<DownloadItem>) {
        val context = App.instance
        val allowMeteredNetworks = sharedPreferences.getBoolean("metered_networks", true)
        val workManager = WorkManager.getInstance(context)

        val currentWork = workManager.getWorkInfosByTag("download").await()
        if (currentWork.size == 0 || currentWork.none{ it.state == WorkInfo.State.RUNNING } || (queuedItems.isNotEmpty() && queuedItems[0].downloadStartTime != 0L)){

            val currentTime = System.currentTimeMillis()
            var delay = 1000L
            if (queuedItems.isNotEmpty()){
                delay = if (queuedItems[0].downloadStartTime != 0L){
                    queuedItems[0].downloadStartTime.minus(currentTime)
                } else 0
                if (delay <= 0L) delay = 1000L
            }


            val workConstraints = Constraints.Builder()
            if (!allowMeteredNetworks) workConstraints.setRequiredNetworkType(NetworkType.UNMETERED)

            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .addTag("download")
                .setConstraints(workConstraints.build())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)

            if (delay > 1000L){
                queuedItems.forEach {
                    workRequest.addTag(it.id.toString())
                }
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

        queuedItems.filter { it.downloadStartTime != 0L && (it.title.isEmpty() || it.author.isEmpty() || it.thumb.isEmpty()) }?.forEach {
            try{
                updateDownloadItem(it, infoUtil, dbManager.downloadDao, dbManager.resultDao)
            }catch (ignored: Exception){}
        }
    }

    fun getQueuedCollectedFileSize() : Long {
        return dbManager.downloadDao.getSelectedFormatFromQueued().sumOf { it.filesize }
    }

    fun getTotalSize(status: List<DownloadRepository.Status>) : LiveData<Int> {
        return dbManager.downloadDao.getDownloadsCountByStatus(status.map { it.toString() }).asLiveData()
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