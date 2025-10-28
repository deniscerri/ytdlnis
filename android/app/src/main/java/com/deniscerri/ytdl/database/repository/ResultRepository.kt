package com.deniscerri.ytdl.database.repository

import android.content.Context
import android.util.Patterns
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.database.dao.CommandTemplateDao
import com.deniscerri.ytdl.database.dao.ResultDao
import com.deniscerri.ytdl.database.models.ChapterItem
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.util.Extensions.getIDFromYoutubeURL
import com.deniscerri.ytdl.util.Extensions.isYoutubeChannelURL
import com.deniscerri.ytdl.util.Extensions.isYoutubeURL
import com.deniscerri.ytdl.util.Extensions.isYoutubeWatchVideosURL
import com.deniscerri.ytdl.util.Extensions.needsDataUpdating
import com.deniscerri.ytdl.util.extractors.GoogleApiUtil
import com.deniscerri.ytdl.util.extractors.newpipe.NewPipeUtil
import com.deniscerri.ytdl.util.extractors.ytdlp.YTDLPUtil
import com.deniscerri.ytdl.util.extractors.YoutubeApiUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ResultRepository(private val resultDao: ResultDao, private val commandTemplateDao: CommandTemplateDao, private val context: Context) {
    val YTDLNIS_SEARCH = "YTDLNIS_SEARCH"
    val allResults : Flow<List<ResultItem>> = resultDao.getResults()
    var itemCount = MutableStateFlow(-1)

    fun getFiltered(playlistName : String = "") : List<ResultItem> {
        return resultDao.getResultsWithPlaylistName(playlistName)
    }

    private val youtubeApiUtil = YoutubeApiUtil(context)
    private val ytdlpUtil = YTDLPUtil(context, commandTemplateDao)
    private var newPipeUtil = NewPipeUtil(context)
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    enum class SourceType {
        YOUTUBE_VIDEO,
        YOUTUBE_WATCHVIDEOS,
        YOUTUBE_PLAYLIST,
        YOUTUBE_CHANNEL,
        SEARCH_QUERY,
        YT_DLP
    }

    private fun isUsingNewPipeExtractorDataFetching() = sharedPreferences.getString("youtube_data_fetching_extractor", "NEWPIPE") == "NEWPIPE"

    suspend fun insert(it: ResultItem){
        resultDao.insert(it)
    }

    fun getFirstResult() : ResultItem{
        return resultDao.getFirstResult()
    }

    suspend fun getHomeRecommendations(){
        deleteAll()
        val category = sharedPreferences.getString("recommendations_home", "")
        val items = when(category) {
            "newpipe" -> newPipeUtil.getTrending()
            "yt_api" -> youtubeApiUtil.getTrending()
            "yt_dlp_watch_later" -> ytdlpUtil.getYoutubeWatchLater()
            "yt_dlp_recommendations" -> ytdlpUtil.getYoutubeRecommendations()
            "yt_dlp_liked" -> ytdlpUtil.getYoutubeLikedVideos()
            "yt_dlp_watch_history" -> ytdlpUtil.getYoutubeWatchHistory()
            "custom" -> {
                val customURL = sharedPreferences.getString("custom_home_recommendation_url", "")
                if (customURL.isNullOrBlank()) arrayListOf()
                else ytdlpUtil.getFromYTDL(customURL, resultsGenerated = {})
            }
            else -> arrayListOf()
        }

        itemCount.value = items.size
        resultDao.insertMultiple(items)
    }

    fun getSearchSuggestions(searchQuery: String) : ArrayList<String> {
        return GoogleApiUtil.getSearchSuggestions(searchQuery)
    }

    fun getStreamingUrlAndChapters(url: String) : Pair<List<String>, List<ChapterItem>?> {
//        val newPipeTrial = if (isUsingNewPipeExtractorDataFetching()) {
//            newPipeUtil.getStreamingUrlAndChapters(url)
//        }else {
//            Result.failure(Throwable())
//        }
//        if (newPipeTrial.isFailure){
//            val res = ytdlpUtil.getStreamingUrlAndChapters(url)
//            return res.getOrDefault(Pair(listOf(""), null))
//        }

        return ytdlpUtil.getStreamingUrlAndChapters(url)
            .getOrDefault(Pair(listOf(""), null))
    }

    suspend fun search(inputQuery: String, resetResults: Boolean, addToResults: Boolean) : List<ResultItem>{
        if (resetResults) deleteAll()
        val res = when(sharedPreferences.getString("search_engine", "ytsearch")) {
            "ytsearch" -> newPipeUtil.search(inputQuery)
            "ytsearchmusic" -> newPipeUtil.searchMusic(inputQuery)
            else -> Result.failure(Throwable())
        }

        val items = if (res.isSuccess) {
            res.getOrNull()!!
        }else{
            //fallback to yt-dlp
            ytdlpUtil.getFromYTDL(inputQuery, resultsGenerated = {})
        }

        itemCount.value = items.size
        if (addToResults){
            val ids = resultDao.insertMultiple(items)
            ids.forEachIndexed { index, id ->
                items[index].id = id
            }
        }
        return items
    }

    private suspend fun getYoutubeWatchVideos(inputQuery: String, resetResults: Boolean, addToResults: Boolean) : List<ResultItem> {
        if (resetResults) deleteAll()

        //throw YoutubeDLException("Youtube Watch Videos is not yet supported in data fetching. You can download it directly by clicking Continue Anyway or by Quick Downloading it!")
        val items = mutableListOf<ResultItem>()
        val newpipeExtractorResult = if (isUsingNewPipeExtractorDataFetching()) {
            newPipeUtil.getPlaylistData(inputQuery) {
                if (addToResults){
                    runBlocking {
                        val ids = resultDao.insertMultiple(it)
                        ids.forEachIndexed { index, id ->
                            it[index].id = id
                        }
                    }
                }
                items.addAll(it)
            }
        }else {
            Result.failure(Throwable())
        }

        val response = if (newpipeExtractorResult.isSuccess){
            newpipeExtractorResult.getOrElse { items }
        }else{
            val res = ytdlpUtil.getFromYTDL(inputQuery, resultsGenerated = {})
            if (addToResults) {
                val ids = resultDao.insertMultiple(res)
                ids.forEachIndexed { index, id ->
                    res[index].id = id
                }
            }
            res
        }

        itemCount.value = response.size
        return response
    }

    private suspend fun getYoutubeVideo(inputQuery: String, resetResults: Boolean, addToResults: Boolean) : List<ResultItem>{
        val theURL = inputQuery.replace("\\?list.*".toRegex(), "")
        val newpipeExtractorResult = if (isUsingNewPipeExtractorDataFetching()) {
            newPipeUtil.getVideoData(theURL)
        }else {
            Result.failure(Throwable())
        }

        val res = if (newpipeExtractorResult.isSuccess) {
            newpipeExtractorResult.getOrNull()!!
        }else{
            ytdlpUtil.getFromYTDL("https://youtu.be/${inputQuery.getIDFromYoutubeURL()}", resultsGenerated = {})
        }

        if (resetResults) {
            deleteAll()
            itemCount.value = res.size
        }else{
            res.filter { it.playlistTitle.isBlank() }.forEach { it.playlistTitle = YTDLNIS_SEARCH }
        }
        if (addToResults){
            val ids = resultDao.insertMultiple(res)
            ids.forEachIndexed { index, id ->
                res[index].id = id
            }
        }
        return res
    }

    private suspend fun getYoutubePlaylist(inputQuery: String, resetResults: Boolean, addToResults: Boolean) : List<ResultItem>{
        val id = inputQuery.split("list=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1].split("&").first()
        val playlistURL = "https://youtube.com/playlist?list=${id}"
        if (resetResults) deleteAll()
        val items = mutableListOf<ResultItem>()
        val ytExtractorResult = if (isUsingNewPipeExtractorDataFetching()){
            newPipeUtil.getPlaylistData(playlistURL) {
                if (addToResults){
                    runBlocking {
                        val ids = resultDao.insertMultiple(it)
                        ids.forEachIndexed { index, id ->
                            it[index].id = id
                        }
                    }
                }
                items.addAll(it)
            }
        }else {
            Result.failure(Throwable())
        }

        val finalResults = mutableListOf<ResultItem>()
        if (ytExtractorResult.isSuccess) {
            ytExtractorResult.getOrElse { items }.apply {
                finalResults.addAll(this)
                itemCount.value = this.size
            }
        }else {
            var itemCounts = 0
            ytdlpUtil.getFromYTDL(inputQuery) {
                if (addToResults) {
                    runBlocking {
                        val ids = resultDao.insertMultiple(it)
                        ids.forEachIndexed { index, id ->
                            it[index].id = id
                        }
                    }
                }
                finalResults.addAll(it)
                itemCounts+=it.size
                itemCount.value = itemCounts
            }
        }

        return finalResults
    }

    private suspend fun getYoutubeChannel(url: String, resetResults: Boolean, addToResults: Boolean) : List<ResultItem>{
        if (resetResults) deleteAll()
        val items = mutableListOf<ResultItem>()
        val ytExtractorResult = if (isUsingNewPipeExtractorDataFetching()) {
            newPipeUtil.getChannelData(url) {
                if (addToResults){
                    runBlocking {
                        val ids = resultDao.insertMultiple(it)
                        ids.forEachIndexed { index, id ->
                            it[index].id = id
                        }
                    }
                }
                items.addAll(it)
            }
        }else {
            Result.failure(Throwable())
        }

        val finalResults = mutableListOf<ResultItem>()
        if (ytExtractorResult.isSuccess) {
            ytExtractorResult.getOrElse { items }.apply {
                finalResults.addAll(this)
                itemCount.value = this.size
            }
        }else {
            var itemCounts = 0
            ytdlpUtil.getFromYTDL(url) {
                if (addToResults) {
                    runBlocking {
                        val ids = resultDao.insertMultiple(it)
                        ids.forEachIndexed { index, id ->
                            it[index].id = id
                        }
                    }
                }
                finalResults.addAll(it)
                itemCounts+=it.size
                itemCount.value = itemCounts
            }
        }

        return finalResults
    }

    private suspend fun getFromYTDLP(inputQuery: String, resetResults: Boolean, addToResults: Boolean, singleItem: Boolean = false) : List<ResultItem> {
        if (resetResults) {
            deleteAll()
        }

        var itemsCount = 0
        val itemsToReturn = mutableListOf<ResultItem>()

        ytdlpUtil.getFromYTDL(inputQuery, singleItem) { results ->
            if (resetResults) {
                itemsCount += results.size
                itemCount.value = itemsCount
            }
            results.filter { it.playlistTitle.isBlank() }.forEach { it.playlistTitle = YTDLNIS_SEARCH }
            if (addToResults) {
                runBlocking {
                    val ids = resultDao.insertMultiple(results)
                    ids.forEachIndexed { index, id ->
                        results[index].id = id
                    }
                }
            }
            itemsToReturn.addAll(results)
        }

        return itemsToReturn
    }

    fun getFormats(url: String, source : String? = null) : List<Format> {
        val formatSource = source ?: sharedPreferences.getString("formats_source", "yt-dlp")
        val res = if (url.isYoutubeURL()) {
            when(formatSource) {
                "newpipe" -> {
                    val tmpRes = NewPipeUtil(context).getFormats(url)
                    if (tmpRes.isFailure && source != null) {
                        Result.success(listOf())
                    }else{
                        tmpRes
                    }
                }
                else -> Result.failure(Throwable())
            }
        }else{
            Result.failure(Throwable())
        }

        return if (res.isSuccess){
            res.getOrNull()!!
        }else{
            ytdlpUtil.getFormats(url)
        }
    }

    suspend fun getFormatsMultiple(urls: List<String>, source: String? = null, progress: (progress: ResultViewModel.MultipleFormatProgress) -> Unit) : MutableList<MutableList<Format>> {
        val formatSource = source ?: sharedPreferences.getString("formats_source", "yt-dlp")
        val allYoutubeLinks = urls.all { it.isYoutubeURL() }

        val res = when(formatSource) {
            "newpipe" -> {
                if (!allYoutubeLinks) {
                    Result.failure(Throwable())
                }else{
                    val res = NewPipeUtil(context).getFormatsForAll(urls) {
                        progress(it)
                    }
                    res
                }

            }
            else -> {
                Result.failure(Throwable())
            }
        }

        if (res.isSuccess) {
            return res.getOrElse { mutableListOf() }
        }

        //last fallback
        val ytdlpRes = ytdlpUtil.getFormatsForAll(urls) {
            progress(it)
        }

        return ytdlpRes.getOrElse { mutableListOf() }
    }

    suspend fun delete(item: ResultItem){
        resultDao.delete(item.id)
    }

    suspend fun deleteByUrl(url: String) {
        resultDao.deleteByUrl(url)
    }

    suspend fun deleteAll(){
        itemCount.value = 0
        resultDao.deleteAll()
    }

    suspend fun update(item: ResultItem){
        resultDao.update(item)
    }

    fun getItemByID(id: Long) : ResultItem? {
        return resultDao.getResultByID(id)
    }

    fun getItemByURL(url: String): ResultItem? {
        return resultDao.getResultByURL(url)
    }

    fun getAllByURL(url: String) : List<ResultItem> {
        return resultDao.getAllByURL(url)
    }

    fun getAllByIDs(ids: List<Long>) : List<ResultItem> {
        return resultDao.getAllByIDs(ids)
    }

    fun updateID(id: Long, newID: Long) {
        resultDao.updateID(id, newID)
    }

    suspend fun getResultsFromSource(inputQuery: String, resetResults: Boolean, addToResults: Boolean = true, singleItem: Boolean = false) : List<ResultItem> {
        return when(getQueryType(inputQuery)){
            SourceType.YOUTUBE_VIDEO -> {
                getYoutubeVideo(inputQuery, resetResults, addToResults)
            }
            SourceType.YOUTUBE_WATCHVIDEOS -> {
                getYoutubeWatchVideos(inputQuery, resetResults, addToResults)
            }
            SourceType.YOUTUBE_PLAYLIST -> {
                if (singleItem){
                    getFromYTDLP(inputQuery, resetResults, addToResults, true)
                }else{
                    getYoutubePlaylist(inputQuery, resetResults, addToResults)
                }
            }
            SourceType.YOUTUBE_CHANNEL -> {
                if (singleItem) {
                    getFromYTDLP(inputQuery, resetResults, addToResults, true)
                }else{
                    getYoutubeChannel(inputQuery, resetResults, addToResults)
                }
            }
            SourceType.SEARCH_QUERY -> {
                search(inputQuery, resetResults, addToResults)
            }
            SourceType.YT_DLP -> {
                getFromYTDLP(inputQuery, resetResults, addToResults, singleItem)
            }
        }

    }

    private fun getQueryType(inputQuery: String) : SourceType {
        var type = SourceType.SEARCH_QUERY
        if (inputQuery.isYoutubeURL()) {
            type = SourceType.YOUTUBE_VIDEO
            if (inputQuery.contains("playlist?list=")) {
                type = SourceType.YOUTUBE_PLAYLIST
            }else if (inputQuery.isYoutubeChannelURL()) {
                type = SourceType.YOUTUBE_CHANNEL
            }else if (inputQuery.isYoutubeWatchVideosURL()) {
                type = SourceType.YOUTUBE_WATCHVIDEOS
            }
        } else if (Patterns.WEB_URL.matcher(inputQuery).matches()) {
            type = SourceType.YT_DLP
        }
        return type
    }

    suspend fun updateDownloadItem(
        downloadItem: DownloadItem
    ) : DownloadItem? {
        if (downloadItem.needsDataUpdating()){
            runCatching {
                val info = getResultsFromSource(downloadItem.url, resetResults = false, addToResults = false, singleItem = true).first()
                if (downloadItem.title.isEmpty()) downloadItem.title = info.title
                if (downloadItem.author.isEmpty()) downloadItem.author = info.author
                if (downloadItem.playlistTitle.isNotBlank() && downloadItem.playlistTitle != YTDLNIS_SEARCH) downloadItem.playlistTitle = info.playlistTitle
                downloadItem.duration = info.duration
                downloadItem.website = info.website
                if (downloadItem.thumb.isEmpty()) downloadItem.thumb = info.thumb
                return downloadItem
            }
        }
        return null
    }

}