package com.deniscerri.ytdlnis.database.dao

import androidx.room.*
import com.deniscerri.ytdlnis.database.models.CookieItem
import com.deniscerri.ytdlnis.database.models.ObserveSourcesItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ObserveSourcesDao {
    @Query("SELECT * FROM observeSources ORDER BY id DESC")
    fun getAllSources() : List<ObserveSourcesItem>

    @Query("SELECT * FROM observeSources WHERE url = :url LIMIT 1")
    fun getByURL(url: String) : ObserveSourcesItem

    @Query("SELECT * FROM observeSources WHERE id = :id LIMIT 1")
    fun getByID(id: Long) : ObserveSourcesItem

    @Query("SELECT * FROM observeSources ORDER BY id DESC")
    fun getAllSourcesFlow() : Flow<List<ObserveSourcesItem>>

    @Query("SELECT EXISTS(SELECT * FROM observeSources WHERE url=:url LIMIT 1)")
    fun checkIfExistsWithSameURL(url: String) : Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: ObserveSourcesItem) : Long

    @Query("DELETE FROM observeSources")
    suspend fun deleteAll()

    @Query("DELETE FROM observeSources WHERE id=:itemId")
    suspend fun delete(itemId: Long)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: ObserveSourcesItem)
}