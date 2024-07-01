package com.deniscerri.ytdl.database.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.dao.CommandTemplateDao
import com.deniscerri.ytdl.database.dao.DownloadDao
import com.deniscerri.ytdl.database.models.AudioPreferences
import com.deniscerri.ytdl.database.models.CommandTemplate
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.models.VideoPreferences
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.repository.HistoryRepository
import com.deniscerri.ytdl.database.repository.ResultRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdl.util.Extensions.toListString
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.FormatSorter
import com.deniscerri.ytdl.util.InfoUtil
import com.deniscerri.ytdl.work.AlarmScheduler
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import okhttp3.internal.immutableListOf
import java.io.File
import java.util.Locale


class SharedDownloadViewModel(private val context: Context) {
    private val dbManager: DBManager = DBManager.getInstance(context)
    val repository : DownloadRepository
    private val sharedPreferences: SharedPreferences
    private val commandTemplateDao: CommandTemplateDao
    private val infoUtil : InfoUtil

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

    @Parcelize
    data class AlreadyExistsIDs(
        var downloadItemID: Long,
        var historyItemID : Long?
    ) : Parcelable

    val alreadyExistsUiState: MutableStateFlow<List<AlreadyExistsIDs>> = MutableStateFlow(
        mutableListOf()
    )

    private val urlsForAudioType = listOf(
        "music",
        "audio",
        "soundcloud"
    )

