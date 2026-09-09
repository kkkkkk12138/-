package com.xiaoshuo.yijianhuanming.reader

import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xiaoshuo.yijianhuanming.content.web.NavigationDecision
import com.xiaoshuo.yijianhuanming.content.web.WebSecurityCallbacks
import com.xiaoshuo.yijianhuanming.content.web.WebViewProfile
import com.xiaoshuo.yijianhuanming.content.txt.TxtAssetPathHandler
import com.xiaoshuo.yijianhuanming.content.epub.EpubChapter
import com.xiaoshuo.yijianhuanming.content.epub.EpubLocation
import com.xiaoshuo.yijianhuanming.content.epub.EpubReaderDocument
import com.xiaoshuo.yijianhuanming.navigation.AdaptiveReaderChrome
import com.xiaoshuo.yijianhuanming.navigation.ReaderBackTarget
import com.xiaoshuo.yijianhuanming.navigation.readerBackTarget
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    url: String,
    onClose: () -> Unit,
    profile: WebViewProfile = WebViewProfile.REMOTE_PUBLIC_WEB,
    txtPathHandler: TxtAssetPathHandler? = null,
    epubDocument: EpubReaderDocument? = null,
    onEpubLocationChanged: (EpubLocation) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onClearEpubCache: () -> Unit = {},
    viewModel: ReaderViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showRules by remember { mutableStateOf(false) }
    var showContents by remember { mutableStateOf(false) }
    var runtime by remember { mutableStateOf<RuleRuntime?>(null) }
    var webView by remember { mutableStateOf<ReaderWebView?>(null) }
    var currentChapterId by remember(epubDocument) {
        mutableStateOf(epubDocument?.initialLocation?.chapterId)
    }
    var currentUrl by remember(epubDocument, url) {
        mutableStateOf(epubDocument?.initialUrl ?: url)
    }
    var needsInitialRestore by remember(epubDocument) { mutableStateOf(epubDocument != null) }
    var fontScale by remember { mutableStateOf(1f) }
    val currentChapter = epubDocument?.chapter(currentChapterId)
    fun saveEpubLocation(afterSave: () -> Unit = {}) {
        val chapterId = currentChapterId
        val activeWebView = webView
        if (chapterId == null || activeWebView == null) {
            afterSave()
            return
        }
        activeWebView.evaluateJavascript(
            "(function(){const d=document.documentElement;const m=Math.max(0,d.scrollHeight-innerHeight);return m===0?0:scrollY/m})()",
        ) { raw ->
            val ratio = raw?.trim('"')?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
            onEpubLocationChanged(EpubLocation(chapterId, ratio.coerceIn(0.0, 1.0)))
            afterSave()
        }
    }
    fun openChapter(chapterId: String) {
        val chapter = epubDocument?.chapter(chapterId) ?: return
        saveEpubLocation {
            currentChapterId = chapter.id
            currentUrl = chapter.url
            needsInitialRestore = false
            showContents = false
        }
    }
    val callbacks = remember {
        object : WebSecurityCallbacks {
            override fun onNavigationBlocked(decision: NavigationDecision, url: String) {
                Toast.makeText(context, decision.message(), Toast.LENGTH_LONG).show()
            }

            override fun onLoginRiskDetected() {
                Toast.makeText(context, "检测到登录页面，已停止继续浏览", Toast.LENGTH_LONG).show()
            }
        }
    }
    DisposableEffect(lifecycleOwner, epubDocument, currentChapterId, webView) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) saveEpubLocation()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PredictiveBackHandler {
        it.collect {}
        when (
            readerBackTarget(
                panelOpen = showRules || showContents,
                canNavigateWebHistory = webView?.canGoBack() == true,
            )
        ) {
            ReaderBackTarget.DismissPanel -> {
                showRules = false
                showContents = false
            }
            ReaderBackTarget.WebHistory -> webView?.goBack()
            ReaderBackTarget.CloseReader -> saveEpubLocation(onClose)
        }
    }
    AdaptiveReaderChrome(
        supportingContent = {
            ReaderSettingsSheet(
                fontScale = fontScale,
                onFontScaleChange = { scale ->
                    fontScale = scale
                    webView?.evaluateJavascript(
                        "document.documentElement.style.fontSize='${(scale * 100).toInt()}%'",
                        null,
                    )
                },
                onClearWebData = {
                    CookieManager.getInstance().removeAllCookies(null)
                    WebStorage.getInstance().deleteAllData()
                    webView?.clearFormData()
                    webView?.clearCache(true)
                    webView?.clearHistory()
                },
                onClearRules = {
                    runtime?.let { current ->
                        scope.launch { viewModel.clearRules(current) }
                    }
                },
                onClearHistory = onClearHistory,
                onClearEpubCache = onClearEpubCache,
            )
        },
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                ReaderToolbar(
                    ruleCount = state.persisted.size,
                    onRules = { showRules = true },
                    onClose = {
                        saveEpubLocation(onClose)
                    },
                    showContents = !epubDocument?.tableOfContents.isNullOrEmpty(),
                    onContents = { showContents = true },
                    hasPrevious = currentChapter?.previousChapterId != null,
                    hasNext = currentChapter?.nextChapterId != null,
                    onPrevious = { currentChapter?.previousChapterId?.let(::openChapter) },
                    onNext = { currentChapter?.nextChapterId?.let(::openChapter) },
                )
                AndroidView(
                    factory = { androidContext ->
                        ReaderWebView(
                            context = androidContext,
                            profile = profile,
                            securityCallbacks = callbacks,
                            txtPathHandler = txtPathHandler,
                            epubPathHandler = epubDocument?.pathHandler,
                            onRuntimeReady = { installedRuntime ->
                                runtime = installedRuntime
                                if (needsInitialRestore) {
                                    val ratio = epubDocument?.initialLocation?.scrollRatio ?: 0.0
                                    webView?.post {
                                        webView?.evaluateJavascript(
                                            "scrollTo(0,Math.max(0,document.documentElement.scrollHeight-innerHeight)*$ratio)",
                                            null,
                                        )
                                    }
                                    needsInitialRestore = false
                                }
                                scope.launch {
                                    viewModel.reapplyPersisted(installedRuntime).onFailure {
                                        Toast.makeText(
                                            context,
                                            it.message ?: "规则重新应用失败",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            },
                        ).also {
                            webView = it
                            it.loadUrl(currentUrl)
                        }
                    },
                    update = { webView ->
                        if (webView.url != currentUrl) webView.loadUrl(currentUrl)
                    },
                    onRelease = {
                        webView = null
                        it.destroy()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    MaterialTheme {
        if (showRules) {
            ModalBottomSheet(onDismissRequest = { showRules = false }) {
                RuleEditorSheet(
                    state = state,
                    onEdit = viewModel::editRule,
                    onChange = viewModel::changeRule,
                    onAdd = viewModel::addRule,
                    onDelete = viewModel::deleteRule,
                    onApply = {
                        runtime?.let { current ->
                            scope.launch { viewModel.applyAll(current) }
                        }
                    },
                    onDismiss = { showRules = false },
                )
            }
        }
        if (showContents && epubDocument != null) {
            ModalBottomSheet(onDismissRequest = { showContents = false }) {
                ContentsSheet(
                    chapters = epubDocument.chapters.map {
                        EpubChapter(
                            id = it.id,
                            title = it.title,
                            href = it.url,
                            previousChapterId = it.previousChapterId,
                            nextChapterId = it.nextChapterId,
                        )
                    },
                    tableOfContents = epubDocument.tableOfContents,
                    currentChapterId = currentChapterId.orEmpty(),
                    onChapterSelected = ::openChapter,
                    onPrevious = { currentChapter?.previousChapterId?.let(::openChapter) },
                    onNext = { currentChapter?.nextChapterId?.let(::openChapter) },
                )
            }
        }
    }
}

@Composable
internal fun ReaderToolbar(
    ruleCount: Int,
    onRules: () -> Unit,
    onClose: () -> Unit,
    showContents: Boolean,
    onContents: () -> Unit,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val rows = readerToolbarRows(
            widthDp = maxWidth.value.toInt(),
            fontScale = density.fontScale,
            hasPrevious = hasPrevious,
            showContents = showContents,
            hasNext = hasNext,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            rows.forEach { actions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    actions.forEach { action ->
                        Button(
                            onClick = when (action) {
                                ReaderToolbarAction.Close -> onClose
                                ReaderToolbarAction.Previous -> onPrevious
                                ReaderToolbarAction.Contents -> onContents
                                ReaderToolbarAction.Next -> onNext
                                ReaderToolbarAction.Rules -> onRules
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) {
                            Text(
                                text = when (action) {
                                    ReaderToolbarAction.Close -> "关闭"
                                    ReaderToolbarAction.Previous -> "上一章"
                                    ReaderToolbarAction.Contents -> "目录"
                                    ReaderToolbarAction.Next -> "下一章"
                                    ReaderToolbarAction.Rules -> "规则 $ruleCount"
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal enum class ReaderToolbarAction {
    Close,
    Previous,
    Contents,
    Next,
    Rules,
}

internal fun readerToolbarRows(
    widthDp: Int,
    fontScale: Float,
    hasPrevious: Boolean,
    showContents: Boolean,
    hasNext: Boolean,
): List<List<ReaderToolbarAction>> {
    val actions = buildList {
        add(ReaderToolbarAction.Close)
        if (hasPrevious) add(ReaderToolbarAction.Previous)
        if (showContents) add(ReaderToolbarAction.Contents)
        if (hasNext) add(ReaderToolbarAction.Next)
        add(ReaderToolbarAction.Rules)
    }
    val needsWrap = actions.size > 3 && widthDp < 480 * fontScale
    if (!needsWrap) return listOf(actions)

    val navigation = actions.filter {
        it == ReaderToolbarAction.Close ||
            it == ReaderToolbarAction.Previous ||
            it == ReaderToolbarAction.Next
    }
    val tools = actions.filter {
        it == ReaderToolbarAction.Contents || it == ReaderToolbarAction.Rules
    }
    return listOf(navigation, tools).filter(List<ReaderToolbarAction>::isNotEmpty)
}

private fun NavigationDecision.message(): String = when (this) {
    NavigationDecision.Allow -> ""
    NavigationDecision.ConfirmCleartext -> "HTTP 页面需要明确确认"
    NavigationDecision.BlockLogin -> "登录页面已被拦截"
    is NavigationDecision.Block -> reason
}
