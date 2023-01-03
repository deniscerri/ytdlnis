package com.deniscerri.ytdlnis.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.deniscerri.ytdlnis.database.dao.HistoryDao
import com.deniscerri.ytdlnis.database.dao.ResultDao
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.models.ResultItem

@Database(
    entities = [ResultItem::class, HistoryItem::class],
    version = 7,
    autoMigrations = []
)
abstract class DBManager : RoomDatabase(){
    abstract val resultDao : ResultDao
    abstract val historyDao : HistoryDao

    companion object {
        @Volatile
        private var instance : DBManager? = null

        fun getInstance(context: Context) : DBManager {
            if (instance == null){
                synchronized(this){
                    instance = buildDatabase(context)
                }
            }
            return instance!!
        }

        private fun buildDatabase(context: Context) : DBManager {
            return Room.databaseBuilder(
                context.applicationContext,
                DBManager::class.java, "ytdlnis_db"
            )
                .build();
        }
    }

}