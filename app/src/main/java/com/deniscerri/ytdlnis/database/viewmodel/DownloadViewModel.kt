package com.deniscerri.ytdlnis.database.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.net.ConnectivityManager
import android.os.Environment
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.App
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.dao.CommandTemplateDao
import com.deniscerri.ytdlnis.database.models.AudioPreferences
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.models.VideoPreferences
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.work.DownloadWorker
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit


class DownloadViewModel(private val application: Application) : AndroidViewModel(application) {
    private val repository : DownloadRepository
    private val sharedPreferences: SharedPreferences
    private val commandTemplateDao: CommandTemplateDao
    private val infoUtil : InfoUtil
    val allDownloads : LiveData<List<DownloadItem>>
    val queuedDownloads : LiveData<List<DownloadItem>>
    val activeDownloads : LiveData<List<DownloadItem>>
    val activeDownloadsCount : LiveData<Int>
    val cancelledDownloads : LiveData<List<DownloadItem>>
    val erroredDownloads : LiveData<List<DownloadItem>>
    val processingDownloads : LiveData<List<DownloadItem>>

    private var bestVideoFormat : Format
    private var bestAudioFormat : Format
    private var defaultVideoFormats : MutableList<Format>

    private val videoQualityPreference: String
    private val formatIDPreference: String
    private val resources : Resources
    enum class Type {
        audio, video, command
    }

    init {
        val dao = DBManager.getInstance(application).downloadDao
        repository = DownloadRepository(dao)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
        commandTemplateDao = DBManager.getInstance(application).commandTemplateDao
        infoUtil = InfoUtil(application)

        allDownloads = repository.allDownloads.asLiveData()
        queuedDownloads = repository.queuedDownloads.asLiveData()
        activeDownloads = repository.activeDownloads.asLiveData()
        activeDownloadsCount = repository.activeDownloadsCount.asLiveData()
        processingDownloads = repository.processingDownloads.asLiveData()
        cancelledDownloads = repository.cancelledDownloads.asLiveData()
        erroredDownloads = repository.erroredDownloads.asLiveData()

        videoQualityPreference = sharedPreferences.getString("video_quality", application.getString(R.string.best_quality)).toString()
        formatIDPreference = sharedPreferences.getString("format_id", "").toString()

        val confTmp = Configuration(application.resources.configuration)
        confTmp.locale = Locale(sharedPreferences.getString("app_language", "en")!!)
        val metrics = DisplayMetrics()
        resources = Resources(application.assets, metrics, confTmp)


        val videoFormat = resources.getStringArray(R.array.video_formats)
        var videoContainer = sharedPreferences.getString("video_format",  "Default")
        if (videoContainer == "Default") videoContainer = App.instance.getString(R.string.defaultValue)

        defaultVideoFormats = mutableListOf()
        videoFormat.forEach {
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

        var audioContainer = sharedPreferences.getString("audio_format", "mp3")
        if (audioContainer == "Default") audioContainer = App.instance.getString(R.string.defaultValue)
        bestAudioFormat = Format(
            resources.getString(R.string.best_quality),
            audioContainer!!,
            "",
            "",
            "",
            0,
            resources.getString(R.string.best_quality)
        )
    }

    fun deleteDownload(item: DownloadItem) = viewModelScope.launch(Dispatchers.IO) {
        repository.delete(item)
    }

    fun updateDownload(item: DownloadItem) = viewModelScope.launch(Dispatchers.IO){
        repository.update(item)
    }

    fun getItemByID(id: Long) : DownloadItem {
        return repository.getItemByID(id)
    }

    fun createDownloadItemFromResult(resultItem: ResultItem, type: Type) : DownloadItem {
        val embedSubs = sharedPreferences.getBoolean("embed_subtitles", false)
        val saveSubs = sharedPreferences.getBoolean("write_subtitles", false)
        val addChapters = sharedPreferences.getBoolean("add_chapters", false)
        val saveThumb = sharedPreferences.getBoolean("write_thumbnail", false)
        val embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false)
        val customFileNameTemplate = when(type) {
            Type.audio -> sharedPreferences.getString("file_name_template_audio", "%(uploader)s - %(title)s")
            Type.video -> sharedPreferences.getString("file_name_template", "%(uploader)s - %(title)s")
            else -> ""
        }

        val downloadPath = when(type){
            Type.audio -> sharedPreferences.getString("music_path", File(Environment.DIRECTORY_DOWNLOADS, "YTDLnis/Audio").absolutePath)
            Type.video -> sharedPreferences.getString("video_path", File(Environment.DIRECTORY_DOWNLOADS, "YTDLnis/Video").absolutePath)
            else -> sharedPreferences.getString("command_path", File(Environment.DIRECTORY_DOWNLOADS, "YTDLnis/Command").absolutePath)
        }

        val sponsorblock = sharedPreferences.getStringSet("sponsorblock_filters", emptySet())

        val audioPreferences = AudioPreferences(embedThumb, false, ArrayList(sponsorblock!!))
        val videoPreferences = VideoPreferences(embedSubs, addChapters, false, ArrayList(sponsorblock), saveSubs)

        return DownloadItem(0,
            resultItem.url,
            resultItem.title,
            resultItem.author,
            resultItem.thumb,
            resultItem.duration,
            type,
            getFormat(resultItem.formats, type),
            "",
            resultItem.formats,
            downloadPath!!, resultItem.website, "", resultItem.playlistTitle, audioPreferences, videoPreferences,customFileNameTemplate!!, saveThumb, DownloadRepository.Status.Processing.toString(), 0
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
            System.currentTimeMillis()
        )

    }

