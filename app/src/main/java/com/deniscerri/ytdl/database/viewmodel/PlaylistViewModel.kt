package com.deniscerri.ytdl.database.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.PlaylistEntryItem
import com.deniscerri.ytdl.database.models.PlaylistItem
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.repository.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PlaylistViewModel(private val application: Application) : AndroidViewModel(application) {
    private val repository: PlaylistRepository
    val items: LiveData<List<PlaylistItem>>

    init {
        repository = PlaylistRepository(DBManager.getInstance(application).playlistDao)
        items = repository.items.asLiveData()
    }

    fun getAll(): List<PlaylistItem> = repository.getAll()
    fun getPlaylist(id: Long): PlaylistItem? = repository.getPlaylist(id)
    fun getEntriesFlow(playlistId: Long): Flow<List<PlaylistEntryItem>> = repository.getEntriesFlow(playlistId)
    fun getEntries(playlistId: Long): List<PlaylistEntryItem> = repository.getEntries(playlistId)

    suspend fun insertPlaylist(item: PlaylistItem): Long = repository.insertPlaylist(item)
    suspend fun updatePlaylist(item: PlaylistItem) = repository.updatePlaylist(item)
    suspend fun deletePlaylist(item: PlaylistItem) = repository.deletePlaylist(item)
    suspend fun deleteAllPlaylists() = repository.deleteAllPlaylists()
    suspend fun deleteEntry(item: PlaylistEntryItem) = repository.deleteEntry(item)

    suspend fun addEntry(playlist: PlaylistItem, result: ResultItem): Boolean = withContext(Dispatchers.IO) {
        if (repository.entryExists(playlist.id, result.url)) return@withContext false
        val entry = PlaylistEntryItem(
            playlistId = playlist.id,
            url = result.url,
            title = result.title.ifBlank { result.url },
            author = result.author,
            thumb = result.thumb,
            duration = result.duration,
            position = repository.getNextPosition(playlist.id)
        )
        val id = repository.insertEntry(entry)
        if (id > 0 && playlist.thumb.isBlank() && result.thumb.isNotBlank()) {
            repository.updatePlaylist(playlist.copy(thumb = result.thumb))
        }
        id > 0
    }

    suspend fun reorder(playlistId: Long, fromPosition: Int, toPosition: Int) = withContext(Dispatchers.IO) {
        val entries = repository.getEntries(playlistId).toMutableList()
        if (fromPosition !in entries.indices || toPosition !in entries.indices) return@withContext
        val moved = entries.removeAt(fromPosition)
        entries.add(toPosition, moved)
        repository.updateEntries(entries.mapIndexed { index, item -> item.copy(position = index) })
    }

    fun toResultItem(entry: PlaylistEntryItem): ResultItem {
        return ResultItem(
            id = entry.id,
            url = entry.url,
            title = entry.title,
            author = entry.author,
            duration = entry.duration,
            thumb = entry.thumb,
            website = "",
            playlistTitle = "",
            formats = emptyList(),
            urls = "",
            chapters = arrayListOf(),
            playlistURL = "",
            playlistIndex = null,
            creationTime = System.currentTimeMillis()
        )
    }

    fun getCursor(playlistId: Long): Int =
        application.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(cursorKey(playlistId), 0)

    fun setCursor(playlistId: Long, index: Int) {
        application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(cursorKey(playlistId), index).apply()
    }

    suspend fun prefetchWindow(
        playlist: PlaylistItem,
        entries: List<PlaylistEntryItem>,
        cursor: Int,
        channelsViewModel: ChannelsViewModel,
        downloadViewModel: DownloadViewModel
    ) = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext
        val count = PreferenceManager.getDefaultSharedPreferences(application)
            .getInt("playlist_prefetch_count", 3)
        val start = cursor.coerceIn(0, entries.lastIndex)
        val end = (start + count).coerceAtMost(entries.lastIndex)
        val window = entries.subList(start, end + 1)
        val results = window.map(::toResultItem)
        val downloaded = channelsViewModel.getDownloadedPaths(results)
        val running = DBManager.getInstance(application).downloadDao.getRunningDownloadUrlsList().toSet()
        val downloads = results
            .filter { downloaded[it.url] == null && !running.contains(it.url) }
            .map { downloadViewModel.createDownloadItemFromResult(it, givenType = playlist.type) }
        if (downloads.isNotEmpty()) {
            downloadViewModel.queueDownloads(downloads, ignoreDuplicates = true)
        }
    }

    private fun cursorKey(playlistId: Long) = "playlist_${playlistId}_cursor"

    companion object {
        private const val PREFS = "playlist_playback_cursor"
    }
}
