package com.deniscerri.ytdl.database.repository

import com.deniscerri.ytdl.database.dao.ChannelsDao
import com.deniscerri.ytdl.database.models.ChannelItem
import kotlinx.coroutines.flow.Flow

class ChannelsRepository(private val channelsDao: ChannelsDao) {
    val items : Flow<List<ChannelItem>> = channelsDao.getAllChannelsFlow()

    fun getAll() : List<ChannelItem> {
        return channelsDao.getAllChannels()
    }

    fun getByURL(url: String) : ChannelItem {
        return channelsDao.getByURL(url)
    }

    fun getByID(id: Long) : ChannelItem {
        return channelsDao.getByID(id)
    }

    suspend fun insert(item: ChannelItem) : Long {
        // The url column has a UNIQUE index, so a duplicate is ignored at the db level
        // (OnConflictStrategy.IGNORE) and insert() returns -1 — no read-then-insert race.
        return channelsDao.insert(item)
    }

    suspend fun delete(item: ChannelItem) {
        channelsDao.delete(item.id)
    }

    suspend fun deleteAll() {
        channelsDao.deleteAll()
    }

    suspend fun update(item: ChannelItem) {
        channelsDao.update(item)
    }
}
