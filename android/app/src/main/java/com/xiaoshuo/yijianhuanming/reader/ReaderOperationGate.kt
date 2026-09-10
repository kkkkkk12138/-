package com.xiaoshuo.yijianhuanming.reader

data class ReaderOperationToken(
    val readerSessionId: String,
    val runtimeGeneration: Long,
    val applyGeneration: Long?,
)

class ReaderOperationGate {
    private var readerSessionId: String = ""
    private var runtimeGeneration: Long = 0
    private var applyGeneration: Long = 0

    @Synchronized
    fun beginSession(sessionId: String): ReaderOperationToken {
        require(sessionId.isNotBlank()) { "Reader session id must not be blank" }
        readerSessionId = sessionId
        runtimeGeneration = 0
        applyGeneration = 0
        return currentRuntimeToken()
    }

    @Synchronized
    fun beginRuntime(): ReaderOperationToken {
        check(readerSessionId.isNotEmpty()) { "Reader session must start before runtime" }
        runtimeGeneration += 1
        applyGeneration = 0
        return currentRuntimeToken()
    }

    @Synchronized
    fun beginApply(): ReaderOperationToken {
        check(runtimeGeneration > 0) { "Runtime must start before applying rules" }
        applyGeneration += 1
        return ReaderOperationToken(
            readerSessionId = readerSessionId,
            runtimeGeneration = runtimeGeneration,
            applyGeneration = applyGeneration,
        )
    }

    @Synchronized
    fun isCurrent(token: ReaderOperationToken): Boolean =
        token.readerSessionId == readerSessionId &&
            token.runtimeGeneration == runtimeGeneration &&
            (token.applyGeneration == null || token.applyGeneration == applyGeneration)

    private fun currentRuntimeToken() = ReaderOperationToken(
        readerSessionId = readerSessionId,
        runtimeGeneration = runtimeGeneration,
        applyGeneration = null,
    )
}
