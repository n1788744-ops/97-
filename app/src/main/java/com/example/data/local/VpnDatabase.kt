package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ServerProfileEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VpnDatabase : RoomDatabase() {
    abstract fun serverProfileDao(): ServerProfileDao

    companion object {
        @Volatile
        private var INSTANCE: VpnDatabase? = null

        fun getInstance(context: Context): VpnDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VpnDatabase::class.java,
                    "cipherguard_vpn.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
