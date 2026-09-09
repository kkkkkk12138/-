package com.xiaoshuo.yijianhuanming.reader

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaoshuo.yijianhuanming.content.web.NavigationDecision
import com.xiaoshuo.yijianhuanming.content.web.WebSecurityCallbacks
import com.xiaoshuo.yijianhuanming.content.web.WebViewProfile
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    url: String,
    onClose: () -> Unit,
    viewModel: ReaderViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showRules by remember { mutableStateOf(false) }
    var runtime by remember { mutableStateOf<RuleRuntime?>(null) }
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

    BackHandler(onBack = onClose)
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                ReaderToolbar(
                    ruleCount = state.persisted.size,
                    onRules = { showRules = true },
                    onClose = onClose,
                )
                AndroidView(
                    factory = { androidContext ->
                        ReaderWebView(
                            context = androidContext,
                            profile = WebViewProfile.REMOTE_PUBLIC_WEB,
                            securityCallbacks = callbacks,
                            onRuntimeReady = { installedRuntime ->
                                runtime = installedRuntime
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
                        ).also { it.loadUrl(url) }
                    },
                    update = { webView ->
                        if (webView.url != url) webView.loadUrl(url)
                    },
                    onRelease = ReaderWebView::destroy,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
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
    }
}

@Composable
private fun ReaderToolbar(
    ruleCount: Int,
    onRules: () -> Unit,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterStart).heightIn(min = 48.dp),
        ) {
            Text("关闭")
        }
        Button(
            onClick = onRules,
            modifier = Modifier.align(Alignment.CenterEnd).heightIn(min = 48.dp),
        ) {
            Text("规则 $ruleCount")
        }
    }
}

private fun NavigationDecision.message(): String = when (this) {
    NavigationDecision.Allow -> ""
    NavigationDecision.ConfirmCleartext -> "HTTP 页面需要明确确认"
    NavigationDecision.BlockLogin -> "登录页面已被拦截"
    is NavigationDecision.Block -> reason
}
