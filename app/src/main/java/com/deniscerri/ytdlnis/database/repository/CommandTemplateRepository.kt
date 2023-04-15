package com.deniscerri.ytdlnis.database.repository

import androidx.lifecycle.LiveData
import com.deniscerri.ytdlnis.database.dao.CommandTemplateDao
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.TemplateShortcut

class CommandTemplateRepository(private val commandDao: CommandTemplateDao) {
    val items : LiveData<List<CommandTemplate>> = commandDao.getAllTemplatesLiveData()
    val shortcuts : LiveData<List<TemplateShortcut>> = commandDao.getAllShortcutsLiveData()

    fun getAll() : List<CommandTemplate> {
        return commandDao.getAllTemplates();
    }

    fun getTotalNumber() : Int {
        return commandDao.getTotalNumber()
    }

    fun getTotalShortcutNumber() : Int {
        return commandDao.getTotalShortcutNumber()
    }

    fun getItem(id: Long) : CommandTemplate {
        return commandDao.getTemplate(id)
    }

    suspend fun insert(item: CommandTemplate){
        commandDao.insert(item)
    }

    suspend fun delete(item: CommandTemplate){
        commandDao.delete(item.id)
    }

    fun getAllShortCuts() : List<TemplateShortcut> {
        return commandDao.getAllShortcuts();
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