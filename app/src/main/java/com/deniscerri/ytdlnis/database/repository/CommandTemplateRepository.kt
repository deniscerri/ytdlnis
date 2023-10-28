package com.deniscerri.ytdlnis.database.repository

import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.dao.CommandTemplateDao
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.TemplateShortcut
import kotlinx.coroutines.flow.Flow

class CommandTemplateRepository(private val commandDao: CommandTemplateDao) {
    val items : Flow<List<CommandTemplate>> = commandDao.getAllTemplatesFlow()
    val shortcuts : Flow<List<TemplateShortcut>> = commandDao.getAllShortcutsFlow()

    enum class CommandTemplateSortType {
        DATE, TITLE, LENGTH
    }

    fun getAll() : List<CommandTemplate> {
        return commandDao.getAllTemplates()
    }

    fun getFiltered(query : String, sortType: CommandTemplateSortType, sort: DBManager.SORTING) : List<CommandTemplate> {
        return when(sortType){
            CommandTemplateSortType.DATE ->  commandDao.getCommandsSortedByID(query,  sort.toString())
            CommandTemplateSortType.TITLE ->  commandDao.getCommandsSortedByTitle(query,  sort.toString())
            CommandTemplateSortType.LENGTH ->  commandDao.getCommandsSortedByContentLength(query,  sort.toString())
        }
    }

    fun getTotalNumber() : Int {
        return commandDao.getTotalNumber()
    }

    fun getTotalShortcutNumber() : Int {
        return commandDao.getTotalShortcutNumber()
    }

    fun getItem(id: Long) : CommandTemplate {
        return commandDao.getTemplate(id)!!
    }

    suspend fun insert(item: CommandTemplate){
        commandDao.insert(item)
    }

    suspend fun delete(item: CommandTemplate){
        commandDao.delete(item.id)
    }

    fun getAllShortCuts() : List<TemplateShortcut> {
        return commandDao.getAllShortcuts()
    }

    suspend fun insertShortcut(item: TemplateShortcut){
        if (commandDao.checkExistingShortcut(item.content) == 0){
            commandDao.insertShortcut(item)
        }
    }

    suspend fun deleteShortcut(item: TemplateShortcut){
        commandDao.deleteShortcut(item.id)
    }

    suspend fun deleteAll(){
        commandDao.deleteAll()
    }


    suspend fun update(item: CommandTemplate){
        commandDao.update(item)
    }

}