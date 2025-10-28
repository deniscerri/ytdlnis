package com.deniscerri.ytdl.database.repository

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.dao.ObserveSourcesDao
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.deniscerri.ytdl.util.Extensions.calculateNextTimeForObserving
import com.deniscerri.ytdl.work.ObserveSourceWorker
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ObserveSourcesRepository(private val observeSourcesDao: ObserveSourcesDao, private val workManager: WorkManager, private val sharedPreferences: SharedPreferences) {
    val items : Flow<List<ObserveSourcesItem>> = observeSourcesDao.getAllSourcesFlow()
    enum class SourceStatus {
        ACTIVE, STOPPED
    }

    enum class EveryCategory {
        HOUR, DAY, WEEK, MONTH
    }

    companion object {
        val everyCategoryName = mapOf(
            EveryCategory.HOUR to R.string.hour,
            EveryCategory.DAY to R.string.day,
            EveryCategory.WEEK to R.string.week,
            EveryCategory.MONTH to R.string.month
        )
    }


    fun getAll() : List<ObserveSourcesItem> {
        return observeSourcesDao.getAllSources()
    }

    fun getByURL(url: String) : ObserveSourcesItem {
        return observeSourcesDao.getByURL(url)
    }

    fun getByID(id: Long) : ObserveSourcesItem {
        return observeSourcesDao.getByID(id)
    }


    suspend fun insert(item: ObserveSourcesItem) : Long{
        if (!observeSourcesDao.checkIfExistsWithSameURL(item.url)){
            return observeSourcesDao.insert(item)
        }
        return -1
    }

    suspend fun delete(item: ObserveSourcesItem){
        observeSourcesDao.delete(item.id)
    }


    suspend fun deleteAll(){
        observeSourcesDao.deleteAll()
    }

    suspend fun update(item: ObserveSourcesItem){
        observeSourcesDao.update(item)
    }

    fun cancelObservationTaskByID(id: Long){
        workManager.cancelAllWorkByTag("observation_$id")
    }

    fun observeTask(it: ObserveSourcesItem){
        cancelObservationTaskByID(it.id)

        Calendar.getInstance().apply {
            timeInMillis = it.startsTime

            if (it.everyCategory != EveryCategory.HOUR){
                val hourMin = Calendar.getInstance()
                hourMin.timeInMillis = it.everyTime
                set(Calendar.HOUR_OF_DAY, hourMin.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, hourMin.get(Calendar.MINUTE))
            }

            when(it.everyCategory){
                EveryCategory.HOUR -> {}
                EveryCategory.DAY -> {}
                EveryCategory.WEEK -> {
                    var weekDayNr = get(Calendar.DAY_OF_WEEK) - 1
                    if (weekDayNr == 0) weekDayNr = 7
                    val followingWeekDay = it.weeklyConfig?.weekDays?.firstOrNull { it >= weekDayNr }
                    if (followingWeekDay == null){
                        add(
                            Calendar.DAY_OF_MONTH,
                            it.weeklyConfig?.weekDays?.minBy { it }?.plus((7 - weekDayNr)) ?: 0)
                    }else{
                        add(Calendar.DAY_OF_MONTH, followingWeekDay - weekDayNr)
                    }
                }
                EveryCategory.MONTH -> {
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
            val allowMeteredNetworks = sharedPreferences.getBoolean("metered_networks", true)

            val workConstraints = Constraints.Builder()
            if (!allowMeteredNetworks) workConstraints.setRequiredNetworkType(NetworkType.UNMETERED)
            else {
                workConstraints.setRequiredNetworkType(NetworkType.CONNECTED)
            }

            val workRequest = OneTimeWorkRequestBuilder<ObserveSourceWorker>()
                .addTag("observeSources")
                .addTag("observation_${it.id}")
                .setConstraints(workConstraints.build())
                .setInitialDelay(timeInMillis - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putLong("id", it.id).build())

            workManager.enqueueUniqueWork(
                "OBSERVE${it.id}",
                ExistingWorkPolicy.REPLACE,
                workRequest.build()
            )
        }

    }

}