package com.xiaoshuo.yijianhuanming.navigation

import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class ReaderDestinationState(
    val inputType: String,
    val uri: String,
    val chapterId: String? = null,
    val scrollRatio: Double = 0.0,
) {
    fun toSavedState(): Map<String, String> = buildMap {
        put(INPUT_TYPE, inputType)
        put(URI, uri)
        chapterId?.let { put(CHAPTER_ID, it) }
        put(SCROLL_RATIO, scrollRatio.toString())
    }

    companion object {
        private const val INPUT_TYPE = "reader.inputType"
        private const val URI = "reader.uri"
        private const val CHAPTER_ID = "reader.chapterId"
        private const val SCROLL_RATIO = "reader.scrollRatio"

        fun fromSavedState(state: Map<String, String>): ReaderDestinationState? {
            val inputType = state[INPUT_TYPE]?.takeIf { it in setOf("WEB", "TXT", "EPUB") }
                ?: return null
            val uri = state[URI]?.takeIf(String::isNotBlank) ?: return null
            return ReaderDestinationState(
                inputType = inputType,
                uri = uri,
                chapterId = state[CHAPTER_ID],
                scrollRatio = state[SCROLL_RATIO]?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.0,
            )
        }
    }
}

enum class ReaderPaneMode {
    BottomSheet,
    SupportingPane;

    companion object {
        fun forWidth(widthDp: Int): ReaderPaneMode =
            if (widthDp >= 840) SupportingPane else BottomSheet
    }
}

object ReaderExperiencePolicy {
    const val edgeToEdge = true
    const val predictiveBack = true
    fun usesDynamicColor(apiLevel: Int): Boolean = apiLevel >= Build.VERSION_CODES.S

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    fun isDynamicColorSupported(): Boolean = usesDynamicColor(Build.VERSION.SDK_INT)
}

enum class ReaderBackTarget {
    DismissPanel,
    WebHistory,
    CloseReader,
}

fun readerBackTarget(panelOpen: Boolean, canNavigateWebHistory: Boolean): ReaderBackTarget =
    when {
        panelOpen -> ReaderBackTarget.DismissPanel
        canNavigateWebHistory -> ReaderBackTarget.WebHistory
        else -> ReaderBackTarget.CloseReader
    }

@Composable
fun NameReplacerTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val inspect = LocalInspectionMode.current
    val colorScheme = if (
        dynamicColor &&
        ReaderExperiencePolicy.isDynamicColorSupported() &&
        !inspect
    ) {
        dynamicColorScheme(context, darkTheme)
    } else {
        if (darkTheme) androidx.compose.material3.darkColorScheme()
        else androidx.compose.material3.lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@RequiresApi(Build.VERSION_CODES.S)
private fun dynamicColorScheme(context: Context, darkTheme: Boolean) =
    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

@Composable
fun AppNavHost(
    destination: ReaderDestinationState?,
    library: @Composable () -> Unit,
    reader: @Composable (ReaderDestinationState) -> Unit,
) {
    if (destination == null) library() else reader(destination)
}

@Composable
fun AdaptiveReaderChrome(
    supportingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    paneMode: ReaderPaneMode? = null,
    mainContent: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val resolvedMode = paneMode ?: ReaderPaneMode.forWidth(maxWidth.value.toInt())
        AdaptiveReaderChromeContent(resolvedMode, supportingContent, mainContent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdaptiveReaderChromeContent(
    paneMode: ReaderPaneMode,
    supportingContent: @Composable () -> Unit,
    mainContent: @Composable () -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    if (paneMode == ReaderPaneMode.SupportingPane) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { mainContent() }
            Box(
                Modifier
                    .fillMaxHeight()
                    .widthIn(min = 320.dp, max = 420.dp)
                    .testTag("reader-supporting-pane"),
            ) { supportingContent() }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(
                    onClick = { showSheet = true },
                    modifier = Modifier.semantics { contentDescription = "打开阅读设置" },
                ) { Text("设置") }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                mainContent()
            }
        }
        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                modifier = Modifier.testTag("reader-bottom-sheet"),
            ) {
                supportingContent()
            }
        }
    }
}
