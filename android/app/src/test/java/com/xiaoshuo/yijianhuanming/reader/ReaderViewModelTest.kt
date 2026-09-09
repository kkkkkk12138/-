package com.xiaoshuo.yijianhuanming.reader

import com.xiaoshuo.yijianhuanming.data.ReplaceRule
import com.xiaoshuo.yijianhuanming.data.RuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderViewModelTest {
    @Test
    fun room_failure_keeps_the_edited_draft() = runBlocking {
        val repository = FakeRuleRepository(failSave = true)
        val viewModel = ReaderViewModel(repository, testScope())
        yield()
        viewModel.addRule()
        val id = viewModel.state.value.draft.single().id
        viewModel.changeRule(id, "旧名", "新名")

        viewModel.applyAll(FakeRuntime())

        assertEquals("旧名", viewModel.state.value.draft.single().source)
        assertEquals(id, viewModel.state.value.editingRuleId)
        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun javascript_failure_keeps_draft_even_after_room_save() = runBlocking {
        val repository = FakeRuleRepository()
        val viewModel = ReaderViewModel(repository, testScope())
        yield()
        viewModel.addRule()
        val id = viewModel.state.value.draft.single().id
        viewModel.changeRule(id, "旧名", "新名")

        viewModel.applyAll(FakeRuntime(applySuccess = false))

        assertEquals("旧名", viewModel.state.value.draft.single().source)
        assertEquals(id, viewModel.state.value.editingRuleId)
        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun deleting_last_rule_restores_original_and_finishes_editing() = runBlocking {
        val repository = FakeRuleRepository(
            initial = listOf(ReplaceRule("1", "旧名", "新名", 0)),
        )
        val runtime = FakeRuntime()
        val viewModel = ReaderViewModel(repository, testScope())
        yield()
        viewModel.deleteRule("1")

        viewModel.applyAll(runtime)

        assertEquals(1, runtime.restoreCalls)
        assertEquals(emptyList<ReplaceRule>(), viewModel.state.value.draft)
        assertNull(viewModel.state.value.editingRuleId)
        assertNull(viewModel.state.value.error)
    }

    private fun testScope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
}

private class FakeRuleRepository(
    initial: List<ReplaceRule> = emptyList(),
    private val failSave: Boolean = false,
) : RuleRepository {
    private val stored = MutableStateFlow(initial)
    override val rules: Flow<List<ReplaceRule>> = stored

    override suspend fun replaceAll(rules: List<ReplaceRule>) {
        if (failSave) error("room failed")
        stored.value = rules
    }
}

private class FakeRuntime(
    private val applySuccess: Boolean = true,
) : RuleRuntime {
    var restoreCalls = 0

    override suspend fun applyRules(rules: List<ReplaceRule>): Result<Unit> =
        if (applySuccess) Result.success(Unit) else Result.failure(IllegalStateException("js failed"))

    override suspend fun restoreOriginalText(): Result<Unit> {
        restoreCalls += 1
        return if (applySuccess) Result.success(Unit) else Result.failure(IllegalStateException("js failed"))
    }
}
