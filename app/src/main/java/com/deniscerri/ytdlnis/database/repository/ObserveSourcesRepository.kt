package com.deniscerri.ytdlnis.database.repository

import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.dao.ObserveSourcesDao
import com.deniscerri.ytdlnis.database.models.ObserveSourcesItem
import kotlinx.coroutines.flow.Flow

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

}