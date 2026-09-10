package com.xiaoshuo.yijianhuanming.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoshuo.yijianhuanming.data.ReplaceRule
import com.xiaoshuo.yijianhuanming.data.RuleRepository
import com.xiaoshuo.yijianhuanming.data.normalizeRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class RuleEditorState(
    val persisted: List<ReplaceRule> = emptyList(),
    val draft: List<ReplaceRule> = emptyList(),
    val editingRuleId: String? = null,
    val isApplying: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ReaderViewModel internal constructor(
    private val repository: RuleRepository,
    collectionScope: CoroutineScope?,
) : ViewModel() {
    @Inject constructor(repository: RuleRepository) : this(repository, null)

    private val mutableState = MutableStateFlow(RuleEditorState())
    val state: StateFlow<RuleEditorState> = mutableState.asStateFlow()
    private var hasLocalDraft = false

    init {
        (collectionScope ?: viewModelScope).launch {
            repository.rules.collect { persisted ->
                mutableState.update { current ->
                    current.copy(
                        persisted = persisted,
                        draft = if (hasLocalDraft) current.draft else persisted,
                    )
                }
            }
        }
    }

    fun editRule(id: String) {
        mutableState.update { it.copy(editingRuleId = id, error = null) }
    }

    fun changeRule(id: String, source: String, target: String) {
        hasLocalDraft = true
        mutableState.update { current ->
            current.copy(
                draft = current.draft.map { rule ->
                    if (rule.id == id) rule.copy(source = source, target = target) else rule
                },
                error = null,
            )
        }
    }

    fun addRule() {
        hasLocalDraft = true
        val rule = ReplaceRule(
            id = UUID.randomUUID().toString(),
            source = "",
            target = "",
            order = mutableState.value.draft.size,
        )
        mutableState.update {
            it.copy(draft = it.draft + rule, editingRuleId = rule.id, error = null)
        }
    }

    fun deleteRule(id: String) {
        hasLocalDraft = true
        mutableState.update { current ->
            current.copy(
                draft = current.draft
                    .filterNot { it.id == id }
                    .mapIndexed { index, rule -> rule.copy(order = index) },
                editingRuleId = current.editingRuleId.takeUnless { it == id },
                error = null,
            )
        }
    }

    suspend fun applyAll(runtime: RuleRuntime) {
        val draft = normalizeRules(mutableState.value.draft)
        mutableState.update { it.copy(isApplying = true, error = null) }
        val save = runCatching { repository.replaceAll(draft) }
        if (save.isFailure) {
            mutableState.update {
                it.copy(isApplying = false, error = save.exceptionOrNull()?.message ?: "保存规则失败")
            }
            return
        }

        val runtimeResult = if (draft.isEmpty()) {
            runtime.restoreOriginalText()
        } else {
            runtime.applyRules(draft)
        }
        if (runtimeResult.isFailure) {
            mutableState.update {
                it.copy(
                    persisted = draft,
                    isApplying = false,
                    error = runtimeResult.exceptionOrNull()?.message ?: "网页换名失败",
                )
            }
            return
        }

        hasLocalDraft = false
        mutableState.update {
            it.copy(
                persisted = draft,
                draft = draft,
                editingRuleId = null,
                isApplying = false,
                error = null,
            )
        }
    }

    suspend fun reapplyPersisted(runtime: RuleRuntime): Result<Unit> {
        val rules = mutableState.value.persisted
        return if (rules.isEmpty()) {
            runtime.restoreOriginalText()
        } else {
            runtime.applyRules(rules).map {}
        }
    }

    suspend fun clearRules(runtime: RuleRuntime): Result<Unit> {
        val saved = runCatching { repository.replaceAll(emptyList()) }
        if (saved.isFailure) return saved
        val restored = runtime.restoreOriginalText()
        if (restored.isSuccess) {
            hasLocalDraft = false
            mutableState.value = RuleEditorState()
        }
        return restored
    }
}
