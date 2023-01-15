package com.deniscerri.ytdlnis.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.deniscerri.ytdlnis.database.models.HistoryItem

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history WHERE title LIKE '%'||:query||'%' AND type LIKE '%'||:format||'%' AND website LIKE '%'||:site||'%' ORDER BY " +
            "CASE WHEN :sort = 'ASC' THEN id END ASC," +
            "CASE WHEN :sort = 'DESC' THEN id END DESC," +
            "CASE WHEN :sort = '' THEN id END DESC ")
    fun getHistory(query : String, format : String, site : String, sort : String) : List<HistoryItem>

    @Query("SELECT * FROM history")
    fun getAllHistory() : LiveData<List<HistoryItem>>

    @Query("SELECT * FROM history WHERE id=:id LIMIT 1")
    suspend fun getHistoryItem(id: Int) : HistoryItem

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryItem)

    @Query("DELETE FROM history WHERE id=:id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM history")
    suspend fun deleteAll()

    @Query("DELETE FROM history WHERE id > (SELECT MIN(h.id) FROM history h WHERE h.url = history.url AND h.type = history.type)")
    suspend fun deleteDuplicates()

    @Update
    suspend fun update(item: HistoryItem)
}