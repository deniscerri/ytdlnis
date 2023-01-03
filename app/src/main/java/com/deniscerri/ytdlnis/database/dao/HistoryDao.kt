package com.deniscerri.ytdlnis.database.dao

import androidx.room.*
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.util.FileUtil

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history WHERE title LIKE '%'||:query||'%' AND type LIKE '%'||:format||'%' AND website LIKE '%'||:site||'%' ORDER BY " +
            "CASE WHEN :sort = 'ASC' THEN id END ASC," +
            "CASE WHEN :sort = 'DESC' THEN id END DESC," +
            "CASE WHEN :sort = '' THEN id END DESC ")
    suspend fun getHistory(query : String, format : String, site : String, sort : String) : List<HistoryItem>

    @Query("SELECT * FROM history WHERE url=:url AND type=:type LIMIT 1")
    suspend fun getHistoryItemByURLAndType(url: String, type: String) : HistoryItem

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToHistory(results: ArrayList<HistoryItem>)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Query("DELETE FROM history where id=:id")
    suspend fun deleteItem(id: Int)

    @Transaction
    suspend fun clearDeletedHistory(){
        val fileUtil = FileUtil()
        val items : List<HistoryItem> = getHistory("","","","")
        items.forEach { item ->
            if (!fileUtil.exists(item.downloadPath)){
                clearHistoryItem(item, false)
            }
        }
    }

    @Transaction
    suspend fun clearDownloadingHistory(){
        val items : List<HistoryItem> = getHistory("","","","")
        items.forEach { item ->
            if (item.isQueuedDownload == 1){
                clearHistoryItem(item, false)
            }
        }
    }

    @Query("DELETE FROM history WHERE id > (SELECT MIN(h.id) FROM history h WHERE h.url = history.url AND h.type = history.type)")
    suspend fun clearDuplicates()

    @Query("UPDATE results SET downloadedAudio=0, downloadedVideo=0 WHERE downloadedAudio=1 OR downloadedVideo=1")
    suspend fun removeAllDownloadStatusesFromResults()

    @Query("UPDATE results SET downloadedAudio=0 WHERE downloadedAudio=1 AND url=:url")
    suspend fun removeAudioDownloadStatusFromOneResult(url: String)

    @Query("UPDATE results SET downloadedVideo=0 WHERE downloadedVideo=1 AND url=:url")
    suspend fun removeVideoDownloadStatusFromOneResult(url: String)

    @Transaction
    suspend fun clearDuplicateHistory(){
        clearDuplicates()
        removeAllDownloadStatusesFromResults()
    }


    @Transaction
    suspend fun clearHistoryItem(video: HistoryItem, delete_file : Boolean){
        if (delete_file){
            val fileUtil = FileUtil()
            fileUtil.deleteFile(video.downloadPath)
        }
        deleteItem(video.id)
        when(video.type){
            "audio" -> removeAudioDownloadStatusFromOneResult(video.url)
            "video" -> removeVideoDownloadStatusFromOneResult(video.url)
        }
    }

    @Update
    suspend fun updateHistoryItem(item: HistoryItem)

    @Transaction
    suspend fun checkDownloaded(url: String, type: String) : Boolean {
        val historyItem = getHistoryItemByURLAndType(url, type)
        val fileUtil = FileUtil()
        return fileUtil.exists(historyItem.downloadPath)
    }
}