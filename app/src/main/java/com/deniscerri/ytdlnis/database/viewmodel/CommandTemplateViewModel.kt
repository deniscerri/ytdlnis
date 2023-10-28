package com.deniscerri.ytdlnis.database.viewmodel

import android.app.Application
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.CommandTemplateExport
import com.deniscerri.ytdlnis.database.models.TemplateShortcut
import com.deniscerri.ytdlnis.database.repository.CommandTemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CommandTemplateViewModel(private val application: Application) : AndroidViewModel(application) {
    private val repository: CommandTemplateRepository
    val sortOrder = MutableLiveData(DBManager.SORTING.DESC)
    val sortType = MutableLiveData(CommandTemplateRepository.CommandTemplateSortType.DATE)
    private val queryFilter = MutableLiveData("")

    val allItems: LiveData<List<CommandTemplate>>
    private var _items = MediatorLiveData<List<CommandTemplate>>()

    val shortcuts : LiveData<List<TemplateShortcut>>
    private val jsonFormat = Json { prettyPrint = true }

    init {
        val dao = DBManager.getInstance(application).commandTemplateDao
        repository = CommandTemplateRepository(dao)
        allItems = repository.items.asLiveData()
        shortcuts = repository.shortcuts.asLiveData()

        _items.addSource(allItems){
            filter(queryFilter.value!!, sortType.value!!, sortOrder.value!!)
        }

        _items.addSource(sortType){
            filter(queryFilter.value!!, sortType.value!!, sortOrder.value!!)
        }

        _items.addSource(queryFilter){
            filter(queryFilter.value!!,  sortType.value!!, sortOrder.value!!)
        }
    }

    fun getFilteredList() : LiveData<List<CommandTemplate>>{
        return _items
    }

    fun setSorting(sort: CommandTemplateRepository.CommandTemplateSortType){
        if (sortType.value != sort){
            sortOrder.value = DBManager.SORTING.DESC
        }else{
            sortOrder.value = if (sortOrder.value == DBManager.SORTING.DESC) {
                DBManager.SORTING.ASC
            } else DBManager.SORTING.DESC
        }
        sortType.value = sort
    }

    private fun filter(query : String, sortType: CommandTemplateRepository.CommandTemplateSortType, sort: DBManager.SORTING) = viewModelScope.launch(Dispatchers.IO){
        _items.postValue(repository.getFiltered(query, sortType, sort))
    }

    fun setQueryFilter(filter: String){
        queryFilter.value = filter
    }

    fun getTemplate(itemId: Long): CommandTemplate {
        return repository.getItem(itemId)
    }

    fun getAll(): List<CommandTemplate> {
        return repository.getAll()
    }

    fun getAllShortcuts() : List<TemplateShortcut> {
        return repository.getAllShortCuts()
    }

    fun getTotalNumber(): Int {
        return repository.getTotalNumber()
    }

    fun getTotalShortcutNumber(): Int {
        return repository.getTotalShortcutNumber()
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

    suspend fun importFromClipboard() : Int {
        var count = 0
        try{
            val allTemplates = repository.getAll()
            val allShortcuts = repository.getAllShortCuts()
            val clipboard = withContext(Dispatchers.Main){
                application.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            }
            val clip = clipboard.primaryClip!!.getItemAt(0).text.toString()

            jsonFormat.decodeFromString<CommandTemplateExport>(clip).run {
                templates.filterNot {t ->
                    allTemplates.find { it.content == t.content} != null
                }.run {
                    this.forEach {
                        repository.insert(it.copy(id=0))
                        count++
                    }
                }

                shortcuts.filterNot {
                    allShortcuts.contains(it)
                }.run{
                    this.forEach {
                        repository.insertShortcut(it.copy(id=0))
                        count++
                    }
                }
            }
        }catch (e: Exception){
            e.printStackTrace()
        }

        return count
    }


    fun exportToClipboard() = viewModelScope.launch {
        try{
            val allTemplates = withContext(Dispatchers.IO){
                repository.getAll()
            }
            val allShortcuts = withContext(Dispatchers.IO){
                repository.getAllShortCuts()
            }
            val output = jsonFormat.encodeToString(
                CommandTemplateExport(
                    templates = allTemplates,
                    shortcuts = allShortcuts
                )
            )

            val clipboard: ClipboardManager =
                application.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setText(output)
        }catch (e: Exception){
            e.printStackTrace()
        }
    }

}