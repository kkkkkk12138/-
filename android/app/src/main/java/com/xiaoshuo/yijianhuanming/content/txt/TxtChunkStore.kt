package com.xiaoshuo.yijianhuanming.content.txt

import java.io.File
import java.io.Reader
import java.nio.charset.StandardCharsets

data class TxtChunk(
    val index: Int,
    val file: File,
    val startCharacterOffset: Long,
    val endCharacterOffset: Long,
)

data class TxtChunkManifest(
    val sessionId: String,
    val chunks: List<TxtChunk>,
    val totalCharacterOffset: Long,
)

class TxtChunkStore(
    private val root: File,
) {
    fun writeSession(
        sessionId: String,
        reader: Reader,
        maxChunkBytes: Int = TxtLimits.CHUNK_BYTES,
    ): TxtChunkManifest {
        require(sessionId.matches(SESSION_ID)) { "Invalid TXT session id" }
        require(maxChunkBytes > 0) { "Chunk size must be positive" }
        root.mkdirs()
        val target = root.resolve(sessionId)
        val staging = root.resolve(".$sessionId.incomplete")
        staging.deleteRecursively()
        target.deleteRecursively()
        check(staging.mkdirs()) { "Cannot create TXT session" }

        return try {
            val chunks = writeChunks(staging, reader, maxChunkBytes)
            check(staging.renameTo(target)) { "Cannot publish TXT session" }
            TxtChunkManifest(
                sessionId = sessionId,
                chunks = chunks.map { it.copy(file = target.resolve(it.file.name)) },
                totalCharacterOffset = chunks.lastOrNull()?.endCharacterOffset ?: 0L,
            )
        } catch (error: Throwable) {
            staging.deleteRecursively()
            target.deleteRecursively()
            throw error
        }
    }

    fun closeSession(sessionId: String) {
        if (sessionId.matches(SESSION_ID)) root.resolve(sessionId).deleteRecursively()
    }

    private fun writeChunks(
        directory: File,
        reader: Reader,
        maxChunkBytes: Int,
    ): List<TxtChunk> {
        val chunks = mutableListOf<TxtChunk>()
        val escaped = StringBuilder()
        var escapedBytes = 0
        var chunkStart = 0L
        var characterOffset = 0L
        var pendingHighSurrogate: Char? = null
        val input = CharArray(8 * 1024)

        fun flush() {
            if (escaped.isEmpty()) return
            val index = chunks.size
            val file = directory.resolve("%06d.html".format(index))
            file.writeText(escaped.toString(), StandardCharsets.UTF_8)
            chunks += TxtChunk(index, file, chunkStart, characterOffset)
            escaped.clear()
            escapedBytes = 0
            chunkStart = characterOffset
        }

        fun appendCodePoint(codePoint: Int) {
            val rawWidth = Character.charCount(codePoint)
            val token = escapeCodePoint(codePoint)
            val tokenBytes = token.toByteArray(StandardCharsets.UTF_8).size
            if (escapedBytes > 0 && escapedBytes + tokenBytes > maxChunkBytes) flush()
            escaped.append(token)
            escapedBytes += tokenBytes
            characterOffset += rawWidth
        }

        while (true) {
            val count = reader.read(input)
            if (count < 0) break
            var index = 0
            pendingHighSurrogate?.let { high ->
                if (count > 0 && input[0].isLowSurrogate()) {
                    appendCodePoint(Character.toCodePoint(high, input[0]))
                    index = 1
                } else {
                    appendCodePoint(high.code)
                }
                pendingHighSurrogate = null
            }
            while (index < count) {
                val current = input[index]
                if (current.isHighSurrogate() && index + 1 == count) {
                    pendingHighSurrogate = current
                    break
                }
                if (current.isHighSurrogate() && input[index + 1].isLowSurrogate()) {
                    appendCodePoint(Character.toCodePoint(current, input[index + 1]))
                    index += 2
                } else {
                    appendCodePoint(current.code)
                    index += 1
                }
            }
        }
        pendingHighSurrogate?.let { appendCodePoint(it.code) }
        flush()
        return chunks
    }

    private fun escapeCodePoint(codePoint: Int): String = when (codePoint) {
        '<'.code -> "&lt;"
        '>'.code -> "&gt;"
        '&'.code -> "&amp;"
        '"'.code -> "&quot;"
        '\''.code -> "&#39;"
        else -> String(Character.toChars(codePoint))
    }

    private companion object {
        val SESSION_ID = Regex("[A-Za-z0-9_-]{1,80}")
    }
}
