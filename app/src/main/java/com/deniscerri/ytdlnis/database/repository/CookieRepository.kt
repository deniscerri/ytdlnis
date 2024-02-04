package com.deniscerri.ytdlnis.database.repository

import com.deniscerri.ytdlnis.database.dao.CookieDao
import com.deniscerri.ytdlnis.database.models.CookieItem
import kotlinx.coroutines.flow.Flow

class CookieRepository(private val cookieDao: CookieDao) {
    val items : Flow<List<CookieItem>> = cookieDao.getAllCookiesFlow()

    fun getAll() : List<CookieItem> {
        return cookieDao.getAllCookies()
    }

    fun getByURL(url: String) : CookieItem {
        return cookieDao.getByURL(url)
    }


    suspend fun insert(item: CookieItem) : Long{
        if (! cookieDao.checkIfExistsWithSameURL(item.url)){
            return cookieDao.insert(item)
        }
        return -1
    }

    suspend fun delete(item: CookieItem){
        cookieDao.delete(item.id)
    }


    suspend fun deleteAll(){
        cookieDao.deleteAll()
    }

    suspend fun update(item: CookieItem){
        cookieDao.update(item)
    }

}