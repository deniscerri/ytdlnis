package com.deniscerri.ytdl.database.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.ChannelItem
import com.deniscerri.ytdl.database.models.ChannelVideoCache
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.repository.ChannelsRepository
import com.deniscerri.ytdl.database.repository.ResultRepository
import com.deniscerri.ytdl.util.Extensions.getIDFromYoutubeURL
import com.deniscerri.ytdl.util.FileUtil
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class ChannelsViewModel(private val application: Application) : AndroidViewModel(application) {
    private val channelsRepository: ChannelsRepository
    private val resultRepository: ResultRepository
    private val gson = Gson()
    val items: LiveData<List<ChannelItem>>

    /** Emits the set of urls that currently have an in-progress (queued/active/…) download. */
    val runningDownloadUrls: Flow<List<String>> by lazy {
        DBManager.getInstance(application).downloadDao.getRunningDownloadUrls().distinctUntilChanged()
    }

    init {
        val dbManager = DBManager.getInstance(application)
        val channelsDao = dbManager.channelsDao
        val resultDao = dbManager.resultDao
        val commandTemplateDao = dbManager.commandTemplateDao

        channelsRepository = ChannelsRepository(channelsDao)
        resultRepository = ResultRepository(resultDao, commandTemplateDao, application.applicationContext)
        items = channelsRepository.items.asLiveData()
    }

    fun getAll(): List<ChannelItem> {
        return channelsRepository.getAll()
    }

    fun getByURL(url: String) : ChannelItem {
        return channelsRepository.getByURL(url)
    }

    fun getByID(id: Long) : ChannelItem {
        return channelsRepository.getByID(id)
    }

    suspend fun insert(item: ChannelItem) : Long {
        return channelsRepository.insert(item)
    }

    fun delete(item: ChannelItem) = viewModelScope.launch(Dispatchers.IO) {
        // Drop the cached uploads too, otherwise re-adding this channel resurrects stale videos.
        DBManager.getInstance(application).channelVideoCacheDao.delete(item.url)
        channelsRepository.delete(item)
    }

    fun deleteAll() = viewModelScope.launch(Dispatchers.IO) {
        DBManager.getInstance(application).channelVideoCacheDao.deleteAll()
        channelsRepository.deleteAll()
    }

    suspend fun update(item: ChannelItem) {
        channelsRepository.update(item)
    }

    /**
     * Returns the cached videos for a channel (instant, no network). Empty if nothing is cached.
     */
    fun getCachedChannelVideos(url: String): List<ResultItem> {
        val cache = DBManager.getInstance(application).channelVideoCacheDao.get(url) ?: return emptyList()
        return runCatching {
            gson.fromJson(cache.videosJson, Array<ResultItem>::class.java).toList().withStableIds()
        }.getOrDefault(emptyList())
    }

    /**
     * Channel videos are fetched with addToResults = false, so NewPipe leaves their db id at 0 and
     * every row would share the same DiffUtil identity in [com.deniscerri.ytdl.ui.adapter.HomeAdapter].
     * Give each a stable id derived from its url so hiding, refreshing and list/grid toggles update
     * the right rows. The id is position-independent so cached and freshly fetched lists agree.
     */
    private fun List<ResultItem>.withStableIds(): List<ResultItem> {
        return onEach { it.id = it.url.hashCode().toLong() }
    }

    /**
     * Fetches a channel's videos over the network and caches them for the next visit.
     */
    suspend fun getChannelVideos(url: String): List<ResultItem> {
        val videos = resultRepository.getResultsFromSource(url, resetResults = false, addToResults = false, singleItem = false)
            .onEach {
                // Download each channel video as a standalone item. Items fetched from a channel
                // carry the channel as playlistURL/playlistTitle; leaving those set makes
                // YTDLPUtil.buildYTDLRequest run yt-dlp against the whole channel with a
                // "--match-filter id~='<id>'", which skips every entry and downloads nothing.
                it.playlistURL = ""
                it.playlistTitle = ""
                it.playlistIndex = null
            }
            .withStableIds()
        // This point is only reached on a successful fetch (getResultsFromSource throws on
        // failure), so an empty result means the channel genuinely has no visible uploads — clear
        // the stale cache instead of leaving previously cached videos to reappear next visit.
        runCatching {
            val cacheDao = DBManager.getInstance(application).channelVideoCacheDao
            if (videos.isNotEmpty()) {
                cacheDao.upsert(ChannelVideoCache(url, gson.toJson(videos), System.currentTimeMillis()))
            } else {
                cacheDao.delete(url)
            }
        }
        return videos
    }

    suspend fun searchChannels(query: String): List<ChannelItem> {
        return resultRepository.searchChannels(query)
    }

    /** Canonical channel url for a pasted link, or null if it isn't a supported channel url. */
    fun normalizeChannelUrl(url: String): String? {
        return resultRepository.normalizeChannelUrl(url)
    }

    /** The urls (from the given items) that currently have an in-progress download. */
    fun getRunningDownloadUrls(items: List<ResultItem>): Set<String> {
        val running = DBManager.getInstance(application).downloadDao.getRunningDownloadUrlsList().toSet()
        return items.map { it.url }.filter { running.contains(it) }.toSet()
    }

    /**
     * For the given channel videos, returns a map of result url -> local downloaded file path,
     * containing only items that have a completed download whose file still exists on disk.
     * Matching is by YouTube video id so it survives minor url differences.
     */
    fun getDownloadedPaths(items: List<ResultItem>): Map<String, String> {
        val historyDao = DBManager.getInstance(application).historyDao
        val idToPath = mutableMapOf<String, String>()
        historyDao.getAllHistoryList().forEach { history ->
            val id = history.url.getIDFromYoutubeURL() ?: return@forEach
            if (idToPath.containsKey(id)) return@forEach
            val existingFile = history.downloadPath.firstOrNull { it.isNotBlank() && FileUtil.existsAccessible(it) }
            if (existingFile != null) idToPath[id] = existingFile
        }

        val result = mutableMapOf<String, String>()
        items.forEach { item ->
            val id = item.url.getIDFromYoutubeURL() ?: return@forEach
            idToPath[id]?.let { result[item.url] = it }
        }
        return result
    }
}
