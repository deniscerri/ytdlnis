package com.deniscerri.ytdlnis.database.repository

import com.deniscerri.ytdlnis.database.dao.LogDao
import com.deniscerri.ytdlnis.database.models.CookieItem
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
        return logDao.getByID(id);
    }

    suspend fun update(newLine: String, id: Long){
        val item = getItem(id)
        item.content += "${newLine}\n"
        logDao.update(item)
    }

}