package com.deniscerri.ytdlnis.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.deniscerri.ytdlnis.database.dao.*
import com.deniscerri.ytdlnis.database.models.*

@Database(
    entities = [ResultItem::class, Format::class, HistoryItem::class, DownloadItem::class, CommandTemplate::class],
    version = 1,
    autoMigrations = []
)
abstract class DBManager : RoomDatabase(){
    abstract val resultDao : ResultDao
    abstract val historyDao : HistoryDao
    abstract val formatDao : FormatDao
    abstract val downloadDao : DownloadDao
    abstract val commandTemplateDao : CommandTemplateDao

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
                )
                        .allowMainThreadQueries()
                        .build()
                instance = dbInstance
                dbInstance
            }
        }
    }

}