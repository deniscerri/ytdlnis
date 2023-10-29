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
            val item = getItem(id)
            val log = item.content
            //clean duplicate progress + add newline
            val lines = log.split("\n").toMutableList()
            run loop@ {
                for(i in lines.size - 1 downTo 0){
                    val l = lines[i]
                    if(l.contains("\\[download]( *?)(\\d)(.*?)".toRegex())){
                        lines[i] = ""
                        return@loop
                    }
                }
            }
            val l = if (line.contains("[download]")) {
                "[download]" + line.split("[download]").last()
            }else {
                line
            }
            item.content = lines.filter { it.isNotBlank() }.joinToString("\n") + "\n${l}"
            logDao.update(item)
        }
    }

}