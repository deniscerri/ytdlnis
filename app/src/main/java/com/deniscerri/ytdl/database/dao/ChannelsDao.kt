package com.deniscerri.ytdl.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.deniscerri.ytdl.database.models.ChannelItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelsDao {
    @Query("SELECT * FROM channels ORDER BY id DESC")
    fun getAllChannels() : List<ChannelItem>

    @Query("SELECT * FROM channels WHERE url = :url LIMIT 1")
    fun getByURL(url: String) : ChannelItem

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    fun getByID(id: Long) : ChannelItem

    @Query("SELECT * FROM channels ORDER BY id DESC")
    fun getAllChannelsFlow() : Flow<List<ChannelItem>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: ChannelItem) : Long

    @Query("DELETE FROM channels")
    suspend fun deleteAll()

    @Query("DELETE FROM channels WHERE id=:itemId")
    suspend fun delete(itemId: Long)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: ChannelItem)
}
