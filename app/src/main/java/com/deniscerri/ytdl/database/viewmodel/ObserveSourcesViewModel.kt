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
        repository = ObserveSourcesRepository(dao)
        items = repository.items.asLiveData()
        workManager = WorkManager.getInstance(application)
        preferences = PreferenceManager.getDefaultSharedPreferences(application)
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

    suspend fun insert(item: ObserveSourcesItem) : Long {
        if (item.id > 0) {
            repository.update(item)
            observeTask(item)
            return item.id
        }

        val id = repository.insert(item)
        item.id = id
        if (id > 0) observeTask(item)
        return id
    }

    fun delete(item: ObserveSourcesItem) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { cancelObservationTaskByID(item.id) }
        repository.delete(item)
    }

    fun deleteAll() = viewModelScope.launch(Dispatchers.IO) {
        getAll().forEach {
            runCatching { cancelObservationTaskByID(it.id) }
        }

        repository.deleteAll()
    }

    suspend fun update(item: ObserveSourcesItem) {
        repository.update(item)
    }

    private fun cancelObservationTaskByID(id: Long){
        workManager.cancelAllWorkByTag(id.toString())
    }



    private fun observeTask(it: ObserveSourcesItem){
        cancelObservationTaskByID(it.id)

        Calendar.getInstance().apply {
            timeInMillis = it.startsTime
            val date = Calendar.getInstance()
            set(Calendar.DAY_OF_MONTH, date.get(Calendar.DAY_OF_MONTH))
            set(Calendar.MONTH, date.get(Calendar.MONTH))
            set(Calendar.YEAR, date.get(Calendar.YEAR))

            if (it.everyCategory != ObserveSourcesRepository.EveryCategory.HOUR){
                val hourMin = Calendar.getInstance()
                hourMin.timeInMillis = it.everyTime
                set(Calendar.HOUR_OF_DAY, hourMin.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, hourMin.get(Calendar.MINUTE))
            }

            when(it.everyCategory){
                ObserveSourcesRepository.EveryCategory.HOUR -> {}
                ObserveSourcesRepository.EveryCategory.DAY -> {}
                ObserveSourcesRepository.EveryCategory.WEEK -> {
                    var weekDayNr = get(Calendar.DAY_OF_WEEK) - 1
                    if (weekDayNr == 0) weekDayNr = 7
                    val followingWeekDay = it.weeklyConfig?.weekDays?.firstOrNull { it >= weekDayNr }
                    if (followingWeekDay == null){
                        add(Calendar.DAY_OF_MONTH,
                            it.weeklyConfig?.weekDays?.minBy { it }?.plus((7 - weekDayNr)) ?: 0)
                    }else{
                        add(Calendar.DAY_OF_MONTH, followingWeekDay - weekDayNr)
                    }
                }
                ObserveSourcesRepository.EveryCategory.MONTH -> {
                    val currentMonthIndex = get(Calendar.MONTH)
                    if (it.monthlyConfig?.startsMonth != currentMonthIndex){
                        set(Calendar.MONTH, it.monthlyConfig?.startsMonth ?: 0)
                        if (timeInMillis < Calendar.getInstance().timeInMillis){
                            add(Calendar.YEAR, 1)
                        }
                    }
                }
            }

            //schedule for next time
            val workConstraints = Constraints.Builder()
            val workRequest = OneTimeWorkRequestBuilder<ObserveSourceWorker>()
                .addTag("observeSources")
                .addTag(it.id.toString())
                .setConstraints(workConstraints.build())
                .setInitialDelay(System.currentTimeMillis() - timeInMillis, TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putLong("id", it.id).build())

            workManager.enqueueUniqueWork(
                "OBSERVE${it.id}",
                ExistingWorkPolicy.REPLACE,
                workRequest.build()
            )
        }

    }


}