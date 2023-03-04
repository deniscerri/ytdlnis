package com.deniscerri.ytdlnis.database.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.models.TemplateShortcut
import com.deniscerri.ytdlnis.database.repository.CommandTemplateRepository
import com.deniscerri.ytdlnis.database.repository.HistoryRepository
import com.deniscerri.ytdlnis.database.repository.HistoryRepository.HistorySort
import com.deniscerri.ytdlnis.database.repository.HistoryRepository.HistorySortType
import com.deniscerri.ytdlnis.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CommandTemplateViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CommandTemplateRepository
    val items: LiveData<List<CommandTemplate>>
    val shortcuts : LiveData<List<TemplateShortcut>>

    init {
        val dao = DBManager.getInstance(application).commandTemplateDao
        repository = CommandTemplateRepository(dao)
        items = repository.items
        shortcuts = repository.shortcuts
    }

    fun getTemplate(itemId: Long): CommandTemplate {
        return repository.getItem(itemId)
    }

    fun getAll(): List<CommandTemplate> {
        return repository.getAll()
    }

    fun insert(item: CommandTemplate) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(item)
    }

    fun delete(item: CommandTemplate) = viewModelScope.launch(Dispatchers.IO) {
        repository.delete(item)
    }

    fun insertShortcut(item: TemplateShortcut) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertShortcut(item)
    }

    fun deleteShortcut(item: TemplateShortcut) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteShortcut(item)
    }

    fun deleteAll() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAll()
    }

    fun update(item: CommandTemplate) = viewModelScope.launch(Dispatchers.IO) {
        repository.update(item)
    }

    fun importFromClipboard() = viewModelScope.launch(Dispatchers.IO) {

    }

    fun exportToClipboard() {

    }

}