package com.xiaoshuo.yijianhuanming.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private data class ConfirmAction(
    val label: String,
    val action: () -> Unit,
)

@Composable
fun ReaderSettingsSheet(
    fontScale: Float = 1f,
    onFontScaleChange: (Float) -> Unit = {},
    onClearWebData: () -> Unit = {},
    onClearHistory: () -> Unit = {},
    onClearRules: () -> Unit = {},
    onClearEpubCache: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var confirmation by remember { mutableStateOf<ConfirmAction?>(null) }
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("阅读设置", style = MaterialTheme.typography.headlineSmall)
        Text("字体大小 ${(fontScale * 100).toInt()}%")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onFontScaleChange((fontScale - 0.1f).coerceAtLeast(0.8f)) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "减小阅读字体" },
            ) { Text("A−") }
            OutlinedButton(
                onClick = { onFontScaleChange((fontScale + 0.1f).coerceAtMost(2f)) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "增大阅读字体" },
            ) { Text("A+") }
        }
        DestructiveSetting("清除网页数据") {
            confirmation = ConfirmAction("清除网页数据", onClearWebData)
        }
        DestructiveSetting("清除最近阅读") {
            confirmation = ConfirmAction("清除最近阅读", onClearHistory)
        }
        DestructiveSetting("清除全部规则") {
            confirmation = ConfirmAction("清除全部规则", onClearRules)
        }
        DestructiveSetting("清除 EPUB 缓存") {
            confirmation = ConfirmAction("清除 EPUB 缓存", onClearEpubCache)
        }
    }

    confirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text("确认${pending.label}？") },
            text = { Text("此操作不可撤销。原始 TXT / EPUB 文件不会被删除。") },
            confirmButton = {
                Button(onClick = {
                    pending.action()
                    confirmation = null
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DestructiveSetting(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { contentDescription = label },
    ) {
        Text(label)
    }
}
