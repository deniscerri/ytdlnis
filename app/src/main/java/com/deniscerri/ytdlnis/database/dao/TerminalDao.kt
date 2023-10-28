package com.deniscerri.ytdlnis.database.dao

import androidx.room.*
import com.deniscerri.ytdlnis.database.models.TerminalItem
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
    fun updateTerminalLog(l: String, id: Long)

    @Query("SELECT * FROM terminalDownloads WHERE id=:id LIMIT 1")
    fun getTerminalById(id: Long) : TerminalItem?
    @Transaction
    fun updateLog(line: String, id: Long){
        val t = getTerminalById(id) ?: return
        val log = t.log ?: ""
        val lines = log.split("\n")
        //clean dublicate progress + add newline
        var newLine = line
        if (newLine.contains("[download")){
            newLine = "[download]" + line.split("[download]").last()
        }

        val l = lines.dropLastWhile { it.contains("[download") }.joinToString("\n") +  "\n${newLine}"

        updateTerminalLog(l, id)
    }

    @Insert
    suspend fun insert(item: TerminalItem) : Long

    @Query("DELETE from terminalDownloads WHERE id=:id")
    suspend fun delete(id: Long)
}