package com.deniscerri.ytdlnis.database.repository

import androidx.lifecycle.LiveData
import com.deniscerri.ytdlnis.database.dao.CommandTemplateDao
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.TemplateShortcut

class CommandTemplateRepository(private val commandDao: CommandTemplateDao) {
    val items : LiveData<List<CommandTemplate>> = commandDao.getAllTemplatesLiveData()
    val shortcuts : LiveData<List<TemplateShortcut>> = commandDao.getAllShortcuts()

    fun getAll() : List<CommandTemplate> {
        return commandDao.getAllTemplates();
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

    suspend fun insertShortcut(item: TemplateShortcut){
        commandDao.insertShortcut(item.content)
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