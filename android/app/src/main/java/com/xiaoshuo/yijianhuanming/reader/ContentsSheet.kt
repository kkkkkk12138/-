package com.xiaoshuo.yijianhuanming.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xiaoshuo.yijianhuanming.content.epub.EpubChapter
import com.xiaoshuo.yijianhuanming.content.epub.EpubTocEntry

@Composable
fun ContentsSheet(
    chapters: List<EpubChapter>,
    tableOfContents: List<EpubTocEntry>,
    currentChapterId: String,
    onChapterSelected: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val current = chapters.firstOrNull { it.id == currentChapterId }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("目录")
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onPrevious,
                enabled = current?.previousChapterId != null,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text("上一章")
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onNext,
                enabled = current?.nextChapterId != null,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text("下一章")
            }
        }
        LazyColumn {
            items(flatten(tableOfContents)) { row ->
                Text(
                    text = row.entry.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { selected = row.entry.chapterId == currentChapterId }
                        .clickable { onChapterSelected(row.entry.chapterId) }
                        .padding(start = (row.depth * 16).dp, top = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}

private data class TocRow(val entry: EpubTocEntry, val depth: Int)

private fun flatten(entries: List<EpubTocEntry>, depth: Int = 0): List<TocRow> =
    entries.flatMap { listOf(TocRow(it, depth)) + flatten(it.children, depth + 1) }
