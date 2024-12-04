package com.deniscerri.ytdl.database.dao

import android.util.Log
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.DownloadItemConfigureMultiple
import com.deniscerri.ytdl.database.models.DownloadItemSimple
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY status")
    fun getAllDownloads() : PagingSource<Int, DownloadItem>

    @Query("SELECT * FROM downloads WHERE status='Active'")
    fun getActiveDownloads() : Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status='Active' or status = 'Paused' ORDER BY CASE WHEN status = 'Active' THEN 0 ELSE 1 END")
    fun getActiveAndPausedDownloads() : Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status='Paused'")
    fun getPausedDownloads() : Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status='Paused'")
    fun getPausedDownloadsList() : List<DownloadItem>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM downloads WHERE status = 'Processing'")
    fun getProcessingDownloads() : Flow<List<DownloadItemConfigureMultiple>>

    @Query("SELECT COUNT(*) FROM downloads WHERE status in (:statuses)")
    fun getDownloadsCountFlow(statuses: List<String>) : Flow<Int>

    @Query("SELECT COUNT(*) FROM downloads WHERE status in (:status)")
    fun getDownloadsCountByStatusFlow(status : List<String>) : Flow<Int>

    @Query("SELECT COUNT(*) FROM downloads WHERE status in (:statuses)")
    fun getDownloadsCountByStatus(statuses : List<String>) : Int


    @Query("""
        SELECT DISTINCT type from downloads where status = 'Processing'
    """)
    fun getProcessingDownloadTypes() : List<String>

    @Query("""
        SELECT DISTINCT container from downloads where status = 'Processing'
    """)
    fun getProcessingDownloadContainers() : List<String>


    @Query("UPDATE downloads set status = 'Processing' WHERE id in (:ids)")
    suspend fun updateItemsToProcessing(ids: List<Long>)


    @Query("SELECT * FROM downloads WHERE status = 'Processing' ORDER BY id LIMIT 1")
    fun getFirstProcessingDownload() : DownloadItem


    @Query("SELECT * FROM downloads WHERE status = 'Processing'")
    fun getProcessingDownloadsList() : List<DownloadItem>

    @Query("UPDATE downloads set downloadPath=:path WHERE status ='Processing'")
    suspend fun updateProcessingDownloadPath(path: String)

    @Query("UPDATE downloads set container=:cont WHERE status ='Processing'")
    suspend fun updateProcessingContainer(cont: String)

    @Query("SELECT * FROM downloads WHERE status='Active'")
    fun getActiveDownloadsList() : List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE url=:url AND status='Processing'")
    fun getProcessingDownloadsByUrl(url: String) : List<DownloadItem>

    @Query("DELETE from downloads where status = 'Processing' AND url=:url")
    suspend fun deleteProcessingByUrl(url: String)

    @Query("SELECT * FROM downloads WHERE status in('Active','Queued', 'Scheduled')")
    fun getActiveAndQueuedDownloadsList() : List<DownloadItem>

    @Query("UPDATE downloads SET status='Queued', downloadStartTime = -1 where status in ('Paused')")
    suspend fun resetPausedToQueued()

    @Query("SELECT id FROM downloads WHERE status in('Active','Queued', 'Paused')")
    fun getActiveAndQueuedDownloadIDs() : List<Long>

    @Query("SELECT * FROM downloads WHERE status in('Active','Queued')")
    fun getActiveAndQueuedDownloads() : Flow<List<DownloadItem>>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM downloads WHERE status='Queued' ORDER BY id")
    fun getQueuedDownloads() : PagingSource<Int, DownloadItemSimple>

    @Query("SELECT format FROM downloads WHERE status='Queued'")
    fun getSelectedFormatFromQueued() : List<Format>

    @Query("""
        SELECT * FROM downloads 
        WHERE downloadStartTime <= :currentTime and status in ('Queued', 'Scheduled') 
        ORDER BY downloadStartTime, id
    """)
    fun getQueuedScheduledDownloadsUntil(currentTime: Long) : Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status='Queued' ORDER BY downloadStartTime, id")
    fun getQueuedDownloadsList() : List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE status='Scheduled' ORDER BY downloadStartTime, id")
    fun getScheduledDownloadsList() : List<DownloadItem>

    @Query("SELECT id FROM downloads WHERE status='Queued' ORDER BY downloadStartTime, id")
    fun getQueuedDownloadsListIDs() : List<Long>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM downloads WHERE status='Cancelled' ORDER BY id DESC")
    fun getCancelledDownloads() : PagingSource<Int, DownloadItemSimple>

    @Query("SELECT * FROM downloads WHERE status='Cancelled' ORDER BY id DESC")
    fun getCancelledDownloadsList() : List<DownloadItem>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM downloads WHERE status='Error' ORDER BY id DESC")
    fun getErroredDownloads() : PagingSource<Int, DownloadItemSimple>

    @Query("SELECT * FROM downloads WHERE status='Error' ORDER BY id DESC")
    fun getErroredDownloadsList() : List<DownloadItem>


    @Query("SELECT id from downloads WHERE status='Scheduled' ORDER BY downloadStartTime, id DESC")
    fun getScheduledDownloadIDs(): List<Long>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM downloads WHERE status='Saved' ORDER BY id DESC")
    fun getSavedDownloads() : PagingSource<Int, DownloadItemSimple>

    @Query("SELECT * FROM downloads WHERE status='Saved' ORDER BY id DESC")
    fun getSavedDownloadsList() : List<DownloadItem>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM downloads WHERE status='Scheduled' ORDER BY downloadStartTime, id DESC")
    fun getScheduledDownloads() : PagingSource<Int, DownloadItemSimple>

    @Query("SELECT * FROM downloads WHERE id=:id LIMIT 1")
    fun getDownloadById(id: Long) : DownloadItem

    @Query("SELECT * FROM downloads WHERE id IN (:ids)")
    fun getDownloadsByIds(ids: List<Long>) : List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE id IN (:ids)")
    fun getDownloadsByIdsFlow(ids: List<Long>) : Flow<List<DownloadItem>>

    @Query("SELECT id FROM downloads ORDER BY id DESC LIMIT 1")
    fun getLastDownloadId(): Long

    @Query("SELECT status FROM downloads WHERE id=:id")
    fun checkStatus(id: Long) : DownloadRepository.Status?

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

    @Query("DELETE FROM downloads WHERE status='Saved'")
    suspend fun deleteSaved()

    @Query("DELETE FROM downloads WHERE status='Processing'")
    suspend fun deleteProcessing()

    @Query("DELETE FROM downloads WHERE status='Duplicate'")
    suspend fun deleteWithDuplicateStatus()

    @Query("DELETE FROM downloads WHERE status='Scheduled'")
    suspend fun deleteScheduled()

    @Query("DELETE FROM downloads WHERE id in (:list)")
    suspend fun deleteAllWithIDs(list: List<Long>)

    @Query("UPDATE downloads SET status='Cancelled' WHERE status in('Queued','Active', 'Scheduled', 'Paused')")
    suspend fun cancelActiveQueued()

    @Query("DELETE FROM downloads WHERE status='Processing' AND id=:id")
    suspend fun deleteSingleProcessing(id: Long)

    @Upsert
    suspend fun update(item: DownloadItem) : Long

    @Transaction
    suspend fun updateAll(list: List<DownloadItem>) : List<DownloadItem> {
        val toReturn = mutableListOf<DownloadItem>()
        list.forEach {
            if (it.id > 0) {
                update(it)
            }else{
                it.id = insert(it)
            }
            toReturn.add(it)
        }

        return toReturn
    }

    @Query("UPDATE downloads set status=:status where id=:id")
    suspend fun setStatus(id: Long, status: String)

    @Query("UPDATE downloads set status=:status where id IN (:ids)")
    suspend fun setStatusMultiple(ids: List<Long>, status: String)

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

    @Query("Select id from downloads where status in (:status)")
    fun getIDsByStatus(status: List<String>) : List<Long>

    @Query("Select url from downloads where id in (:ids)")
    fun getURLsByID(ids: List<Long>) : List<String>

    @Query("UPDATE downloads SET downloadStartTime=0, status='Queued' where id in (:list)")
    suspend fun resetScheduleTimeForItems(list: List<Long>)

    @Query("UPDATE downloads SET downloadStartTime=0, status='Queued' WHERE status = 'Scheduled'")
    suspend fun resetScheduleTimeForAllScheduledItems()

    @Query("Update downloads SET status='Queued', downloadStartTime = 0 WHERE id in (:list)")
    suspend fun reQueueDownloadItems(list: List<Long>)

    @Query("Update downloads SET status='Saved' WHERE status='Processing'")
    suspend fun updateProcessingtoSavedStatus()

    @Transaction
    suspend fun putAtTopOfTheQueue(existingIDs: List<Long>){
        val downloads = getQueuedDownloadsListIDs()
        val newIDs = downloads.sortedBy { it }.take(existingIDs.size)

        resetScheduleTimeForItems(existingIDs)
        existingIDs.forEach { updateDownloadID(it, -it) }
        downloads.filter { !existingIDs.contains(it) }.toMutableList().apply {
            this.reverse()
            this.forEach {
                updateDownloadID(it, it + existingIDs.size)
            }
        }

        existingIDs.forEachIndexed { idx, it ->
            updateDownloadID(-it, newIDs[idx])
        }
    }

    @Transaction
    suspend fun putAtBottomOfTheQueue(existingIDs: List<Long>){
        val downloads = getQueuedDownloadsListIDs()
        val newIDs = downloads.sortedByDescending { it }.take(existingIDs.size)

        resetScheduleTimeForItems(existingIDs)
        existingIDs.forEach { updateDownloadID(it, -it) }
        downloads.filter { !existingIDs.contains(it) }.toMutableList().apply {
            this.reverse()
            this.forEach {
                updateDownloadID(it, it + existingIDs.size)
            }
        }

        existingIDs.forEachIndexed { idx, it ->
            updateDownloadID(-it, newIDs[idx])
        }
    }

    @Query("Update downloads set id=:newId where id=:id")
    suspend fun updateDownloadID(id: Long, newId: Long)

    @Query("SELECT id from downloads WHERE id > :item1 AND id < :item2 AND status in (:statuses) ORDER BY id DESC")
    fun getIDsBetweenTwoItems(item1: Long, item2: Long, statuses: List<String>) : List<Long>

    @Query("SELECT id from downloads WHERE id > :item1 AND id < :item2 AND status in('Scheduled') ORDER BY downloadStartTime, id")
    fun getScheduledIDsBetweenTwoItems(item1: Long, item2: Long) : List<Long>


    @Query("UPDATE downloads set incognito=:incognito WHERE status='Processing'")
    suspend fun updateProcessingIncognito(incognito: Boolean)

    @Query("SELECT COUNT(id) FROM downloads WHERE status='Processing' AND incognito='1'")
    fun getProcessingAsIncognitoCount(): Int
}