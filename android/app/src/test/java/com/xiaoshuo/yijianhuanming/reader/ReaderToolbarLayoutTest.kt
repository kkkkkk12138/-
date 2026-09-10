package com.xiaoshuo.yijianhuanming.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderToolbarLayoutTest {
    @Test
    fun epub_ratio_script_uses_document_scroll_extent_and_guards_short_chapters() {
        assertEquals(
            """
            (function(){const d=document.documentElement;const max=Math.max(1,d.scrollHeight-d.clientHeight);const value=d.scrollHeight<=d.clientHeight?0:window.scrollY/max;return value})()
            """.trimIndent(),
            EPUB_CHAPTER_RATIO_SCRIPT,
        )
    }

    @Test
    fun compact_width_wraps_five_actions_into_two_rows() {
        val rows = readerToolbarRows(
            widthDp = 360,
            fontScale = 1.3f,
            hasPrevious = true,
            showContents = true,
            hasNext = true,
        )

        assertEquals(
            listOf(
                listOf(ReaderToolbarAction.Close, ReaderToolbarAction.Previous, ReaderToolbarAction.Next),
                listOf(ReaderToolbarAction.Contents, ReaderToolbarAction.Rules),
            ),
            rows,
        )
    }

    @Test
    fun regular_width_keeps_short_toolbar_on_one_row() {
        val rows = readerToolbarRows(
            widthDp = 412,
            fontScale = 1f,
            hasPrevious = false,
            showContents = true,
            hasNext = false,
        )

        assertEquals(
            listOf(
                listOf(
                    ReaderToolbarAction.Close,
                    ReaderToolbarAction.Contents,
                    ReaderToolbarAction.Rules,
                ),
            ),
            rows,
        )
    }
}
