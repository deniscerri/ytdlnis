package com.deniscerri.ytdlnis.database.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.repository.HistoryRepository
import com.deniscerri.ytdlnis.database.DBManager.SORTING
import com.deniscerri.ytdlnis.database.repository.HistoryRepository.HistorySortType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository : HistoryRepository
    val sortOrder = MutableLiveData(SORTING.DESC)
    val sortType = MutableLiveData(HistorySortType.DATE)
    val websiteFilter = MutableLiveData("")
    private val queryFilter = MutableLiveData("")
    private val formatFilter = MutableLiveData("")
    private val notDeletedFilter = MutableLiveData(false)

    val allItems : LiveData<List<HistoryItem>>
    private var _items = MediatorLiveData<List<HistoryItem>>()

    init {
        val dao = DBManager.getInstance(application).historyDao
        repository = HistoryRepository(dao)
        allItems = repository.items.asLiveData()

        _items.addSource(allItems){
            filter(queryFilter.value!!, formatFilter.value!!, websiteFilter.value!!, sortType.value!!, sortOrder.value!!, notDeletedFilter.value!!)
        }
        _items.addSource(formatFilter){
            filter(queryFilter.value!!, formatFilter.value!!, websiteFilter.value!!, sortType.value!!, sortOrder.value!!, notDeletedFilter.value!!)
        }
        _items.addSource(sortType){
            filter(queryFilter.value!!, formatFilter.value!!, websiteFilter.value!!, sortType.value!!, sortOrder.value!!, notDeletedFilter.value!!)
        }
        _items.addSource(websiteFilter){
            filter(queryFilter.value!!, formatFilter.value!!, websiteFilter.value!!, sortType.value!!, sortOrder.value!!, notDeletedFilter.value!!)
        }
        _items.addSource(queryFilter){
            filter(queryFilter.value!!, formatFilter.value!!, websiteFilter.value!!, sortType.value!!, sortOrder.value!!, notDeletedFilter.value!!)
        }

        _items.addSource(notDeletedFilter){
            filter(queryFilter.value!!, formatFilter.value!!, websiteFilter.value!!, sortType.value!!, sortOrder.value!!, notDeletedFilter.value!!)
        }

    }

    fun getFilteredList() : LiveData<List<HistoryItem>>{
        return _items
    }

    fun setSorting(sort: HistorySortType){
        if (sortType.value != sort){
            sortOrder.value = SORTING.DESC
        }else{
            sortOrder.value = if (sortOrder.value == SORTING.DESC) {
                SORTING.ASC
            } else SORTING.DESC
        }
        sortType.value = sort
    }

    fun setWebsiteFilter(filter : String){
        websiteFilter.value = filter
    }

    fun setQueryFilter(filter: String){
        queryFilter.value = filter
    }

    fun setFormatFilter(filter: String){
        formatFilter.value = filter
    }

    fun setNotDeleted(filter: Boolean){
        notDeletedFilter.value = filter
    }

    private fun filter(query : String, format : String, site : String, sortType: HistorySortType, sort: SORTING, notDeleted: Boolean) = viewModelScope.launch(Dispatchers.IO){
        _items.postValue(repository.getFiltered(query, format, site, sortType, sort, notDeleted))
    }

    fun getAll() : List<HistoryItem> {
        return repository.getAll()
    }

    fun insert(item: HistoryItem) = viewModelScope.launch(Dispatchers.IO){
        repository.insert(item)
    }

    fun delete(item: HistoryItem, deleteFile: Boolean) = viewModelScope.launch(Dispatchers.IO){
        repository.delete(item, deleteFile)
    }

    fun deleteAll(deleteFile: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAll(deleteFile)
    }

    fun deleteDuplicates() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteDuplicates()
    }

    fun update(item: HistoryItem) = viewModelScope.launch(Dispatchers.IO){
        repository.update(item)
    }

    fun clearDeleted() = viewModelScope.launch(Dispatchers.IO) {
        repository.clearDeletedHistory()
    }

}