    init {
        dao = dbManager.downloadDao
        repository = DownloadRepository(dao)
        historyRepository = HistoryRepository(dbManager.historyDao)
        resultRepository = ResultRepository(dbManager.resultDao, context)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        commandTemplateDao = DBManager.getInstance(context).commandTemplateDao
        infoUtil = InfoUtil(context)

        CoroutineScope(SupervisorJob()).launch(Dispatchers.IO) {
            if (sharedPreferences.getBoolean("use_extra_commands", false)){
                extraCommandsForAudio = commandTemplateDao.getAllTemplatesAsExtraCommandsForAudio().joinToString(" ")
                extraCommandsForVideo = commandTemplateDao.getAllTemplatesAsExtraCommandsForVideo().joinToString(" ")
            }
        }

        videoQualityPreference = sharedPreferences.getString("video_quality", "best").toString()
        formatIDPreference = sharedPreferences.getString("format_id", "").toString().split(",").filter { it.isNotEmpty() }
        audioFormatIDPreference = sharedPreferences.getString("format_id_audio", "").toString().split(",").filter { it.isNotEmpty() }

        val confTmp = Configuration(context.resources.configuration)
        confTmp.setLocale(Locale(sharedPreferences.getString("app_language", "en")!!))
        val metrics = DisplayMetrics()
        resources = Resources(context.assets, metrics, confTmp)


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

    fun getPreferredAudioRequirements(): MutableList<(Format) -> Int> {
        val requirements: MutableList<(Format) -> Int> = mutableListOf()

        val itemValues = resources.getStringArray(R.array.format_importance_audio_values).toSet()
        val prefAudio = sharedPreferences.getString("format_importance_audio", itemValues.joinToString(","))!!

        prefAudio.split(",").forEachIndexed { idx, s ->
            val importance = (itemValues.size - idx) * 10
            when(s) {
                "id" -> {
                    requirements.add {it: Format -> if (audioFormatIDPreference.contains(it.format_id)) importance else 0}
                }
                "language" -> {
                    sharedPreferences.getString("audio_language", "")?.apply {
                        if (this.isNotBlank()){
                            requirements.add { it: Format -> if (it.lang?.contains(this) == true) importance else 0 }
                        }
                    }
                }
                "codec" -> {
                    requirements.add {it: Format -> if ("^(${audioCodec}).+$".toRegex(RegexOption.IGNORE_CASE).matches(it.acodec)) importance else 0}
                }
                "container" -> {
                    requirements.add {it: Format -> if (it.container == audioContainer) importance else 0 }
                }
            }
        }

        return requirements
    }

    //requirement and importance
    @SuppressLint("RestrictedApi")
    fun getPreferredVideoRequirements(): MutableList<(Format) -> Int> {
        val requirements: MutableList<(Format) -> Int> = mutableListOf()

        val itemValues = resources.getStringArray(R.array.format_importance_video_values).toSet()
        val prefVideo = sharedPreferences.getString("format_importance_video", itemValues.joinToString(","))!!

        prefVideo.split(",").forEachIndexed { idx, s ->
            var importance = (itemValues.size - idx) * 10

            when(s) {
                "id" -> {
                    requirements.add { it: Format -> if (formatIDPreference.contains(it.format_id)) importance else 0 }
                }
                "resolution" -> {
                    context.getStringArray(R.array.video_formats_values)
                        .filter { it.contains("_") }
                        .map{ it.split("_")[0].dropLast(1)
                        }.toMutableList().apply {
                            when(videoQualityPreference) {
                                "worst" -> {
                                    requirements.add { it: Format -> if (it.format_note.contains("worst", ignoreCase = true)) (importance) else 0 }
                                }
                                "best" -> {
                                    requirements.add { it: Format -> if (it.format_note.contains("best", ignoreCase = true)) (importance) else 0 }
                                }
                                else -> {
                                    val preferenceIndex = this.indexOfFirst { videoQualityPreference.contains(it) }
                                    val preference = this[preferenceIndex]
                                    for(i in 0..preferenceIndex){
                                        removeAt(0)
                                    }
                                    add(0, preference)
                                    forEachIndexed { index, res ->
                                        requirements.add { it: Format -> if (it.format_note.contains(res, ignoreCase = true)) (importance - index - 1) else 0 }
                                    }
                                }
                            }

                        }
                }
                "codec" -> {
                    requirements.add { it: Format -> if ("^(${videoCodec})(.+)?$".toRegex(RegexOption.IGNORE_CASE).matches(it.vcodec)) importance else 0 }
                }
                "no_audio" -> {
                    requirements.add { it: Format -> if (it.acodec == "none" || it.acodec == "") importance else 0 }
                }
                "container" -> {
                    requirements.add { it: Format ->
                        if (videoContainer == "mp4")
                            if (it.container.equals("mpeg_4", true)) importance else 0
                        else
                            if (it.container.equals(videoContainer, true)) importance else 0
                    }
                }
            }
        }

        return  requirements
    }

    fun getFormat(formats: List<Format>, type: Type) : Format {
        when(type) {
            Type.audio -> {
                return cloneFormat (
                    try {
                        val theFormats = formats.filter { it.vcodec.isBlank() || it.vcodec == "none" }
                        FormatSorter(context).sortAudioFormats(theFormats).first()
//
//                        val requirements = getPreferredAudioRequirements()
//                        theFormats.maxByOrNull { f -> requirements.sumOf{ req -> req(f)} } ?: throw Exception()
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

                        FormatSorter(context).sortVideoFormats(theFormats).first()
//
//                        when (videoQualityPreference) {
//                            "worst" -> {
//                                theFormats.last()
//                            }
//                            else /*best*/ -> {
//                                val requirements = getPreferredVideoRequirements()
//                                theFormats.run {
//                                    if (sharedPreferences.getBoolean("prefer_smaller_formats", false)){
//                                        sortedBy { it.filesize }.maxByOrNull { f -> requirements.sumOf { req -> req(f) } } ?: throw Exception()
//                                    }else{
//                                        sortedByDescending { it.filesize }.maxByOrNull { f ->
//                                            val summ = requirements.sumOf { req -> req(f) }
//                                            summ
//                                        } ?: throw Exception()
//                                    }
//                                }
//                            }
//                        }
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

    private fun cloneFormat(item: Format) : Format {
        val string = Gson().toJson(item, Format::class.java)
        return Gson().fromJson(string, Format::class.java)
    }

    suspend fun queueDownloads(items: List<DownloadItem>, ign : Boolean = false) : List<AlreadyExistsIDs> {
        val context = App.instance
        val alarmScheduler = AlarmScheduler(context)
        val queuedItems = mutableListOf<DownloadItem>()
        //download id, history item id
        //history item id if the existing item is already downloaded
        val existingItemIDs = mutableListOf<AlreadyExistsIDs>()

        if (items.any { it.playlistTitle.isEmpty() } && items.size > 1){
            items.forEachIndexed { index, it -> it.playlistTitle = "Various[${index+1}]" }
        }

        val downloadArchive = runCatching { File(FileUtil.getDownloadArchivePath(context)).useLines { it.toList() } }.getOrElse { listOf() }
            .map { it.split(" ")[1] }
        items.forEach {
            if (it.status != DownloadRepository.Status.Scheduled.toString())
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
                            existingItemIDs.add(AlreadyExistsIDs(it.id, null))
                        }
                    }
                    "url_type" -> {
                        val activeAndQueuedDownloads = withContext(Dispatchers.IO){
                            repository.getActiveAndQueuedDownloads()
                        }
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
                            it.id = id
                            alreadyExists = true
                            existingItemIDs.add(AlreadyExistsIDs(it.id, null))
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
                                existingItemIDs.add(AlreadyExistsIDs(id, existingHistoryItem.id))
                            }
                        }
                    }
                    "config" -> {
                        val currentCommand = infoUtil.buildYoutubeDLRequest(it)
                        val parsedCurrentCommand = infoUtil.parseYTDLRequestString(currentCommand)
                        val activeAndQueuedDownloads = withContext(Dispatchers.IO){
                            repository.getActiveAndQueuedDownloads()
                        }
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
                                existingItemIDs.add(AlreadyExistsIDs(id, existingHistoryItem.id))
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

        if (existingItemIDs.isNotEmpty()){
            alreadyExistsUiState.value = existingItemIDs.toList()
        }


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
            if (queuedItems.isNotEmpty()){
                if (!sharedPreferences.getBoolean("paused_downloads", false)) {
                    repository.startDownloadWorker(queuedItems, context)
                }

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

        return existingItemIDs
    }

}