package com.deniscerri.ytdl.database.repository

import com.deniscerri.ytdl.database.dao.LogDao
import com.deniscerri.ytdl.database.models.LogItem
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

    suspend fun update(line: String, id: Long, resetLog: Boolean = false){
        kotlin.runCatching {
            logDao.updateLog(line, id, resetLog)
        }
    }

}