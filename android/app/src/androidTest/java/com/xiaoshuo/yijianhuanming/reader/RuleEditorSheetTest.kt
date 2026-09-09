package com.xiaoshuo.yijianhuanming.reader

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xiaoshuo.yijianhuanming.data.ReplaceRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RuleEditorSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun summary_edit_add_and_delete_last_rule_keep_apply_available() {
        var draft by mutableStateOf(listOf(ReplaceRule("1", "沈清辞", "林惊鹤", 0)))
        var editing by mutableStateOf<String?>(null)
        compose.setContent {
            RuleEditorSheet(
                state = RuleEditorState(draft = draft, editingRuleId = editing),
                onEdit = { editing = it },
                onChange = { id, source, target ->
                    draft = draft.map { if (it.id == id) it.copy(source = source, target = target) else it }
                },
                onAdd = { draft = draft + ReplaceRule("2", "", "", draft.size) },
                onDelete = { id -> draft = draft.filterNot { it.id == id } },
                onApply = {},
                onDismiss = {},
            )
        }

        compose.onNodeWithText("沈清辞 → 林惊鹤").assertIsDisplayed().performClick()
        compose.onNodeWithTag("rule-source-1").performTextReplacement("顾怀安")
        compose.runOnIdle { assertEquals("顾怀安", draft.single().source) }
        compose.onNodeWithText("新增规则").performClick()
        compose.runOnIdle { assertEquals(2, draft.size) }
        compose.onNodeWithTag("delete-rule-1").performClick()
        compose.onNodeWithTag("delete-rule-2").performClick()
        compose.onNodeWithText("全部生效").assertExists()
        compose.onAllNodesWithText("全部生效")[0]
            .assertHeightIsAtLeast(48.dp)
            .assertHasClickAction()
    }

    @Test
    fun failed_apply_keeps_draft_and_shows_error() {
        compose.setContent {
            RuleEditorSheet(
                state = RuleEditorState(
                    draft = listOf(ReplaceRule("draft", "旧名", "新名", 0)),
                    editingRuleId = "draft",
                    error = "保存失败，请重试",
                ),
                onEdit = {},
                onChange = { _, _, _ -> },
                onAdd = {},
                onDelete = {},
                onApply = {},
                onDismiss = {},
            )
        }

        compose.onNodeWithTag("rule-source-draft").assertExists()
        compose.onNodeWithText("保存失败，请重试").assertIsDisplayed()
    }
}
