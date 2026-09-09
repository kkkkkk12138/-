package com.xiaoshuo.yijianhuanming

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiaoshuo.yijianhuanming.reader.HomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsPrimaryInputsAndPrivacyBoundary() {
        compose.setContent { HomeScreen(onOpenUrl = {}, onOpenDocument = {}) }
        compose.onNodeWithText("打开网页链接").assertIsDisplayed()
        compose.onNodeWithText("打开 TXT / EPUB").assertIsDisplayed()
        compose.onNodeWithText("不登录网站，不上传阅读内容").assertIsDisplayed()
        compose.onAllNodesWithText("设置").assertCountEquals(0)
    }
}
