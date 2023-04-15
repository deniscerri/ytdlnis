package com.deniscerri.ytdlnis.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.deniscerri.ytdlnis.database.models.CookieItem

@Dao
interface CookieDao {
    @Query("SELECT * FROM cookies ORDER BY id DESC")
    fun getAllCookies() : List<CookieItem>

    @Query("SELECT * FROM cookies ORDER BY id DESC")
    fun getAllCookiesLiveData() : LiveData<List<CookieItem>>

    @Query("SELECT EXISTS(SELECT * FROM cookies WHERE url=:url LIMIT 1)")
    fun checkIfExistsWithSameURL(url: String) : Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: CookieItem) : Long

    @Query("DELETE FROM cookies")
    suspend fun deleteAll()

    @Query("DELETE FROM cookies WHERE id=:itemId")
    suspend fun delete(itemId: Long)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: CookieItem)
}