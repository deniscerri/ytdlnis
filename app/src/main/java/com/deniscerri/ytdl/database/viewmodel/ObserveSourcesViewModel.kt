package com.deniscerri.ytdl.database.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.deniscerri.ytdl.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdl.work.ObserveSourceWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ObserveSourcesViewModel(private val application: Application) : AndroidViewModel(application) {
    private val repository: ObserveSourcesRepository
    val items: LiveData<List<ObserveSourcesItem>>
    private val workManager : WorkManager
    private val preferences : SharedPreferences

    init {
        val dao = DBManager.getInstance(application).observeSourcesDao
        workManager = WorkManager.getInstance(application)
        preferences = PreferenceManager.getDefaultSharedPreferences(application)
        repository = ObserveSourcesRepository(dao, workManager, preferences)
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
            repository.observeTask(item)
            return item.id
        }

        val id = repository.insert(item)
        item.id = id
        if (id > 0) repository.observeTask(item)
        return id
    }

    suspend fun stopObserving(item: ObserveSourcesItem) {
        item.status = ObserveSourcesRepository.SourceStatus.STOPPED
        repository.update(item)
        repository.cancelObservationTaskByID(item.id)
    }

    fun delete(item: ObserveSourcesItem) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { repository.cancelObservationTaskByID(item.id) }
        repository.delete(item)
    }

    fun deleteAll() = viewModelScope.launch(Dispatchers.IO) {
        getAll().forEach {
            runCatching { repository.cancelObservationTaskByID(it.id) }
        }

        repository.deleteAll()
    }

    suspend fun update(item: ObserveSourcesItem) {
        repository.update(item)
    }
}