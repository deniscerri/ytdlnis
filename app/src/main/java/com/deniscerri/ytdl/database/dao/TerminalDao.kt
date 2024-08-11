package com.deniscerri.ytdl.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.deniscerri.ytdl.database.models.TerminalItem
import com.deniscerri.ytdl.util.Extensions.appendLineToLog
import kotlinx.coroutines.flow.Flow

@Dao
interface TerminalDao {

    @Query("SELECT id,command FROM terminalDownloads ORDER BY id")
    fun getActiveTerminalDownloads() : List<TerminalItem>

    @Query("SELECT id,command FROM terminalDownloads ORDER BY id")
    fun getActiveTerminalDownloadsFlow() : Flow<List<TerminalItem>>

    @Query("SELECT * from terminalDownloads where id=:id")
    fun getActiveTerminalFlow(id: Long) : Flow<TerminalItem>

    @Query("SELECT COUNT(*) FROM terminalDownloads")
    fun getActiveTerminalsCount() : Int

    @Query("UPDATE terminalDownloads set log=:l where id=:id")
    suspend fun updateTerminalLog(l: String, id: Long)

    @Query("SELECT * FROM terminalDownloads WHERE id=:id LIMIT 1")
    fun getTerminalById(id: Long) : TerminalItem?
    @Transaction
    suspend fun updateLog(line: String, id: Long, resetLog : Boolean = false){
        val t = getTerminalById(id) ?: return
        var log = t.log ?: ""
        log = if (resetLog) {
            line.lines().joinToString("\n")
        }else{
            log.appendLineToLog(line)
        }
        updateTerminalLog(log, id)
    }

    @Insert
    suspend fun insert(item: TerminalItem) : Long

    @Query("DELETE from terminalDownloads WHERE id=:id")
    suspend fun delete(id: Long)
}