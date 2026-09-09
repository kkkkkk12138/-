package com.xiaoshuo.yijianhuanming.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun replaceAll_is_atomic_when_insert_fails() = runBlocking {
        val dao = database.ruleDao()
        dao.replaceAll(listOf(RuleEntity("old", "旧名", "新名", 0)))

        val failure = runCatching {
            dao.replaceAll(
                listOf(
                    RuleEntity("duplicate", "甲", "乙", 0),
                    RuleEntity("duplicate", "丙", "丁", 1),
                ),
            )
        }.exceptionOrNull()

        requireNotNull(failure)
        assertEquals(listOf("old"), dao.observeAll().first().map { it.id })
    }

    @Test
    fun replaceAll_accepts_empty_rules() = runBlocking {
        val dao = database.ruleDao()
        dao.replaceAll(listOf(RuleEntity("old", "旧名", "新名", 0)))

        dao.replaceAll(emptyList())

        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun rules_follow_ui_position_not_runtime_match_length() = runBlocking {
        val dao = database.ruleDao()
        dao.replaceAll(
            listOf(
                RuleEntity("long", "沈清辞", "B", 1),
                RuleEntity("short", "沈清", "A", 0),
            ),
        )

        assertEquals(listOf("short", "long"), dao.observeAll().first().map { it.id })
    }

    @Test
    fun reader_sessions_keep_location_and_sort_most_recent_first() = runBlocking {
        val dao = database.readerSessionDao()
        dao.upsert(
            ReaderSessionEntity(
                sourceId = "older",
                type = "EPUB",
                title = "旧书",
                uri = "content://books/older.epub",
                chapterId = "chapter-7",
                scrollRatio = 0.42,
                lastOpenedAt = 100,
            ),
        )
        dao.upsert(
            ReaderSessionEntity(
                sourceId = "newer",
                type = "TXT",
                title = "新书",
                uri = "content://books/newer.txt",
                chapterId = null,
                scrollRatio = 0.75,
                lastOpenedAt = 200,
            ),
        )

        val sessions = dao.observeRecent().first()

        assertEquals(listOf("newer", "older"), sessions.map { it.sourceId })
        assertEquals("chapter-7", sessions[1].chapterId)
        assertEquals(0.42, sessions[1].scrollRatio, 0.0)
    }
}
