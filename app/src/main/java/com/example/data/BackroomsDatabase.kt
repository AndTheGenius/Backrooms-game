package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RunRecordEntity::class, DiscoveredEntryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BackroomsDatabase : RoomDatabase() {
    abstract fun runRecordDao(): RunRecordDao
    abstract fun discoveredEntryDao(): DiscoveredEntryDao

    companion object {
        @Volatile
        private var INSTANCE: BackroomsDatabase? = null

        fun getInstance(context: Context): BackroomsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BackroomsDatabase::class.java,
                    "backrooms_null_sector.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
