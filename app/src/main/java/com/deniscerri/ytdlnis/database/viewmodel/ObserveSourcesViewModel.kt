package com.deniscerri.ytdlnis.database.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.models.ObserveSourcesItem
import com.deniscerri.ytdlnis.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdlnis.receiver.ObserveAlarmReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Month
import java.util.Calendar

class ObserveSourcesViewModel(private val application: Application) : AndroidViewModel(application) {
    private val repository: ObserveSourcesRepository
    val items: LiveData<List<ObserveSourcesItem>>
    private val alarmManager : AlarmManager
    private val preferences : SharedPreferences

    init {
        val dao = DBManager.getInstance(application).observeSourcesDao
        repository = ObserveSourcesRepository(dao)
        items = repository.items.asLiveData()
        alarmManager = application.getSystemService(AlarmManager::class.java)
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
        runCatching { repository.cancelObservationTaskByID(application, item.id) }
        repository.delete(item)
    }

    fun deleteAll() = viewModelScope.launch(Dispatchers.IO) {
        getAll().forEach {
            runCatching { repository.cancelObservationTaskByID(application, it.id) }
        }

        repository.deleteAll()
    }

    suspend fun update(item: ObserveSourcesItem) {
        repository.update(item)
    }

    fun cancelObservationTaskByID(id : Long){
        repository.cancelObservationTaskByID(application, id)
    }


    private fun observeTask(it: ObserveSourcesItem){
        cancelObservationTaskByID(it.id)

        val id = it.id
        val c = Calendar.getInstance()

        val date = Calendar.getInstance()
        date.timeInMillis = it.startsTime
        val hourMin = Calendar.getInstance()
        hourMin.timeInMillis = it.everyTime
        c.set(Calendar.DAY_OF_MONTH, date.get(Calendar.DAY_OF_MONTH))
        c.set(Calendar.MONTH, date.get(Calendar.MONTH))
        c.set(Calendar.YEAR, date.get(Calendar.YEAR))
        c.set(Calendar.HOUR_OF_DAY, hourMin.get(Calendar.HOUR_OF_DAY))
        c.set(Calendar.MINUTE, hourMin.get(Calendar.MINUTE))

        val intent = Intent(application, ObserveAlarmReceiver::class.java)
        intent.putExtra("id", id)

        when(it.everyCategory){
            ObserveSourcesRepository.EveryCategory.DAY -> {}
            ObserveSourcesRepository.EveryCategory.WEEK -> {
                val weekDayNr = c.get(Calendar.DAY_OF_WEEK)
                val followingWeekDay = it.everyWeekDay.firstOrNull { it.toInt() >= weekDayNr }
                if (followingWeekDay == null){
                    c.add(Calendar.DAY_OF_MONTH, it.everyWeekDay.minBy { it.toInt() }.toInt() + (7 - weekDayNr))
                }else{
                    c.add(Calendar.DAY_OF_MONTH, followingWeekDay.toInt() - weekDayNr)
                }
            }
            ObserveSourcesRepository.EveryCategory.MONTH -> {
                val theMonthIndex = Month.values().indexOf(it.startsMonth)
                val currentMonthIndex = c.get(Calendar.MONTH)
                if (theMonthIndex != currentMonthIndex){
                    c.set(Calendar.MONTH, theMonthIndex)
                    if (c.timeInMillis < Calendar.getInstance().timeInMillis){
                        c.add(Calendar.YEAR, 1)
                    }
                }
            }
        }

        alarmManager.setExact(
            AlarmManager.RTC,
            c.timeInMillis,
            PendingIntent.getBroadcast(application, it.id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )

    }


}