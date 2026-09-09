package com.xiaoshuo.yijianhuanming.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiaoshuo.yijianhuanming.data.ReaderSessionEntity
import com.xiaoshuo.yijianhuanming.library.RecentReadingList

@Composable
fun HomeScreen(
    onOpenUrl: () -> Unit,
    onOpenDocument: () -> Unit,
    recent: List<ReaderSessionEntity> = emptyList(),
    onOpenRecent: (ReaderSessionEntity) -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Button(
                onClick = onOpenUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("打开网页链接")
            }
            Button(
                onClick = onOpenDocument,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("打开 TXT / EPUB")
            }
            RecentReadingList(
                items = recent,
                onOpen = onOpenRecent,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "不登录网站，不上传阅读内容",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
