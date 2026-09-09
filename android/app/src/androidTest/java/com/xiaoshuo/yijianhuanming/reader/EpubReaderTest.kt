package com.xiaoshuo.yijianhuanming.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.xiaoshuo.yijianhuanming.content.epub.EpubAssetPathHandler
import com.xiaoshuo.yijianhuanming.content.epub.EpubBook
import com.xiaoshuo.yijianhuanming.content.epub.EpubChapter
import com.xiaoshuo.yijianhuanming.content.epub.EpubTocEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EpubReaderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun contents_sheet_selects_nested_entry_and_exposes_chapter_boundaries() {
        var selected: String? = null
        compose.setContent {
            MaterialTheme {
                ContentsSheet(
                    chapters = listOf(
                        EpubChapter("one", "第一章", "OEBPS/c1.xhtml", null, "two"),
                        EpubChapter("two", "第二章", "OEBPS/c2.xhtml", "one", null),
                    ),
                    tableOfContents = listOf(
                        EpubTocEntry(
                            title = "卷一",
                            chapterId = "one",
                            children = listOf(EpubTocEntry("第二章", "two")),
                        ),
                    ),
                    currentChapterId = "one",
                    onChapterSelected = { selected = it },
                    onPrevious = {},
                    onNext = {},
                )
            }
        }

        compose.onNodeWithText("上一章").assertIsNotEnabled()
        compose.onNodeWithText("下一章").assertIsEnabled()
        compose.onNodeWithText("第二章").assertIsDisplayed().performClick()
        assertEquals("two", selected)
    }

    @Test
    fun asset_handler_serves_only_session_allowlist_with_csp_and_builtin_theme() {
        val root = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .resolve("epub-handler-test-${System.nanoTime()}")
            .apply { mkdirs() }
        root.resolve("OEBPS/chapter.xhtml").also {
            it.parentFile?.mkdirs()
            it.writeText("<html><head></head><body><p>沈清辞</p><script>alert(1)</script></body></html>")
        }
        root.resolve("OEBPS/private.txt").writeText("secret")
        val book = EpubBook(
            title = "样书",
            packagePath = "OEBPS/content.opf",
            chapters = listOf(EpubChapter("one", "第一章", "OEBPS/chapter.xhtml", null, null)),
            tableOfContents = emptyList(),
            allowedResources = emptySet(),
        )
        val handler = EpubAssetPathHandler("session-1", root, book)

        val response = handler.handle("session-1/OEBPS/chapter.xhtml")
        val html = response?.data?.bufferedReader()?.readText().orEmpty()

        assertNotNull(response)
        assertEquals(EpubAssetPathHandler.STRICT_CSP, response?.responseHeaders?.get("Content-Security-Policy"))
        assertTrue(html.contains(EpubAssetPathHandler.BUILT_IN_THEME))
        assertTrue(html.contains("沈清辞"))
        assertTrue(!html.contains("<script"))
        assertNull(handler.handle("other-session/OEBPS/chapter.xhtml"))
        assertNull(handler.handle("session-1/OEBPS/private.txt"))
        root.deleteRecursively()
    }
}
