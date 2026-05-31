package com.andcodedit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's Room database. Holds persisted editor buffers ([ProjectFile]).
 * Obtain the singleton via [get].
 */
@Database(
    entities = [ProjectFile::class],
    version = 1,
    exportSchema = false
)
abstract class AndCodeDatabase : RoomDatabase() {

    abstract fun projectFileDao(): ProjectFileDao

    companion object {
        @Volatile
        private var INSTANCE: AndCodeDatabase? = null

        fun get(context: Context): AndCodeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AndCodeDatabase::class.java,
                    "andcodedit.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
