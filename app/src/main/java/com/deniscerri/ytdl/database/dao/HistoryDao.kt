package com.deniscerri.ytdl.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history WHERE (title LIKE '%'||:query||'%' OR author LIKE '%'||:query||'%') AND type LIKE '%'||:type||'%' AND website LIKE '%'||:site||'%' ORDER BY " +
            "CASE WHEN :sort = 'ASC' THEN id END ASC," +
            "CASE WHEN :sort = 'DESC' THEN id END DESC," +
            "CASE WHEN :sort = '' THEN id END DESC ")
    fun getHistorySortedByIDPaginated(query : String, type : String, site : String, sort : String) : PagingSource<Int, HistoryItem>

    @Query("SELECT * FROM history WHERE (title LIKE '%'||:query||'%' OR author LIKE '%'||:query||'%') AND type LIKE '%'||:type||'%' AND website LIKE '%'||:site||'%' ORDER BY " +
            "CASE WHEN :sort = 'ASC' THEN title END ASC," +
            "CASE WHEN :sort = 'DESC' THEN title END DESC," +
            "CASE WHEN :sort = '' THEN title END DESC ")
    fun getHistorySortedByTitlePaginated(query : String, type : String, site : String, sort : String) : PagingSource<Int, HistoryItem>

    @Query("SELECT * FROM history WHERE (title LIKE '%'||:query||'%' OR author LIKE '%'||:query||'%') AND type LIKE '%'||:type||'%' AND website LIKE '%'||:site||'%' ORDER BY " +
            "CASE WHEN :sort = 'ASC' THEN author END ASC," +
            "CASE WHEN :sort = 'DESC' THEN author END DESC," +
            "CASE WHEN :sort = '' THEN author END DESC ")
    fun getHistorySortedByAuthorPaginated(query : String, type : String, site : String, sort : String) : PagingSource<Int, HistoryItem>

    @Query("SELECT * FROM history WHERE (title LIKE '%'||:query||'%' OR author LIKE '%'||:query||'%') AND type LIKE '%'||:type||'%' AND website LIKE '%'||:site||'%' ORDER BY " +
            "CASE WHEN :sort = 'ASC' THEN filesize END ASC," +
            "CASE WHEN :sort = 'DESC' THEN filesize END DESC," +
            "CASE WHEN :sort = '' THEN filesize END DESC ")
    fun getHistorySortedByFilesizePaginated(query : String, type : String, site : String, sort : String) : PagingSource<Int, HistoryItem>

    @Query("SELECT id, downloadPath FROM history WHERE (title LIKE '%'||:query||'%' OR author LIKE '%'||:query||'%') AND type LIKE '%'||:type||'%' AND website LIKE '%'||:site||'%' ORDER BY " +
            "CASE WHEN :sort = 'ASC' THEN id END ASC," +
            "CASE WHEN :sort = 'DESC' THEN id END DESC," +
            "CASE WHEN :sort = '' THEN id END DESC ")
    fun getHistoryIDsSortedByID(query : String, type : String, site : String, sort : String) : List<HistoryRepository.HistoryIDsAndPaths>

    @Query("SELECT id, downloadPath FROM history WHERE (title LIKE '%'||:query||'%' OR author LIKE '%'||:query||'%') AND type LIKE '%'||:type||'%' AND website LIKE '%'||:site||'%' ORDER BY " +
            "CASE WHEN :sort = 'ASC' THEN title END ASC," +
            "CASE WHEN :sort = 'DESC' THEN title END DESC," +
            "CASE WHEN :sort = '' THEN title END DESC ")
    fun getHistoryIDsSortedByTitle(query : String, type : String, site : String, sort : String) : List<HistoryRepository.HistoryIDsAndPaths>

    @Query("SELECT id, downloadPath FROM history WHERE (title LIKE '%'||:query||'%' OR author LIKE '%'||:query||'%') AND type LIKE '%'||:type||'%' AND website LIKE '%'||:site||'%' ORDER BY " +
            "CASE WHEN :sort = 'ASC' THEN author END ASC," +
            "CASE WHEN :sort = 'DESC' THEN author END DESC," +
            "CASE WHEN :sort = '' THEN author END DESC ")
    fun getHistoryIDsSortedByAuthor(query : String, type : String, site : String, sort : String) : List<HistoryRepository.HistoryIDsAndPaths>

    @Query("SELECT id, downloadPath FROM history WHERE (title LIKE '%'||:query||'%' OR author LIKE '%'||:query||'%') AND type LIKE '%'||:type||'%' AND website LIKE '%'||:site||'%' ORDER BY " +
            "CASE WHEN :sort = 'ASC' THEN filesize END ASC," +
            "CASE WHEN :sort = 'DESC' THEN filesize END DESC," +
            "CASE WHEN :sort = '' THEN filesize END DESC ")
    fun getHistoryIDsSortedByFilesize(query : String, type : String, site : String, sort : String) : List<HistoryRepository.HistoryIDsAndPaths>

    @Query("SELECT * FROM history")
    fun getAllHistory() : Flow<List<HistoryItem>>

    @Query("SELECT DISTINCT website FROM history")
    fun getWebsites() : Flow<List<String>>

    @Query("SELECT COUNT(*) FROM history")
    fun getCount() : Flow<Int>

    @Query("SELECT * FROM history")
    fun getAllHistoryPaginated() : PagingSource<Int, HistoryItem>

    @Query("SELECT * FROM history")
    fun getAllHistoryList() : List<HistoryItem>

    @Query("SELECT * FROM history WHERE id=:id LIMIT 1")
    fun getHistoryItem(id: Long) : HistoryItem

    @Query("SELECT * FROM history WHERE url=:url")
    fun getAllHistoryByURL(url: String) : List<HistoryItem>

    @Query("SELECT * FROM history WHERE url=:url and type=:type")
    fun getAllHistoryByURLAndType(url: String, type: DownloadType) : List<HistoryItem>

    @Query("SELECT * FROM history WHERE id in (:ids)")
    fun getAllHistoryByIDs(ids: List<Long>) : List<HistoryItem>

    @Query("SELECT downloadPath FROM history WHERE id in (:ids)")
    fun getDownloadPathsFromIDs(ids: List<Long>) : List<HistoryRepository.HistoryItemDownloadPaths>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryItem)

    @Query("DELETE FROM history WHERE id=:id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun deleteAll()

    @Query("DELETE FROM history where id in (:ids)")
    suspend fun deleteAllByIDs(ids: List<Long>)

    @Query("DELETE FROM history WHERE id > (SELECT MIN(h.id) FROM history h WHERE h.url = history.url AND h.type = history.type)")
    suspend fun deleteDuplicates()

    @Update
    suspend fun update(item: HistoryItem)
}