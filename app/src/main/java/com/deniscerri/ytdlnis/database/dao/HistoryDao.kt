package com.deniscerri.ytdlnis.database.dao

import androidx.room.*
import com.deniscerri.ytdlnis.database.models.HistoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history WHERE (title LIKE '%'||:query||'%' OR author LIKE '%'||:query||'%') AND type LIKE '%'||:format||'%' AND website LIKE '%'||:site||'%' ORDER BY " +
            "CASE WHEN :sort = 'ASC' THEN id END ASC," +
            "CASE WHEN :sort = 'DESC' THEN id END DESC," +
            "CASE WHEN :sort = '' THEN id END DESC ")
    fun getHistorySortedByID(query : String, format : String, site : String, sort : String) : List<HistoryItem>

    @Query("SELECT * FROM history WHERE (title LIKE '%'||:query||'%' OR author LIKE '%'||:query||'%') AND type LIKE '%'||:format||'%' AND website LIKE '%'||:site||'%' ORDER BY " +
            "CASE WHEN :sort = 'ASC' THEN title END ASC," +
            "CASE WHEN :sort = 'DESC' THEN title END DESC," +
            "CASE WHEN :sort = '' THEN title END DESC ")
    fun getHistorySortedByTitle(query : String, format : String, site : String, sort : String) : List<HistoryItem>

    @Query("SELECT * FROM history WHERE (title LIKE '%'||:query||'%' OR author LIKE '%'||:query||'%') AND type LIKE '%'||:format||'%' AND website LIKE '%'||:site||'%' ORDER BY " +
            "CASE WHEN :sort = 'ASC' THEN author END ASC," +
            "CASE WHEN :sort = 'DESC' THEN author END DESC," +
            "CASE WHEN :sort = '' THEN author END DESC ")
    fun getHistorySortedByAuthor(query : String, format : String, site : String, sort : String) : List<HistoryItem>


    @Query("SELECT * FROM history")
    fun getAllHistory() : Flow<List<HistoryItem>>

    @Query("SELECT * FROM history")
    fun getAllHistoryList() : List<HistoryItem>

    @Query("SELECT * FROM history WHERE id=:id LIMIT 1")
    fun getHistoryItem(id: Long) : HistoryItem

    @Query("SELECT * FROM history WHERE url=:url")
    fun getAllHistoryByURL(url: String) : List<HistoryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryItem)

    @Query("DELETE FROM history WHERE id=:id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun deleteAll()

    @Query("DELETE FROM history WHERE id > (SELECT MIN(h.id) FROM history h WHERE h.url = history.url AND h.type = history.type)")
    suspend fun deleteDuplicates()

    @Update
    suspend fun update(item: HistoryItem)
}