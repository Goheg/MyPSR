package com.example.mypsr.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.mypsr.data.local.dao.SessionDao
import com.example.mypsr.data.local.dao.StepDao
import com.example.mypsr.data.local.entity.SessionEntity
import com.example.mypsr.data.local.entity.StepEntity

@Database(
    entities = [SessionEntity::class, StepEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun stepDao(): StepDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mobile_psr.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
