package com.xiaoshuo.yijianhuanming.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RuleDao {
    @Query("SELECT * FROM rules ORDER BY position ASC")
    abstract fun observeAll(): Flow<List<RuleEntity>>

    @Query("DELETE FROM rules")
    protected abstract suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAll(rules: List<RuleEntity>)

    @Transaction
    open suspend fun replaceAll(rules: List<RuleEntity>) {
        deleteAll()
        if (rules.isNotEmpty()) {
            insertAll(rules)
        }
    }
}
