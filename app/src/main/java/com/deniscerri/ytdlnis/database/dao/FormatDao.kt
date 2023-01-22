package com.deniscerri.ytdlnis.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.ResultItem

@Dao
interface FormatDao {
    @Query("SELECT * FROM formats WHERE itemId=:itemId")
    fun getFormatsByItemId(itemId: Int) : List<Format>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Format)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: Format)

    @Query("DELETE FROM formats")
    suspend fun deleteAll()

    @Query("DELETE FROM formats WHERE itemId=:itemId")
    suspend fun deleteFromatsByItemId(itemId: Int)

}