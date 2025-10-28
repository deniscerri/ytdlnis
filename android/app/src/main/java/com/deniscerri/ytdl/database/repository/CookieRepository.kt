package com.deniscerri.ytdl.database.repository

import com.deniscerri.ytdl.database.dao.CookieDao
import com.deniscerri.ytdl.database.models.CookieItem
import kotlinx.coroutines.flow.Flow
import org.hamcrest.Description

class CookieRepository(private val cookieDao: CookieDao) {
    val items : Flow<List<CookieItem>> = cookieDao.getAllCookiesFlow()

    fun getAll() : List<CookieItem> {
        return cookieDao.getAllCookies()
    }

    fun getAllEnabled() : List<CookieItem> {
        return cookieDao.getAllEnabledCookies()
    }

    fun getByURL(url: String) : CookieItem? {
        return cookieDao.getByURL(url)
    }

    fun getByURLDescription(url: String, description: String) : CookieItem? {
        return cookieDao.getByURLDescription(url, description)
    }


    suspend fun insert(item: CookieItem) : Long {
        return cookieDao.insert(item)
    }

    suspend fun delete(item: CookieItem){
        cookieDao.delete(item.id)
    }

    suspend fun changeCookieEnabledState(itemId: Long, isEnabled: Boolean) {
        cookieDao.changeEnabledState(itemId, isEnabled)
    }


    suspend fun deleteAll(){
        cookieDao.deleteAll()
    }

    suspend fun update(item: CookieItem){
        cookieDao.update(item)
    }

}