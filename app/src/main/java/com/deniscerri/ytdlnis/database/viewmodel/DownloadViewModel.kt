package com.deniscerri.ytdlnis.database.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.deniscerri.ytdlnis.App
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.dao.CommandTemplateDao
import com.deniscerri.ytdlnis.database.models.*
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.work.DownloadWorker
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val repository : DownloadRepository
    private val sharedPreferences: SharedPreferences
    private val commandTemplateDao: CommandTemplateDao
    val allDownloads : LiveData<List<DownloadItem>>
    val queuedDownloads : LiveData<List<DownloadItem>>
    val activeDownloads : LiveData<List<DownloadItem>>
    val cancelledDownloads : LiveData<List<DownloadItem>>
    val erroredDownloads : LiveData<List<DownloadItem>>
    val processingDownloads : LiveData<List<DownloadItem>>

    private var bestVideoFormat : Format
    private var bestAudioFormat : Format
    private var defaultVideoFormats : MutableList<Format>
    enum class Type {
        audio, video, command
    }

    init {
        val dao = DBManager.getInstance(application).downloadDao
        repository = DownloadRepository(dao)
        sharedPreferences =
            getApplication<App>().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
        commandTemplateDao = DBManager.getInstance(application).commandTemplateDao

        allDownloads = repository.allDownloads
        queuedDownloads = repository.queuedDownloads
        activeDownloads = repository.activeDownloads
        processingDownloads = repository.processingDownloads
        cancelledDownloads = repository.cancelledDownloads
        erroredDownloads = repository.erroredDownloads

        val videoFormat = getApplication<App>().resources.getStringArray(R.array.video_formats)
        val videoContainer = sharedPreferences.getString("video_format",  getApplication<App>().resources.getString(R.string.defaultValue))

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

        val audioContainer = sharedPreferences.getString("audio_format", "mp3")
        bestAudioFormat = Format(
            getApplication<App>().resources.getString(R.string.best_quality),
            audioContainer!!,
            "",
            "",
            "",
            0,
            getApplication<App>().resources.getString(R.string.best_quality)
        )
    }

    fun deleteDownload(item: DownloadItem) = viewModelScope.launch(Dispatchers.IO) {
        repository.delete(item)
    }

    fun updateDownload(item: DownloadItem) = viewModelScope.launch(Dispatchers.IO){
        repository.update(item);
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
        val customFileNameTemplate = sharedPreferences.getString("file_name_template", "%(uploader)s - %(title)s")

        val downloadPath = when(type){
            Type.audio -> sharedPreferences.getString("music_path", getApplication<App>().resources.getString(R.string.music_path))
            Type.video -> sharedPreferences.getString("video_path", getApplication<App>().resources.getString(R.string.video_path))
            else -> sharedPreferences.getString("command_path", getApplication<App>().resources.getString(R.string.command_path))
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
        val customFileNameTemplate = sharedPreferences.getString("file_name_template", "%(uploader)s - %(title)s")

        val downloadPath = when(historyItem.type){
            Type.audio -> sharedPreferences.getString("music_path", getApplication<App>().resources.getString(R.string.music_path))
            Type.video -> sharedPreferences.getString("video_path", getApplication<App>().resources.getString(R.string.video_path))
            else -> sharedPreferences.getString("command_path", getApplication<App>().resources.getString(R.string.command_path))
        }

        val sponsorblock = sharedPreferences.getStringSet("sponsorblock_filters", emptySet())

        val audioPreferences = AudioPreferences(embedThumb, false, ArrayList(sponsorblock!!))
        val videoPreferences = VideoPreferences(embedSubs, addChapters, false, ArrayList(sponsorblock), saveSubs)

        return DownloadItem(0,
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
                        formats.last { it.format_note.contains("audio", ignoreCase = true) }
                    }catch (e: Exception){
                        bestAudioFormat
                    }
                )

            }
            Type.video -> {
                return cloneFormat(
                    try {
                        val theFormats = formats.ifEmpty { defaultVideoFormats }

                        val qualityPreference = sharedPreferences.getString("video_quality", getApplication<App>().resources.getString(R.string.best_quality))
                        if (qualityPreference == getApplication<App>().resources.getString(R.string.worst_quality)){
                            theFormats.first { !it.format_note.contains("audio", ignoreCase = true) }
                        }
                        if (qualityPreference == getApplication<App>().resources.getString(R.string.best_quality)){
                            theFormats.last { !it.format_note.contains("audio", ignoreCase = true) }
                        }
                        try{
                            theFormats.last { format -> format.format_note.contains(qualityPreference!!.substring(0, qualityPreference.length - 1)) }
                        }catch (e: Exception){
                            theFormats.last { !it.format_note.contains("audio", ignoreCase = true) }
                        }
                    }catch (e: Exception){
                        bestVideoFormat
                    }
                )
            }
            else -> {
                val c = commandTemplateDao.getFirst()
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
        }
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

    fun putDownloadsForProcessing(items: List<ResultItem?>, downloadItems: List<DownloadItem>) : LiveData<List<Long>> {
        val result = MutableLiveData<List<Long>>()
        viewModelScope.launch(Dispatchers.IO){
            val list : MutableList<Long> = mutableListOf()
            items.forEachIndexed { i, it ->
                val tmpDownloadItem = downloadItems[i]
                try {
                    val item = repository.checkIfPresentForProcessing(it!!)
                    tmpDownloadItem.id = item.id
                    tmpDownloadItem.status = DownloadRepository.Status.Processing.toString()
                    repository.update(tmpDownloadItem)
                    list.add(tmpDownloadItem.id)
                }catch (e: Exception){
                    val id = repository.insert(tmpDownloadItem)
                    list.add(id)
                }

            }
            result.postValue(list)
        }
        return result
    }

    fun deleteCancelled() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteCancelled()
    }

    fun deleteErrored() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteErrored()
    }

    fun deleteQueued() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteQueued()
    }

    fun cloneFormat(item: Format) : Format {
        val string = Gson().toJson(item, Format::class.java)
        return Gson().fromJson(string, Format::class.java)
    }

    suspend fun queueDownloads(items: List<DownloadItem>) {
        val context = getApplication<App>().applicationContext
        val allowMeteredNetworks = sharedPreferences.getBoolean("metered_networks", true)
        items.forEach { it.status = DownloadRepository.Status.Queued.toString() }
        val ids = repository.insertAll(items)
        for (i in ids.indices){
            val it = items[i]
            it.id = ids[i]
            val currentTime = System.currentTimeMillis()
            var delay = if (it.downloadStartTime != 0L){
                it.downloadStartTime - currentTime
            } else 0
            if (delay < 0L) delay = 0L

            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(Data.Builder().putLong("id", it.id).build())
                .addTag("download")
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (allowMeteredNetworks) NetworkType.CONNECTED else NetworkType.UNMETERED)
                        .build()
                )
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).beginUniqueWork(
                it.id.toString(),
                ExistingWorkPolicy.KEEP,
                workRequest
            ).enqueue()
        }
        items.forEach {
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