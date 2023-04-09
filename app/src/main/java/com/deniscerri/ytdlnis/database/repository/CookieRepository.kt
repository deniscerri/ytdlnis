package com.deniscerri.ytdlnis.database.repository

import androidx.lifecycle.LiveData
import com.deniscerri.ytdlnis.database.dao.CookieDao
import com.deniscerri.ytdlnis.database.models.CookieItem

class CookieRepository(private val cookieDao: CookieDao) {
    val items : LiveData<List<CookieItem>> = cookieDao.getAllCookiesLiveData()

    fun getAll() : List<CookieItem> {
        return cookieDao.getAllCookies()
    }


    suspend fun insert(item: CookieItem){
        try{
            cookieDao.checkIfExistsWithSameURL(item.url)
        }catch(e: Exception){
            cookieDao.insert(item)
        }
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