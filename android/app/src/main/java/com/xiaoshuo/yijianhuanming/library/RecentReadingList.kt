package com.xiaoshuo.yijianhuanming.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xiaoshuo.yijianhuanming.data.ReaderSessionEntity

@Composable
fun RecentReadingList(
    items: List<ReaderSessionEntity>,
    onOpen: (ReaderSessionEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = "最近阅读",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        if (items.isEmpty()) {
            Text("还没有阅读记录", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(items, key = { it.sourceId }) { session ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .semantics {
                                contentDescription = "继续阅读${session.title}"
                            }
                            .clickable { onOpen(session) }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                    ) {
                        Column {
                            Text(session.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = when (session.type) {
                                    "EPUB" -> "EPUB · ${(session.scrollRatio * 100).toInt()}%"
                                    "TXT" -> "TXT · ${(session.scrollRatio * 100).toInt()}%"
                                    else -> "网页"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
