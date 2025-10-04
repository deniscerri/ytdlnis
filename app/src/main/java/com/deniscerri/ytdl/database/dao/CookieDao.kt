package com.deniscerri.ytdl.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.deniscerri.ytdl.database.models.CookieItem
import kotlinx.coroutines.flow.Flow

@Dao
interface CookieDao {
    @Query("SELECT * FROM cookies ORDER BY id DESC")
    fun getAllCookies() : List<CookieItem>

    @Query("SELECT * FROM cookies WHERE enabled = 1 ORDER BY id DESC")
    fun getAllEnabledCookies() : List<CookieItem>

    @Query("SELECT * FROM cookies WHERE url = :url LIMIT 1")
    fun getByURL(url: String) : CookieItem?

    @Query("SELECT * FROM cookies WHERE url=:url AND description=:description LIMIT 1")
    fun getByURLDescription(url: String, description: String) : CookieItem?

    @Query("SELECT * FROM cookies ORDER BY id DESC")
    fun getAllCookiesFlow() : Flow<List<CookieItem>>

    @Query("SELECT EXISTS(SELECT * FROM cookies WHERE url=:url LIMIT 1)")
    fun checkIfExistsWithSameURL(url: String) : Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: CookieItem) : Long

    @Query("DELETE FROM cookies")
    suspend fun deleteAll()

    @Query("DELETE FROM cookies WHERE id=:itemId")
    suspend fun delete(itemId: Long)

    @Query("UPDATE cookies set enabled=:enabled where id=:id")
    suspend fun changeEnabledState(id: Long, enabled: Boolean)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: CookieItem)
}