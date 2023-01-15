package com.deniscerri.ytdlnis.database.viewmodel

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.repository.HistoryRepository
import com.deniscerri.ytdlnis.database.repository.HistoryRepository.HistorySort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository : HistoryRepository
    private val sortType = MutableLiveData(HistorySort.DESC)
    private val websiteFilter = MutableLiveData("")
    private val queryFilter = MutableLiveData("")
    private val formatFilter = MutableLiveData("")
    val allItems : LiveData<List<HistoryItem>>
    private var _items = MediatorLiveData<List<HistoryItem>>()

    init {
        val dao = DBManager.getInstance(application).historyDao
        repository = HistoryRepository(dao)
        allItems = repository.items

        _items.addSource(allItems, Observer {
            filter(queryFilter.value!!, formatFilter.value!!, websiteFilter.value!!, sortType.value!!)
        })
        _items.addSource(formatFilter, Observer {
            Log.e("aa", "audio");
            filter(queryFilter.value!!, formatFilter.value!!, websiteFilter.value!!, sortType.value!!)
        })
        _items.addSource(sortType, Observer {
            Log.e("aa", "asssssssssssss");
            filter(queryFilter.value!!, formatFilter.value!!, websiteFilter.value!!, sortType.value!!)
        })
        _items.addSource(websiteFilter, Observer {
            Log.e("Aa", "website")
            filter(queryFilter.value!!, formatFilter.value!!, websiteFilter.value!!, sortType.value!!)
        })
        _items.addSource(queryFilter, Observer {
            filter(queryFilter.value!!, formatFilter.value!!, websiteFilter.value!!, sortType.value!!)
        })

    }

    fun getFilteredList() : LiveData<List<HistoryItem>>{
        return _items;
    }

    fun setSorting(sort: HistorySort){
        Log.e("aa", "asssssssssssss");
        sortType.value = sort
    }

    fun setWebsiteFilter(filter : String){
        websiteFilter.value = filter
    }

    fun setQueryFilter(filter: String){
        queryFilter.value = filter
    }

    fun setFormatFilter(filter: String){
        Log.e("aa", "audio");
        formatFilter.value = filter
    }

    fun filter(query : String, format : String, site : String, sort: HistorySort) = viewModelScope.launch(Dispatchers.IO){
        _items.postValue(repository.getFiltered(query, format, site, sort))
    }

    fun insert(item: HistoryItem) = viewModelScope.launch(Dispatchers.IO){
        repository.insert(item)
    }

    fun delete(item: HistoryItem, deleteFile: Boolean) = viewModelScope.launch(Dispatchers.IO){
        repository.delete(item, deleteFile)
    }

    fun deleteAll() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAll()
    }

    fun deleteDuplicates() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteDuplicates()
    }

    fun update(item: HistoryItem) = viewModelScope.launch(Dispatchers.IO){
        repository.update(item);
    }

    fun clearDeleted() = viewModelScope.launch(Dispatchers.IO) {
        repository.clearDeletedHistory()
    }
}