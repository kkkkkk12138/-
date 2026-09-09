package com.xiaoshuo.yijianhuanming.content.epub

import java.io.File

class CacheManager(
    private val root: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    init {
        require(maxBytes >= 0)
    }

    fun createIncompleteSession(sessionId: String): File {
        requireSessionId(sessionId)
        if (!root.mkdirs() && !root.isDirectory) {
            throw IllegalStateException("无法创建 EPUB 缓存目录")
        }
        val session = root.resolve("$sessionId$INCOMPLETE_SUFFIX")
        session.deleteRecursively()
        if (!session.mkdirs()) {
            throw IllegalStateException("无法创建 EPUB 临时会话")
        }
        return session
    }

    fun commitSession(incomplete: File): File {
        val canonicalRoot = root.canonicalFile
        val source = incomplete.canonicalFile
        if (
            source.parentFile != canonicalRoot ||
            !source.name.endsWith(INCOMPLETE_SUFFIX) ||
            !source.isDirectory
        ) {
            throw IllegalArgumentException("不是受管的 EPUB 临时会话")
        }
        val target = canonicalRoot.resolve(source.name.removeSuffix(INCOMPLETE_SUFFIX))
        target.deleteRecursively()
        if (!source.renameTo(target)) {
            source.deleteRecursively()
            throw IllegalStateException("无法提交 EPUB 缓存会话")
        }
        target.setLastModified(System.currentTimeMillis())
        enforceLimit()
        return target
    }

    fun touch(session: File) {
        val canonicalRoot = root.canonicalFile
        val target = session.canonicalFile
        if (target.parentFile != canonicalRoot || !target.isDirectory) {
            throw IllegalArgumentException("不是受管的 EPUB 会话")
        }
        target.setLastModified(System.currentTimeMillis())
    }

    fun cleanup() {
        root.listFiles()
            .orEmpty()
            .filter { it.name.endsWith(INCOMPLETE_SUFFIX) }
            .forEach(File::deleteRecursively)
        enforceLimit()
    }

    fun clear() {
        root.listFiles().orEmpty().forEach(File::deleteRecursively)
    }

    private fun enforceLimit() {
        val sessions = root.listFiles()
            .orEmpty()
            .filter { it.isDirectory && !it.name.endsWith(INCOMPLETE_SUFFIX) }
            .sortedBy(File::lastModified)
            .toMutableList()
        var total = sessions.sumOf(::sizeOf)
        while (total > maxBytes && sessions.isNotEmpty()) {
            val oldest = sessions.removeAt(0)
            val size = sizeOf(oldest)
            if (oldest.deleteRecursively()) {
                total -= size
            } else {
                break
            }
        }
    }

    private fun sizeOf(file: File): Long =
        if (file.isFile) file.length() else file.listFiles().orEmpty().sumOf(::sizeOf)

    private fun requireSessionId(sessionId: String) {
        require(SESSION_ID.matches(sessionId)) { "EPUB 会话标识无效" }
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 512L * 1024 * 1024
        private const val INCOMPLETE_SUFFIX = ".incomplete"
        private val SESSION_ID = Regex("[A-Za-z0-9._-]+")
    }
}
