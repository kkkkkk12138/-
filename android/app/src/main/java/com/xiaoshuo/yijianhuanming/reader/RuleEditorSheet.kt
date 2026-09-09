package com.xiaoshuo.yijianhuanming.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xiaoshuo.yijianhuanming.data.ReplaceRule

@Composable
fun RuleEditorSheet(
    state: RuleEditorState,
    onEdit: (String) -> Unit,
    onChange: (id: String, source: String, target: String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("换名规则", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("关闭")
                }
            }
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(state.draft, key = ReplaceRule::id) { rule ->
                    if (state.editingRuleId == rule.id) {
                        RuleEditRow(rule = rule, onChange = onChange, onDelete = onDelete)
                    } else {
                        RuleSummaryRow(rule = rule, onEdit = onEdit, onDelete = onDelete)
                    }
                    HorizontalDivider()
                }
            }
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("新增规则")
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = onApply,
                enabled = !state.isApplying,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                if (state.isApplying) {
                    CircularProgressIndicator()
                } else {
                    Text("全部生效")
                }
            }
        }
    }
}

@Composable
private fun RuleSummaryRow(
    rule: ReplaceRule,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onEdit(rule.id) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${rule.source} → ${rule.target}",
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { onDelete(rule.id) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("delete-rule-${rule.id}"),
        ) {
            Text("删除")
        }
    }
}

@Composable
private fun RuleEditRow(
    rule: ReplaceRule,
    onChange: (id: String, source: String, target: String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = rule.source,
            onValueChange = { onChange(rule.id, it, rule.target) },
            label = { Text("原名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("rule-source-${rule.id}"),
        )
        OutlinedTextField(
            value = rule.target,
            onValueChange = { onChange(rule.id, rule.source, it) },
            label = { Text("新名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("rule-target-${rule.id}"),
        )
        TextButton(
            onClick = { onDelete(rule.id) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("delete-rule-${rule.id}"),
        ) {
            Text("删除")
        }
    }
}
