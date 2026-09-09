package com.xiaoshuo.yijianhuanming.content.txt

import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtChunkStoreTest {
    @Test
    fun escapes_text_and_preserves_chunk_sequence_without_duplicates() {
        val store = TxtChunkStore(Files.createTempDirectory("txt-chunks").toFile())
        val input = "<script>沈清辞&\"'</script>"

        val manifest = store.writeSession("escape", StringReader(input), maxChunkBytes = 12)

        assertEquals(
            "&lt;script&gt;沈清辞&amp;&quot;&#39;&lt;/script&gt;",
            manifest.chunks.joinToString("") { it.file.readText() },
        )
        assertEquals(input.length.toLong(), manifest.totalCharacterOffset)
        assertEquals(0L, manifest.chunks.first().startCharacterOffset)
        manifest.chunks.zipWithNext().forEach { (left, right) ->
            assertEquals(left.endCharacterOffset, right.startCharacterOffset)
        }
    }

    @Test
    fun never_splits_a_utf16_surrogate_pair_between_chunks() {
        val store = TxtChunkStore(Files.createTempDirectory("txt-surrogate").toFile())

        val manifest = store.writeSession("emoji", StringReader("甲😀乙"), maxChunkBytes = 4)
        val chunks = manifest.chunks.map { it.file.readText() }

        assertEquals("甲😀乙", chunks.joinToString(""))
        chunks.forEach { chunk ->
            assertFalse(chunk.firstOrNull()?.isLowSurrogate() == true)
            assertFalse(chunk.lastOrNull()?.isHighSurrogate() == true)
        }
    }

    @Test
    fun rejects_files_larger_than_20_mib_but_accepts_the_boundary() {
        assertEquals(20L * 1024 * 1024, TxtLimits.MAX_FILE_BYTES)
        TxtLimits.requireSupportedFileSize(TxtLimits.MAX_FILE_BYTES)

        val failure = runCatching {
            TxtLimits.requireSupportedFileSize(TxtLimits.MAX_FILE_BYTES + 1)
        }.exceptionOrNull()

        assertTrue(failure is TxtLimitExceededException)
    }

    @Test
    fun removes_partial_session_when_chunking_fails() {
        val root = Files.createTempDirectory("txt-failure").toFile()
        val store = TxtChunkStore(root)
        val failingReader = object : Reader() {
            private var emitted = false

            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                if (!emitted) {
                    "partial".toCharArray().copyInto(buffer, offset)
                    emitted = true
                    return "partial".length
                }
                throw IOException("source failed")
            }

            override fun close() = Unit
        }

        val failure = runCatching {
            store.writeSession("broken", failingReader, maxChunkBytes = 8)
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertFalse(root.resolve("broken").exists())
    }

    @Test
    fun production_chunk_limit_is_128_kib() {
        assertEquals(128 * 1024, TxtLimits.CHUNK_BYTES)
    }
}
