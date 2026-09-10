package com.xiaoshuo.yijianhuanming.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RuleEntity::class, ReaderSessionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao

    abstract fun readerSessionDao(): ReaderSessionDao
}
