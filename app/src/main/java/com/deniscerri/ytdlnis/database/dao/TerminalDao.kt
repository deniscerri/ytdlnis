package com.deniscerri.ytdlnis.database.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.DownloadItemSimple
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.TerminalItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TerminalDao {

    @Query("SELECT * FROM terminalDownloads ORDER BY id")
    fun getActiveTerminalDownloads() : List<TerminalItem>

    @Query("SELECT * FROM terminalDownloads ORDER BY id")
    fun getActiveTerminalDownloadsFlow() : Flow<List<TerminalItem>>

    @Query("SELECT COUNT(*) FROM terminalDownloads")
    fun getActiveTerminalsCount() : Int

    @Insert
    suspend fun insert(item: TerminalItem) : Long

    @Query("DELETE from terminalDownloads WHERE id=:id")
    suspend fun delete(id: Long)
}