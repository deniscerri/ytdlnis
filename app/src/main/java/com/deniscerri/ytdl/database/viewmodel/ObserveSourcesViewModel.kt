package com.deniscerri.ytdl.database.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.deniscerri.ytdl.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdl.database.repository.ResultRepository
import com.deniscerri.ytdl.util.ObserveAlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ObserveSourcesViewModel(private val application: Application) : AndroidViewModel(application) {
    private val repository: ObserveSourcesRepository
    val items: LiveData<List<ObserveSourcesItem>>
    private val preferences : SharedPreferences
    private val alarmScheduler = ObserveAlarmScheduler(application)

    init {
        val dao = DBManager.getInstance(application).observeSourcesDao
        preferences = PreferenceManager.getDefaultSharedPreferences(application)
        repository = ObserveSourcesRepository(dao)
        items = repository.items.asLiveData()
    }

    fun getAll(): List<ObserveSourcesItem> {
        return repository.getAll()
    }

    fun getByURL(url: String) : ObserveSourcesItem {
        return repository.getByURL(url)
    }

    fun getByID(id: Long) : ObserveSourcesItem {
        return repository.getByID(id)
    }

    suspend fun insertUpdate(item: ObserveSourcesItem) : Long {
        if (item.id > 0) {
            repository.update(item)
            alarmScheduler.schedule(item)
            return item.id
        }

        val id = repository.insert(item)
        item.id = id
        if (id > 0) alarmScheduler.schedule(item)
        return id
    }

    suspend fun stopObserving(item: ObserveSourcesItem) {
        item.status = ObserveSourcesRepository.SourceStatus.STOPPED
        repository.update(item)
        alarmScheduler.cancel(item.id)
    }

    fun delete(item: ObserveSourcesItem) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {  alarmScheduler.cancel(item.id) }
        repository.delete(item)
    }

    fun deleteAll() = viewModelScope.launch(Dispatchers.IO) {
        getAll().forEach { runCatching { alarmScheduler.cancel(it.id) } }
        repository.deleteAll()
    }

    suspend fun update(item: ObserveSourcesItem) {
        repository.update(item)
    }

    suspend fun resetProcessedLinks(item: ObserveSourcesItem) {
        item.alreadyProcessedLinks = mutableListOf()
        item.ignoredLinks = mutableListOf()
        update(item)
    }

    suspend fun clearIgnoredLinks(item: ObserveSourcesItem) {
        item.ignoredLinks = mutableListOf()
        update(item)
    }

    suspend fun resetRunCount(item: ObserveSourcesItem) {
        item.runCount = 0
        update(item)
    }

    suspend fun markCurrentAsProcessed(item: ObserveSourcesItem) {
        val db = DBManager.getInstance(application)
        val resultRepo = ResultRepository(db.resultDao, db.commandTemplateDao, application)
        val urls = runCatching {
            resultRepo.getResultsFromSource(item.url, resetResults = false, addToResults = false, singleItem = false)
        }.getOrElse { emptyList() }.map { it.url }
        item.alreadyProcessedLinks = urls.toMutableList()
        item.ignoredLinks = mutableListOf()
        repository.update(item)
    }
}