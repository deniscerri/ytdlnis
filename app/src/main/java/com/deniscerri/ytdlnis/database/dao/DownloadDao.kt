package com.deniscerri.ytdlnis.database.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.DownloadItemSimple
import com.deniscerri.ytdlnis.database.models.Format
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY status")
    fun getAllDownloads() : PagingSource<Int, DownloadItem>

    @Query("SELECT * FROM downloads WHERE status='Active' or status='Paused'")
    fun getActiveDownloads() : Flow<List<DownloadItem>>

    @Query("SELECT COUNT(*) FROM downloads WHERE status='Active' or status='Paused'")
    fun getActiveDownloadsCountFlow() : Flow<Int>

    @Query("SELECT COUNT(*) FROM downloads WHERE status in (:status)")
    fun getDownloadsCountByStatusFlow(status : List<String>) : Flow<Int>

    @Query("SELECT COUNT(*) FROM downloads WHERE status in (:status)")
    fun getDownloadsCountByStatus(status : List<String>) : Int

    @Query("SELECT * FROM downloads WHERE status='Active' or status='Paused'")
    fun getActiveAndPausedDownloadsList() : List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE status='Active'")
    fun getActiveDownloadsList() : List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE status='Active' or status='Queued' or status='QueuedPaused'  or status='Paused'")
    fun getActiveAndQueuedDownloadsList() : List<DownloadItem>

    @Query("SELECT id FROM downloads WHERE status='Active' or status='Queued' or status='QueuedPaused'  or status='Paused'")
    fun getActiveAndQueuedDownloadIDs() : List<Long>

    @Query("SELECT * FROM downloads WHERE status='Active' or status='Queued' or status='QueuedPaused'  or status='Paused'")
    fun getActiveAndQueuedDownloads() : Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status='Queued' or status='QueuedPaused' ORDER BY downloadStartTime, id")
    fun getQueuedDownloads() : PagingSource<Int, DownloadItemSimple>

    @Query("SELECT format FROM downloads WHERE status='Queued' or status='QueuedPaused'")
    fun getSelectedFormatFromQueued() : List<Format>

    @Query("SELECT * FROM downloads WHERE downloadStartTime <= :currentTime and status='Queued' ORDER BY downloadStartTime, id LIMIT 20")
    fun getQueuedDownloadsThatAreNotScheduledChunked(currentTime: Long) : Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status='Queued' or status='QueuedPaused' or status='Paused' ORDER BY downloadStartTime, id")
    fun getQueuedAndPausedDownloads() : Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status='Queued' or status='QueuedPaused' ORDER BY downloadStartTime, id")
    fun getQueuedDownloadsList() : List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE status='Cancelled' ORDER BY id DESC")
    fun getCancelledDownloads() : PagingSource<Int, DownloadItemSimple>

    @Query("SELECT * FROM downloads WHERE status='Cancelled' ORDER BY id DESC")
    fun getCancelledDownloadsList() : List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE status LIKE '%Paused%'")
    fun getPausedDownloadsList() : List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE status='Error' ORDER BY id DESC")
    fun getErroredDownloads() : PagingSource<Int, DownloadItemSimple>

    @Query("SELECT * FROM downloads WHERE status='Error' ORDER BY id DESC")
    fun getErroredDownloadsList() : List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE status='Saved' ORDER BY id DESC")
    fun getSavedDownloads() : PagingSource<Int, DownloadItemSimple>

    @Query("SELECT * FROM downloads WHERE status='Saved' ORDER BY id DESC")
    fun getSavedDownloadsList() : List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE id=:id LIMIT 1")
    fun getDownloadById(id: Long) : DownloadItem

    @Query("SELECT id FROM downloads ORDER BY id DESC LIMIT 1")
    fun getLastDownloadId(): Long

    @Query("SELECT status FROM downloads WHERE id=:id")
    fun checkStatus(id: Long) : String

    @Query("UPDATE downloads " +
            "SET status = CASE " +
            "    WHEN status = 'Active' THEN 'Paused' " +
            "    WHEN status = 'Queued' THEN 'QueuedPaused' " +
            "    ELSE status " +
            "    END;")
    fun pauseActiveAndQueued()

    @Query("UPDATE downloads " +
            "SET status = CASE " +
            "    WHEN status = 'Paused' THEN 'Active' " +
            "    WHEN status = 'QueuedPaused' THEN 'Queued' " +
            "    ELSE status " +
            "    END;")
    fun unPauseActiveAndQueued()

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

    @Query("DELETE FROM downloads WHERE status='Saved'")
    suspend fun deleteSaved()

    @Query("DELETE FROM downloads WHERE id in (:list)")
    suspend fun deleteAllWithIDs(list: List<Long>)

    @Query("UPDATE downloads SET status='Cancelled' WHERE status='Queued' or status='QueuedPaused' or status='Active' or status='Paused'")
    suspend fun cancelActiveQueued()

    @Query("DELETE FROM downloads WHERE status='Processing' AND id=:id")
    suspend fun deleteSingleProcessing(id: Long)

    @Upsert
    suspend fun update(item: DownloadItem)

    @Query("UPDATE downloads SET logID=null")
    fun removeAllLogID()

    @Query("UPDATE downloads SET logID=null WHERE logID=:logID")
    fun removeLogID(logID: Long)

    @Upsert
    fun updateMultiple(items :List<DownloadItem>)

    @Query("SELECT * FROM downloads ORDER BY id DESC LIMIT 1")
    fun getLatest() : DownloadItem

    @Query("UPDATE downloads SET status='Queued' WHERE status='Processing'")
    suspend fun queueAllProcessing()


    @Query("SELECT * FROM downloads WHERE url=:url AND (status='Error' OR status='Cancelled') LIMIT 1")
    fun checkIfErrorOrCancelled(url: String) : DownloadItem

    @Query("SELECT * FROM downloads WHERE url=:url AND format=:format AND (status='Error' OR status='Cancelled') LIMIT 1")
    fun getUnfinishedByURLAndFormat(url: String, format: String) : DownloadItem


    @Query("SELECT COUNT(downloadStartTime) = 0 from downloads WHERE id in (:items) AND " +
            "CASE :inverted " +
            "WHEN :inverted = 'false' THEN downloadStartTime > :currentStartTime " +
            "WHEN :inverted = 'true' THEN downloadStartTime < :currentStartTime ELSE downloadStartTime < -1 END")
    fun checkAllQueuedItemsAreScheduledAfterNow(items: List<Long>, inverted: String, currentStartTime: Long) : Boolean


    @Query("Select id from downloads where id not in (:list) and status in (:status)")
    fun getDownloadIDsNotPresentInList(list: List<Long>, status: List<String>) : List<Long>

    @Query("UPDATE downloads SET downloadStartTime=0 where id in (:list)")
    fun resetScheduleTimeForItems(list: List<Long>)

    @Query("Update downloads SET status='Queued', downloadStartTime = 0 WHERE id in (:list)")
    fun reQueueDownloadItems(list: List<Long>)

    @Transaction
    fun putAtTopOfTheQueue(id: Long){
        val downloads = getQueuedDownloadsList()
        val newID = downloads.first().id

        updateDownloadID(id, -id)
        resetScheduleTimeForItems(listOf(-id))

        downloads.reversed().dropWhile { it.id == id }.forEach {
            updateDownloadID(it.id, it.id + 1)
        }
        updateDownloadID(-id, newID)
    }

    @Query("Update downloads set id=:newId where id=:id")
    fun updateDownloadID(id: Long, newId: Long)
}