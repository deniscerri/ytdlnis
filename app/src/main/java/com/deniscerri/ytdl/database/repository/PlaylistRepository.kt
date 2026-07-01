package com.deniscerri.ytdl.database.repository

import com.deniscerri.ytdl.database.dao.PlaylistDao
import com.deniscerri.ytdl.database.models.PlaylistEntryItem
import com.deniscerri.ytdl.database.models.PlaylistItem
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(private val dao: PlaylistDao) {
    val items: Flow<List<PlaylistItem>> = dao.getPlaylistsFlow()

    fun getAll(): List<PlaylistItem> = dao.getAllPlaylists()
    fun getPlaylist(id: Long): PlaylistItem? = dao.getPlaylist(id)
    fun getEntriesFlow(playlistId: Long): Flow<List<PlaylistEntryItem>> = dao.getEntriesFlow(playlistId)
    fun getEntries(playlistId: Long): List<PlaylistEntryItem> = dao.getEntries(playlistId)
    fun entryExists(playlistId: Long, url: String): Boolean = dao.entryExists(playlistId, url)
    fun getNextPosition(playlistId: Long): Int = dao.getNextPosition(playlistId)

    suspend fun insertPlaylist(item: PlaylistItem): Long = dao.insertPlaylist(item)
    suspend fun updatePlaylist(item: PlaylistItem) = dao.updatePlaylist(item)
    suspend fun deletePlaylist(item: PlaylistItem) = dao.deletePlaylist(item.id)
    suspend fun deleteAllPlaylists() = dao.deleteAllPlaylists()
    suspend fun insertEntry(item: PlaylistEntryItem): Long = dao.insertEntry(item)
    suspend fun updateEntry(item: PlaylistEntryItem) = dao.updateEntry(item)
    suspend fun updateEntries(items: List<PlaylistEntryItem>) = dao.updateEntries(items)
    suspend fun deleteEntry(item: PlaylistEntryItem) = dao.deleteEntry(item.id)
}
