package com.deniscerri.ytdlnis.database.repository

import com.deniscerri.ytdlnis.database.dao.LogDao
import com.deniscerri.ytdlnis.database.models.LogItem
import kotlinx.coroutines.flow.Flow

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
            val item = getItem(id)
            val log = item.content
            //clean duplicate progress + add newline
            //item.content = log.replace("(?s:.*\\n)?\\K\\[download\\]( *?)(\\d)(.*?)\\n(?!.*\\[download\\]( *?)(\\d)(.*?)\\n)".toRegex()).replac { it.contains("[download") }.joinToString("\n") +  "\n${line}"
            logDao.update(item)
        }
    }

}