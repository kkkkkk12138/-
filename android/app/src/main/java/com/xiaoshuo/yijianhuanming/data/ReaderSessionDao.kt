package com.xiaoshuo.yijianhuanming.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderSessionDao {
    @Upsert
    suspend fun upsert(session: ReaderSessionEntity)

    @Query("SELECT * FROM reader_sessions ORDER BY lastOpenedAt DESC")
    fun observeRecent(): Flow<List<ReaderSessionEntity>>

    @Query("SELECT * FROM reader_sessions WHERE sourceId = :sourceId LIMIT 1")
    suspend fun findBySourceId(sourceId: String): ReaderSessionEntity?

    @Query("DELETE FROM reader_sessions")
    suspend fun clear()
}
