package com.xiaoshuo.yijianhuanming

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xiaoshuo.yijianhuanming.library.RecentReadingList
import com.xiaoshuo.yijianhuanming.navigation.AdaptiveReaderChrome
import com.xiaoshuo.yijianhuanming.navigation.ReaderPaneMode
import com.xiaoshuo.yijianhuanming.reader.ReaderSettingsSheet
import org.junit.Rule
import org.junit.Test

class AdaptiveReaderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun phone_opens_tools_in_bottom_sheet_with_talkback_labels() {
        compose.setContent {
            MaterialTheme {
                AdaptiveReaderChrome(
                    paneMode = ReaderPaneMode.BottomSheet,
                    supportingContent = { ReaderSettingsSheet() },
                ) {
                    RecentReadingList(items = emptyList(), onOpen = {})
                }
            }
        }

        compose.onNodeWithContentDescription("打开阅读设置")
            .assertContentDescriptionEquals("打开阅读设置")
            .performClick()
        compose.onNodeWithTag("reader-bottom-sheet").assertIsDisplayed()
        compose.onNodeWithText("阅读设置").assertIsDisplayed()
    }

    @Test
    fun wide_screen_keeps_supporting_pane_visible() {
        compose.setContent {
            MaterialTheme {
                AdaptiveReaderChrome(
                    paneMode = ReaderPaneMode.SupportingPane,
                    supportingContent = { ReaderSettingsSheet() },
                ) {
                    RecentReadingList(items = emptyList(), onOpen = {})
                }
            }
        }

        compose.onNodeWithTag("reader-supporting-pane").assertIsDisplayed()
        compose.onNodeWithText("阅读设置").assertIsDisplayed()
    }

    @Test
    fun destructive_settings_require_confirmation() {
        compose.setContent {
            MaterialTheme {
                ReaderSettingsSheet(onClearHistory = {})
            }
        }

        compose.onNodeWithText("清除最近阅读").performClick()
        compose.onNodeWithText("确认清除最近阅读？").assertIsDisplayed()
        compose.onNodeWithText("确认").assertIsDisplayed()
    }
}
