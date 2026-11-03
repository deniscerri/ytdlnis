package com.deniscerri.ytdl.database.repository

import android.app.Application
import com.deniscerri.ytdl.database.dao.CookieDao
import com.deniscerri.ytdl.database.models.CookieItem
import com.deniscerri.ytdl.util.CookieFileBuilder
import com.deniscerri.ytdl.util.FileUtil
import kotlinx.coroutines.flow.Flow
import java.io.File

class CookieRepository(private val cookieDao: CookieDao, private val application: Application) {

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

    fun updateCookieFile() {
        val cookies = getAllEnabled()
        val cookieTXT = CookieFileBuilder.build(cookies.map { it.content })
        FileUtil.getCookieFile(application, true){ c ->
            File(c).writeText(cookieTXT)
        }

        //clear info jsons after updating cookies
        val infoJsonPath = FileUtil.getInfoJsonPath(application)
        File(infoJsonPath).deleteRecursively()
    }

    companion object {
        /**
         * The Netscape header without the trailing newline. It is used as the prefix of
         * the text that is copied to and pasted from the clipboard, so it has to stay
         * identical to the header of the generated cookie file.
         */
        val cookieHeader =
            CookieFileBuilder.HEADER.trimEnd('\n')
    }
}
