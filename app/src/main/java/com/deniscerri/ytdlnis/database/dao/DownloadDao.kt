package com.deniscerri.ytdlnis.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.util.FileUtil

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads")
    fun getAllDownloads() : LiveData<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status='Active'")
    fun getActiveDownloads() : LiveData<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status='Queued'")
    fun getQueuedDownloads() : List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE workID=:workID")
    fun getDownloadByWorkId(workID: Int) : DownloadItem

    @Query("SELECT * FROM downloads WHERE id=:id LIMIT 1")
    fun getDownloadById(id: Long) : DownloadItem

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadItem) : Long

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(item: DownloadItem)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: DownloadItem)

    @Query("SELECT * FROM downloads ORDER BY id DESC LIMIT 1")
    fun getLatest() : DownloadItem
}