package com.xiaoshuo.yijianhuanming.content.txt

object TxtLimits {
    const val SAMPLE_BYTES = 64 * 1024
    const val CHUNK_BYTES = 128 * 1024
    const val MAX_FILE_BYTES = 20L * 1024 * 1024

    fun requireSupportedFileSize(size: Long) {
        if (size < 0L || size > MAX_FILE_BYTES) {
            throw TxtLimitExceededException("TXT 文件不能超过 20 MiB")
        }
    }
}

class TxtLimitExceededException(message: String) : IllegalArgumentException(message)
