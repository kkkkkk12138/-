package com.xiaoshuo.yijianhuanming.library

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingProgressCoordinatorTest {
    @Test
    fun txt_helpers_restore_exact_offset_only_when_source_total_is_unchanged() {
        assertEquals(0.37, txtRatio(3_700, 10_000), 0.0)
        assertEquals(3_700L, restoreTxtOffset(3_700, 10_000, 0.12, 10_000))
        assertEquals(5_000L, restoreTxtOffset(3_700, 10_000, 0.50, 10_001))
        assertEquals(0L, restoreTxtOffset(null, null, 0.0, 0))
        assertEquals(10_000L, restoreTxtOffset(20_000, 10_000, 0.0, 10_000))
    }

    @Test
    fun stale_sequence_is_discarded_after_a_newer_callback_was_saved() = runBlocking {
        val writes = mutableListOf<ReadingProgress>()
        val coordinator = ReadingProgressCoordinator { writes += it }
        val oldSequence = coordinator.beginSequence()
        val newSequence = coordinator.beginSequence()

        assertTrue(coordinator.save(newSequence, progress(offset = 2)))
        assertFalse(coordinator.save(oldSequence, progress(offset = 1)))

        assertEquals(listOf(2L), writes.map { it.textOffset })
        assertEquals(2L, coordinator.latestConfirmed()?.textOffset)
    }

    @Test
    fun room_writes_are_serial_and_newest_sequence_wins() = runBlocking {
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val active = AtomicInteger()
        var maxActive = 0
        val writes = mutableListOf<Long>()
        val coordinator = ReadingProgressCoordinator { value ->
            maxActive = maxOf(maxActive, active.incrementAndGet())
            if (value.textOffset == 1L) {
                firstWriteStarted.complete(Unit)
                releaseFirstWrite.await()
            }
            writes += value.textOffset
            active.decrementAndGet()
        }

        val first = async { coordinator.save(progress(offset = 1)) }
        firstWriteStarted.await()
        val second = async { coordinator.save(progress(offset = 2)) }
        delay(20)
        assertEquals(1, maxActive)
        releaseFirstWrite.complete(Unit)

        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(listOf(1L, 2L), writes)
        assertEquals(1, maxActive)
        assertEquals(2L, coordinator.latestConfirmed()?.textOffset)
    }

    @Test
    fun close_waits_at_most_budget_then_saves_last_confirmed_position() = runBlocking {
        val writes = mutableListOf<ReadingProgress>()
        val coordinator = ReadingProgressCoordinator { writes += it }
        coordinator.save(progress(offset = 37))

        val startedAt = System.nanoTime()
        val saved = coordinator.saveBeforeClose(
            timeoutMillis = 50,
            totalUtf16Units = 100,
            now = { 200 },
        ) {
            delay(5_000)
            90
        }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertTrue(elapsedMillis < 300)
        assertEquals(37L, saved?.textOffset)
        assertEquals(37L, writes.last().textOffset)
    }

    private fun progress(offset: Long) = ReadingProgress(
        textOffset = offset,
        textTotalAtSave = 100,
        scrollRatio = txtRatio(offset, 100),
        lastOpenedAt = 100,
    )
}
