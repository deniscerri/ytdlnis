package com.deniscerri.ytdl.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.deniscerri.ytdl.database.models.LogItem
import com.deniscerri.ytdl.util.Extensions.appendLineToLog
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT id, title, downloadType, format, downloadTime, '' as content FROM logs ORDER BY id DESC")
    fun getAllLogs() : List<LogItem>

    @Query("SELECT id, title, downloadType, format, downloadTime, '' as content FROM logs ORDER BY id DESC")
    fun getAllLogsFlow() : Flow<List<LogItem>>

    @Query("SELECT * FROM logs WHERE id=:id LIMIT 1")
    fun getLogFlowByID(id: Long) : Flow<LogItem>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: LogItem) : Long

    @Query("DELETE FROM logs")
    suspend fun deleteAll()

    @Query("DELETE FROM logs WHERE id=:itemId")
    suspend fun delete(itemId: Long)

    @Transaction
    suspend fun deleteItems(list: List<LogItem>){
        list.forEach{
            delete(it.id)
        }
    }

    @Query("UPDATE logs set content=:l where id=:id")
    suspend fun updateLogContent(l: String, id: Long)

    @Transaction
    suspend fun updateLog(line: String, id: Long) {
        val l = getByID(id) ?: return
        val log = l.content ?: ""
        updateLogContent(log.appendLineToLog(line), id)
    }

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: LogItem)

    @Query("SELECT * FROM logs WHERE id=:id LIMIT 1")
    fun getByID(id: Long) : LogItem
}