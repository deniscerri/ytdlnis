package com.deniscerri.ytdlnis.database.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.dao.ObserveSourcesDao
import com.deniscerri.ytdlnis.database.models.ObserveSourcesItem
import com.deniscerri.ytdlnis.receiver.ObserveAlarmReceiver
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class ObserveSourcesRepository(private val observeSourcesDao: ObserveSourcesDao) {
    val items : Flow<List<ObserveSourcesItem>> = observeSourcesDao.getAllSourcesFlow()
    enum class SourceStatus {
        ACTIVE, STOPPED
    }

    enum class EveryCategory {
        DAY, WEEK, MONTH
    }

    companion object {
        val everyCategoryName = mapOf(
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

    fun cancelObservationTaskByID(context: Context, id: Long){
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                id.toInt(),
                Intent(context, ObserveAlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    fun calculateNextTime(item: ObserveSourcesItem) : Long {
        val c = Calendar.getInstance()

        val hourMin = Calendar.getInstance()
        hourMin.timeInMillis = item.everyTime

        c.set(Calendar.HOUR_OF_DAY, hourMin.get(Calendar.HOUR_OF_DAY))
        c.set(Calendar.MINUTE, hourMin.get(Calendar.MINUTE))

        if (item.everyNr == 0) item.everyNr = 1

        when(item.everyCategory){
            EveryCategory.DAY -> {
                c.add(Calendar.DAY_OF_MONTH, item.everyNr)
            }
            EveryCategory.WEEK -> {
                if(item.everyWeekDay.isEmpty()){
                    c.add(Calendar.DAY_OF_MONTH, 7 * item.everyNr)
                }else{
                    val weekDayNr = c.get(Calendar.DAY_OF_WEEK)
                    val followingWeekDay = item.everyWeekDay.firstOrNull { it.toInt() > weekDayNr }
                    if (followingWeekDay == null){
                        c.add(Calendar.DAY_OF_MONTH, item.everyWeekDay.minBy { it.toInt() }.toInt() + (7 - weekDayNr))
                        item.everyNr--
                    }else{
                        c.add(Calendar.DAY_OF_MONTH, followingWeekDay.toInt() - weekDayNr)
                    }

                    if (item.everyNr > 1){
                        c.add(Calendar.DAY_OF_MONTH, 7 * item.everyNr)
                    }
                }
            }
            EveryCategory.MONTH -> {
                c.add(Calendar.MONTH, item.everyNr)
                c.set(Calendar.DAY_OF_MONTH, item.everyMonthDay)
            }
        }


        return c.timeInMillis
    }

}