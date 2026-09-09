package com.xiaoshuo.yijianhuanming.content.epub

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheManagerPolicyTest {
    @Test
    fun defaults_to_512_mib_and_removes_incomplete_sessions_on_startup_cleanup() {
        assertTrue(CacheManager.DEFAULT_MAX_BYTES == 512L * 1024 * 1024)
        val root = Files.createTempDirectory("epub-cache").toFile()
        val incomplete = root.resolve("crashed.incomplete").apply {
            mkdirs()
            resolve("partial.bin").writeBytes(ByteArray(32))
        }
        val complete = root.resolve("complete").apply {
            mkdirs()
            resolve("book.bin").writeBytes(ByteArray(32))
        }

        CacheManager(root).cleanup()

        assertFalse(incomplete.exists())
        assertTrue(complete.exists())
        root.deleteRecursively()
    }

    @Test
    fun evicts_complete_sessions_in_least_recently_used_order() {
        val root = Files.createTempDirectory("epub-cache-lru").toFile()
        val old = root.resolve("old").apply {
            mkdirs()
            resolve("book.bin").writeBytes(ByteArray(8))
            setLastModified(100)
        }
        val recent = root.resolve("recent").apply {
            mkdirs()
            resolve("book.bin").writeBytes(ByteArray(8))
            setLastModified(200)
        }

        CacheManager(root, maxBytes = 8).cleanup()

        assertFalse(old.exists())
        assertTrue(recent.exists())
        root.deleteRecursively()
    }
}
