package com.deniscerri.ytdlnis.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.dao.HistoryDao
import com.deniscerri.ytdlnis.database.dao.ResultDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.models.ResultItem

@Database(
    entities = [ResultItem::class, HistoryItem::class, DownloadItem::class],
    version = 1,
    autoMigrations = []
)
abstract class DBManager : RoomDatabase(){
    abstract val resultDao : ResultDao
    abstract val historyDao : HistoryDao
    abstract val downloadDao : DownloadDao

    companion object {
        //prevents multiple instances of db getting created at the same time
        @Volatile
        private var instance : DBManager? = null
        //if its not null return it, otherwise create db
        fun getInstance(context: Context) : DBManager {
            return instance ?: synchronized(this){
                val dbInstance = Room.databaseBuilder(
                    context.applicationContext,
                    DBManager::class.java,
                    "YTDLnisDatabase"
                ).build()
                instance = dbInstance
                dbInstance
            }
        }
    }

}