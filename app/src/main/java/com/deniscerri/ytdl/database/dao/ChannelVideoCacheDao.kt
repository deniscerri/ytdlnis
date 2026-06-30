package com.deniscerri.ytdl.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deniscerri.ytdl.database.models.ChannelVideoCache

@Dao
interface ChannelVideoCacheDao {
    @Query("SELECT * FROM channelVideoCache WHERE channelUrl = :channelUrl LIMIT 1")
    fun get(channelUrl: String) : ChannelVideoCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(item: ChannelVideoCache)

    @Query("DELETE FROM channelVideoCache WHERE channelUrl = :channelUrl")
    fun delete(channelUrl: String)

    @Query("DELETE FROM channelVideoCache")
    fun deleteAll()
}
