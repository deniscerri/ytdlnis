package com.deniscerri.ytdlnis.database.repository

import com.deniscerri.ytdlnis.database.dao.LogDao
import com.deniscerri.ytdlnis.database.models.LogItem
import kotlinx.coroutines.flow.Flow
import java.util.regex.MatchResult
import java.util.regex.Pattern

class LogRepository(private val logDao: LogDao) {
    val items : Flow<List<LogItem>> = logDao.getAllLogsFlow()

    fun getAll() : List<LogItem> {
        return logDao.getAllLogs()
    }

    fun getLogFlowByID(id: Long) : Flow<LogItem> {
        return logDao.getLogFlowByID(id)
    }

    fun getLogByID(id: Long) : LogItem? {
        return logDao.getByID(id)
    }


    suspend fun insert(item: LogItem) : Long{
        return logDao.insert(item)
    }

    suspend fun delete(item: LogItem){
        logDao.delete(item.id)
    }


    suspend fun deleteAll(){
        logDao.deleteAll()
    }

    fun getItem(id: Long) : LogItem{
        return logDao.getByID(id)
    }

    suspend fun update(line: String, id: Long){
        runCatching {
            val item = getItem(id) ?: return
            val log = item.content ?: ""
            val lines = log.split("\n")
            //clean dublicate progress + add newline
                var newLine = line
                if (newLine.contains("[download")){
                    newLine = "[download]" + line.split("[download]").last()
                }

                val l = lines.dropLastWhile { it.contains("[download") }.joinToString("\n") +  "\n${newLine}"
                item.content = l

            //item.content += "\n$line"
            logDao.update(item)
        }
    }

}