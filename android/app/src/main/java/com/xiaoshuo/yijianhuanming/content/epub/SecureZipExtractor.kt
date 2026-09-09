package com.xiaoshuo.yijianhuanming.content.epub

import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ExtractionResult(
    val entryCount: Int,
    val totalBytes: Long,
    val files: Set<String>,
)

class SecureZipExtractor(
    private val limits: EpubLimits = EpubLimits.DEFAULT,
) {
    fun extract(source: InputStream, sessionDir: File): ExtractionResult {
        var succeeded = false
        try {
            if (sessionDir.exists()) {
                throw EpubValidationException("EPUB 会话目录已存在")
            }
            if (!sessionDir.mkdirs()) {
                throw EpubValidationException("无法创建 EPUB 会话目录")
            }
            val root = sessionDir.canonicalFile
            val bounded = ArchiveLimitInputStream(source, limits.maxArchiveBytes)
            val zip = MeteredZipInputStream(bounded)
            var entries = 0
            var totalBytes = 0L
            val files = linkedSetOf<String>()

            zip.use {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries += 1
                    if (entries > limits.maxEntries) {
                        throw EpubValidationException("EPUB 条目数超过限制")
                    }
                    val relativeName = validateEntryName(entry.name)
                    val target = File(root, relativeName).canonicalFile
                    if (!target.path.startsWith(root.path + File.separator)) {
                        throw EpubValidationException("EPUB 条目路径越界")
                    }
                    if (entry.isDirectory) {
                        if (!target.mkdirs() && !target.isDirectory) {
                            throw EpubValidationException("无法创建 EPUB 目录")
                        }
                    } else {
                        target.parentFile?.let { parent ->
                            if (!parent.mkdirs() && !parent.isDirectory) {
                                throw EpubValidationException("无法创建 EPUB 目录")
                            }
                        }
                        val entryBytes = copyEntry(zip, target) { copied ->
                            if (copied > limits.maxEntryBytes) {
                                throw EpubValidationException("EPUB 单个资源超过限制")
                            }
                            if (totalBytes + copied > limits.maxTotalBytes) {
                                throw EpubValidationException("EPUB 解压总量超过限制")
                            }
                        }
                        totalBytes += entryBytes
                        enforceCompressionRatio(entry, zip.compressedBytesRead(), entryBytes)
                        files += relativeName
                    }
                    zip.closeEntry()
                }
            }
            succeeded = true
            return ExtractionResult(entries, totalBytes, files)
        } catch (error: EpubValidationException) {
            throw error
        } catch (error: Exception) {
            throw EpubValidationException("EPUB ZIP 无效", error)
        } finally {
            if (!succeeded) {
                sessionDir.deleteRecursively()
            }
        }
    }

    fun validateEntryName(name: String): String {
        if (name.isEmpty() || '\u0000' in name) {
            throw EpubValidationException("EPUB 条目名称无效")
        }
        if (name.startsWith("/") || name.startsWith("\\") || WINDOWS_ABSOLUTE.matches(name)) {
            throw EpubValidationException("EPUB 不允许绝对路径")
        }
        val normalized = name.replace('\\', '/')
        if (normalized.split('/').any { it == ".." }) {
            throw EpubValidationException("EPUB 条目包含路径穿越")
        }
        return normalized
    }

    private fun copyEntry(
        zip: ZipInputStream,
        target: File,
        checkBytes: (Long) -> Unit,
    ): Long {
        var copied = 0L
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        FileOutputStream(target).use { output ->
            while (true) {
                val count = zip.read(buffer)
                if (count < 0) break
                copied += count
                checkBytes(copied)
                output.write(buffer, 0, count)
            }
        }
        return copied
    }

    private fun enforceCompressionRatio(entry: ZipEntry, measuredCompressed: Long, expanded: Long) {
        if (expanded == 0L) return
        val compressed = entry.compressedSize.takeIf { it > 0L } ?: measuredCompressed
        if (compressed <= 0L) {
            throw EpubValidationException("无法计数 EPUB 压缩数据")
        }
        if (expanded > compressed * limits.maxCompressionRatio.toLong()) {
            throw EpubValidationException("EPUB 单条目压缩比超过限制")
        }
    }

    private class MeteredZipInputStream(source: InputStream) : ZipInputStream(source) {
        fun compressedBytesRead(): Long = inf.bytesRead
    }

    private class ArchiveLimitInputStream(
        source: InputStream,
        private val maximum: Long,
    ) : FilterInputStream(source) {
        private var count = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) add(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val value = super.read(buffer, offset, length)
            if (value > 0) add(value.toLong())
            return value
        }

        private fun add(value: Long) {
            count += value
            if (count > maximum) {
                throw EpubValidationException("EPUB 压缩文件超过限制")
            }
        }
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 32 * 1024
        val WINDOWS_ABSOLUTE = Regex("^[A-Za-z]:[/\\\\].*")
    }
}
