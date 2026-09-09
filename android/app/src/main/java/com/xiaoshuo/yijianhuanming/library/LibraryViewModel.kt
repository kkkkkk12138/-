package com.xiaoshuo.yijianhuanming.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoshuo.yijianhuanming.content.epub.CacheManager
import com.xiaoshuo.yijianhuanming.data.ReaderSessionDao
import com.xiaoshuo.yijianhuanming.data.ReaderSessionEntity
import com.xiaoshuo.yijianhuanming.data.RuleRepository
import com.xiaoshuo.yijianhuanming.reader.RuleRuntime
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface LibraryHistory {
    val recent: Flow<List<ReaderSessionEntity>>
    suspend fun clear()
}

class RoomLibraryHistory @Inject constructor(
    private val dao: ReaderSessionDao,
) : LibraryHistory {
    override val recent: Flow<List<ReaderSessionEntity>> = dao.observeRecent()
    override suspend fun clear() = dao.clear()
}

interface EpubCache {
    fun cleanup()
    fun clear()
}

class AppEpubCache @Inject constructor(
    @ApplicationContext context: Context,
) : EpubCache {
    private val manager = CacheManager(context.cacheDir.resolve("epub"))
    override fun cleanup() = manager.cleanup()
    override fun clear() = manager.clear()
}

data class LibraryState(
    val recent: List<ReaderSessionEntity> = emptyList(),
    val operationInProgress: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LibraryViewModel internal constructor(
    private val history: LibraryHistory,
    private val rules: RuleRepository,
    private val epubCache: EpubCache,
    collectionScope: CoroutineScope?,
) : ViewModel() {
    @Inject constructor(
        history: RoomLibraryHistory,
        rules: RuleRepository,
        epubCache: AppEpubCache,
    ) : this(history, rules, epubCache, null)

    private val mutableState = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = mutableState.asStateFlow()
    private val scope = collectionScope ?: viewModelScope

    init {
        epubCache.cleanup()
        scope.launch {
            history.recent.collect { recent ->
                mutableState.update { it.copy(recent = recent) }
            }
        }
    }

    suspend fun clearHistory() = runOperation { history.clear() }

    suspend fun clearEpubCache() = runOperation { epubCache.clear() }

    suspend fun clearRules(runtime: RuleRuntime?) = runOperation {
        rules.replaceAll(emptyList())
        runtime?.restoreOriginalText()?.getOrThrow()
    }

    private suspend fun runOperation(block: suspend () -> Unit) {
        mutableState.update { it.copy(operationInProgress = true, error = null) }
        runCatching { block() }
            .onSuccess {
                mutableState.update { it.copy(operationInProgress = false) }
            }
            .onFailure { failure ->
                mutableState.update {
                    it.copy(
                        operationInProgress = false,
                        error = failure.message ?: "操作失败",
                    )
                }
            }
    }
}
