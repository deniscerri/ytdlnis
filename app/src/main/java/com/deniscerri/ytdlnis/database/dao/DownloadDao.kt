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

    @Query("SELECT * FROM downloads WHERE status in ('Active', 'ActivePaused', 'PausedReQueued')")
    fun getActiveDownloads() : Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status = 'Processing'")
    fun getProcessingDownloads() : Flow<List<DownloadItem>>

    @Query("SELECT COUNT(*) FROM downloads WHERE status in ('Active', 'ActivePaused', 'PausedReQueued')")
    fun getActiveDownloadsCountFlow() : Flow<Int>

    @Query("SELECT COUNT(*) FROM downloads WHERE status in (:status)")
    fun getDownloadsCountByStatusFlow(status : List<String>) : Flow<Int>

    @Query("SELECT COUNT(*) FROM downloads WHERE status in (:status)")
    fun getDownloadsCountByStatus(status : List<String>) : Int


    @Query("""
        SELECT COUNT(*) FROM downloads WHERE status = 'Processing'
        UNION
        SELECT COUNT(*) FROM downloads WHERE status ='Processing' AND type =
            (SELECT type from downloads WHERE status = 'Processing' ORDER BY id LIMIT 1)
    """)
    fun getProcessingDownloadsCountByType() : List<Int>


    @Query("UPDATE downloads set status = 'Processing' WHERE id in (:ids)")
    suspend fun updateItemsToProcessing(ids: List<Long>)


    @Query("SELECT * FROM downloads WHERE status = 'Processing' ORDER BY id LIMIT 1")
    fun getFirstProcessingDownload() : DownloadItem

    @Query("SELECT * FROM downloads WHERE status in('Active','ActivePaused','PausedReQueued')")
    fun getActiveAndPausedDownloadsList() : List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE status = 'Processing'")
    fun getProcessingDownloadsList() : List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE status='Active'")
    fun getActiveDownloadsList() : List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE status in('Active','Queued','QueuedPaused','ActivePaused','PausedReQueued')")
    fun getActiveAndQueuedDownloadsList() : List<DownloadItem>

    @Query("SELECT id FROM downloads WHERE status in('Active','Queued','QueuedPaused','ActivePaused','PausedReQueued')")
    fun getActiveAndQueuedDownloadIDs() : List<Long>

    @Query("SELECT * FROM downloads WHERE status in('Active','Queued','QueuedPaused','ActivePaused','PausedReQueued')")
    fun getActiveAndQueuedDownloads() : Flow<List<DownloadItem>>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM downloads WHERE status in('Queued','QueuedPaused') ORDER BY downloadStartTime, id")
    fun getQueuedDownloads() : PagingSource<Int, DownloadItemSimple>

    @Query("SELECT format FROM downloads WHERE status in('Queued','QueuedPaused')")
    fun getSelectedFormatFromQueued() : List<Format>

    @Query("SELECT * FROM downloads WHERE downloadStartTime <= :currentTime and status in ('Queued', 'PausedReQueued') ORDER BY downloadStartTime, id LIMIT 20")
    fun getQueuedDownloadsThatAreNotScheduledChunked(currentTime: Long) : Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status in ('Queued','QueuedPaused','ActivePaused','PausedReQueued') ORDER BY downloadStartTime, id")
    fun getQueuedAndPausedDownloads() : Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status in('Queued','QueuedPaused') ORDER BY downloadStartTime, id")
    fun getQueuedDownloadsList() : List<DownloadItem>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM downloads WHERE status='Cancelled' ORDER BY id DESC")
    fun getCancelledDownloads() : PagingSource<Int, DownloadItemSimple>

    @Query("SELECT * FROM downloads WHERE status='Cancelled' ORDER BY id DESC")
    fun getCancelledDownloadsList() : List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE status LIKE '%Paused%'")
    fun getPausedDownloadsList() : List<DownloadItem>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM downloads WHERE status='Error' ORDER BY id DESC")
    fun getErroredDownloads() : PagingSource<Int, DownloadItemSimple>

    @Query("SELECT * FROM downloads WHERE status='Error' ORDER BY id DESC")
    fun getErroredDownloadsList() : List<DownloadItem>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM downloads WHERE status='Saved' ORDER BY id DESC")
    fun getSavedDownloads() : PagingSource<Int, DownloadItemSimple>

    @Query("SELECT * FROM downloads WHERE status='Saved' ORDER BY id DESC")
    fun getSavedDownloadsList() : List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE id=:id LIMIT 1")
    fun getDownloadById(id: Long) : DownloadItem

    @Query("SELECT * FROM downloads WHERE id IN (:ids)")
    fun getDownloadsByIds(ids: List<Long>) : List<DownloadItem>

    @Query("SELECT id FROM downloads ORDER BY id DESC LIMIT 1")
    fun getLastDownloadId(): Long

    @Query("SELECT status FROM downloads WHERE id=:id")
    fun checkStatus(id: Long) : String

    @Query("UPDATE downloads " +
            "SET status = CASE " +
            "    WHEN status = 'Active' THEN 'ActivePaused' " +
            "    WHEN status = 'Queued' THEN 'QueuedPaused' " +
            "    ELSE status " +
            "    END;")
    fun pauseActiveAndQueued()

    @Query("UPDATE downloads " +
            "SET status = CASE " +
            "    WHEN status = 'ActivePaused' THEN 'Active' " +
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

    @Query("DELETE FROM downloads WHERE status='Processing'")
    suspend fun deleteProcessing()

    @Query("DELETE FROM downloads WHERE id in (:list)")
    suspend fun deleteAllWithIDs(list: List<Long>)

    @Query("UPDATE downloads SET status='Cancelled' WHERE status in('Queued','QueuedPaused','Active','ActivePaused','PausedReQueued')")
    suspend fun cancelActiveQueued()

    @Query("DELETE FROM downloads WHERE status='Processing' AND id=:id")
    suspend fun deleteSingleProcessing(id: Long)

    @Upsert
    suspend fun update(item: DownloadItem)

    @Update
    suspend fun updateWithoutUpsert(item: DownloadItem)

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

    @Query("Select url from downloads where status in (:status)")
    fun getURLsByStatus(status: List<String>) : List<String>

    @Query("UPDATE downloads SET downloadStartTime=0 where id in (:list)")
    fun resetScheduleTimeForItems(list: List<Long>)

    @Query("Update downloads SET status='Queued', downloadStartTime = 0 WHERE id in (:list)")
    fun reQueueDownloadItems(list: List<Long>)

    @Query("Update downloads SET status='Saved' WHERE status='Processing'")
    fun updateProcessingtoSavedStatus()

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