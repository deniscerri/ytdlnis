package com.deniscerri.ytdlnis.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.deniscerri.ytdlnis.database.models.DownloadItem

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY status")
    fun getAllDownloads() : LiveData<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status='Active'")
    fun getActiveDownloads() : LiveData<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status='Queued'")
    fun getQueuedDownloads() : LiveData<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status='Cancelled' ORDER BY id DESC")
    fun getCancelledDownloads() : LiveData<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status='Error' ORDER BY id DESC")
    fun getErroredDownloads() : LiveData<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status='Processing' ORDER BY id DESC")
    fun getProcessingDownloads() : LiveData<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE id=:id LIMIT 1")
    fun getDownloadById(id: Long) : DownloadItem

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadItem) : Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<DownloadItem>) : List<Long>

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Query("DELETE FROM downloads WHERE id=:id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM downloads WHERE status='Cancelled'")
    suspend fun deleteCancelled()

    @Query("DELETE FROM downloads WHERE status='Error'")
    suspend fun deleteErrored()

    @Query("DELETE FROM downloads WHERE status='Queued'")
    suspend fun deleteQueued()

    @Query("DELETE FROM downloads WHERE status='Processing' AND id=:id")
    suspend fun deleteSingleProcessing(id: Long)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: DownloadItem)

    @Query("SELECT * FROM downloads ORDER BY id DESC LIMIT 1")
    fun getLatest() : DownloadItem

    @Query("UPDATE downloads SET status='Queued' WHERE status='Processing'")
    suspend fun queueAllProcessing()


    @Query("SELECT * FROM downloads WHERE url=:url AND (status='Error' OR status='Cancelled') LIMIT 1")
    fun checkIfErrorOrCancelled(url: String) : DownloadItem

    @Query("SELECT * FROM downloads WHERE url=:url AND format=:format AND (status='Error' OR status='Cancelled') LIMIT 1")
    fun getUnfinishedByURLAndFormat(url: String, format: String) : DownloadItem
}