    fun switchDownloadType(list: List<DownloadItem>, type: Type) : List<DownloadItem>{
        list.forEach {
            val format = getFormat(it.allFormats, type)
            it.format = format
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

        val downloadPath = when(historyItem.type){
            Type.audio -> sharedPreferences.getString("music_path", File(Environment.DIRECTORY_DOWNLOADS, "YTDLnis/Audio").absolutePath)
            Type.video -> sharedPreferences.getString("video_path", File(Environment.DIRECTORY_DOWNLOADS, "YTDLnis/Video").absolutePath)
            else -> sharedPreferences.getString("command_path", File(Environment.DIRECTORY_DOWNLOADS, "YTDLnis/Command").absolutePath)
        }

        val sponsorblock = sharedPreferences.getStringSet("sponsorblock_filters", emptySet())

        val audioPreferences = AudioPreferences(embedThumb, false, ArrayList(sponsorblock!!))
        val videoPreferences = VideoPreferences(embedSubs, addChapters, false, ArrayList(sponsorblock), saveSubs)

        return DownloadItem(historyItem.downloadId,
            historyItem.url,
            historyItem.title,
            historyItem.author,
            historyItem.thumb,
            historyItem.duration,
            historyItem.type,
            historyItem.format,
            "",
            ArrayList(),
            downloadPath!!, historyItem.website, "", "", audioPreferences, videoPreferences,customFileNameTemplate!!, saveThumb, DownloadRepository.Status.Processing.toString(), 0
        )

    }


    private fun getFormat(formats: List<Format>, type: Type) : Format {
        when(type) {
            Type.audio -> {
                return cloneFormat (
                    try {
                        try{
                            formats.first { it.format_note.contains("audio", ignoreCase = true) && it.format_id == formatIDPreference }
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
                        val theFormats = formats.ifEmpty { defaultVideoFormats }
                        try {
                            formats.first { !it.format_note.contains("audio", ignoreCase = true) && it.format_id == formatIDPreference }
                        }catch (e: Exception){
                            when (videoQualityPreference) {
                                "worst" -> {
                                    theFormats.first {!it.format_note.contains("audio", ignoreCase = true) }
                                }
                                "best" -> {
                                    theFormats.last {!it.format_note.contains("audio", ignoreCase = true) }
                                }
                                else -> {
                                    try{
                                        theFormats.last {it.format_note.contains(videoQualityPreference.substring(0, videoQualityPreference.length - 1)) }
                                    }catch (e: Exception){
                                        theFormats.last { !it.format_note.contains("audio", ignoreCase = true) }
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
        var containerPreference = sharedPreferences.getString("audio_format", "Default")
        if (containerPreference == "Default") containerPreference = resources.getString(R.string.defaultValue)
        audioFormats.forEach { formats.add(Format(it, containerPreference!!,"","", "",0, it)) }
        return formats
    }

    fun getGenericVideoFormats() : MutableList<Format>{
        val videoFormats = resources.getStringArray(R.array.video_formats)
        val formats = mutableListOf<Format>()
        var containerPreference = sharedPreferences.getString("video_format", "Default")
        if (containerPreference == "Default") containerPreference = resources.getString(R.string.defaultValue)
        videoFormats.forEach { formats.add(Format(it, containerPreference!!,resources.getString(R.string.defaultValue),"", "",0, it)) }
        return formats
    }

    fun getLatestCommandTemplateAsFormat() : Format {
        val t = commandTemplateDao.getFirst()
        return Format(t.title, "", "", "", "", 0, t.content)
    }

    fun turnResultItemsToDownloadItems(items: List<ResultItem?>) : List<DownloadItem> {
        val list : MutableList<DownloadItem> = mutableListOf()
        val preferredType = sharedPreferences.getString("preferred_download_type", "video");
        items.forEach {
            list.add(createDownloadItemFromResult(it!!, Type.valueOf(preferredType!!)))
        }
        return list
    }

    fun insert(item: DownloadItem) = viewModelScope.launch(Dispatchers.IO){
        repository.insert(item)
    }

    fun deleteCancelled() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteCancelled()
    }

    fun deleteErrored() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteErrored()
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

    fun getErrored() : List<DownloadItem> {
        return repository.getErroredDownloads()
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

    suspend fun queueDownloads(items: List<DownloadItem>) = CoroutineScope(Dispatchers.IO).launch {
        val context = App.instance
        val activeAndQueuedDownloads = repository.getActiveAndQueuedDownloads()
        val allowMeteredNetworks = sharedPreferences.getBoolean("metered_networks", true)
        val queuedItems = mutableListOf<DownloadItem>()
        var lastDownloadId = repository.getLastDownloadId()
        items.forEach {
            lastDownloadId++
            it.status = DownloadRepository.Status.Queued.toString()
            if (activeAndQueuedDownloads.firstOrNull{d ->
                    d.id = 0
                    d.status = DownloadRepository.Status.Queued.toString()
                    d.toString() == it.toString()
            } != null) {
                Toast.makeText(context, context.getString(R.string.download_already_exists), Toast.LENGTH_LONG).show()
            }else{
                if (it.id == 0L){
                    it.id = lastDownloadId
                    val insert = async {repository.insert(it)}
                    val id = insert.await()
                    it.id = id
                }else{
                    repository.update(it)
                }

                queuedItems.add(it)
            }
        }

        //if the download was quick downloaded and are incomplete
        if (queuedItems.count() == 1){
            val item = queuedItems.first()
            if (item.title.isEmpty() || item.author.isEmpty() || item.thumb.isEmpty()){
                runCatching {
                    val info = infoUtil.getMissingInfo(item.url)
                    if (item.title.isEmpty()) item.title = info?.title.toString()
                    if (item.author.isEmpty()) item.author = info?.author.toString()
                    item.duration = info?.duration.toString()
                    item.website = info?.website.toString()
                    if (item.thumb.isEmpty()) item.thumb = info?.thumb.toString()
                    repository.update(item)
                }.onFailure {  err ->
                    Log.e("error", err.message.toString())
                }
            }
        }

        queuedItems.forEach {
            val currentTime = System.currentTimeMillis()
            var delay = if (it.downloadStartTime != 0L){
                it.downloadStartTime - currentTime
            } else 0
            if (delay < 0L) delay = 0L

            val workConstraints = Constraints.Builder()
            if (!allowMeteredNetworks) workConstraints.setRequiredNetworkType(NetworkType.UNMETERED)

            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(Data.Builder().putLong("id", it.id).build())
                .addTag("download")
                .setConstraints(workConstraints.build())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).beginUniqueWork(
                it.id.toString(),
                ExistingWorkPolicy.KEEP,
                workRequest
            ).enqueue()

            it.id = withContext(Dispatchers.IO){
                repository.checkIfReDownloadingErroredOrCancelled(it)
            }
            if (it.id != 0L){
                repository.delete(it)
            }
        }

        val isCurrentNetworkMetered = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
        if (!allowMeteredNetworks && isCurrentNetworkMetered){
            Toast.makeText(context, context.getString(R.string.metered_network_download_start_info), Toast.LENGTH_LONG).show()
        }
    }

}