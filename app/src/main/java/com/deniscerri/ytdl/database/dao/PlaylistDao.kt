package com.deniscerri.ytdl.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.deniscerri.ytdl.database.models.PlaylistEntryItem
import com.deniscerri.ytdl.database.models.PlaylistItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getPlaylistsFlow(): Flow<List<PlaylistItem>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): List<PlaylistItem>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    fun getPlaylist(id: Long): PlaylistItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(item: PlaylistItem): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updatePlaylist(item: PlaylistItem)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position ASC, id ASC")
    fun getEntriesFlow(playlistId: Long): Flow<List<PlaylistEntryItem>>

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position ASC, id ASC")
    fun getEntries(playlistId: Long): List<PlaylistEntryItem>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_entries WHERE playlistId = :playlistId")
    fun getNextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(item: PlaylistEntryItem): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateEntry(item: PlaylistEntryItem)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateEntries(items: List<PlaylistEntryItem>)

    @Query("DELETE FROM playlist_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_entries WHERE playlistId = :playlistId AND url = :url)")
    fun entryExists(playlistId: Long, url: String): Boolean
}
