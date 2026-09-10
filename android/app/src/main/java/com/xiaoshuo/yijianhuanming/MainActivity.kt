package com.xiaoshuo.yijianhuanming

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaoshuo.yijianhuanming.intake.AndroidInputResolver
import com.xiaoshuo.yijianhuanming.intake.DocumentKind
import com.xiaoshuo.yijianhuanming.intake.DocumentMetadataResolver
import com.xiaoshuo.yijianhuanming.intake.ReaderInput
import com.xiaoshuo.yijianhuanming.intake.UrlEntryResolver
import com.xiaoshuo.yijianhuanming.content.txt.TxtContentSource
import com.xiaoshuo.yijianhuanming.content.txt.TxtOpenResult
import com.xiaoshuo.yijianhuanming.content.txt.TxtReaderDocument
import com.xiaoshuo.yijianhuanming.content.txt.TxtResumePosition
import com.xiaoshuo.yijianhuanming.content.epub.EpubContentSource
import com.xiaoshuo.yijianhuanming.content.epub.EpubLocation
import com.xiaoshuo.yijianhuanming.content.epub.EpubReaderDocument
import com.xiaoshuo.yijianhuanming.content.web.WebViewProfile
import com.xiaoshuo.yijianhuanming.data.ReaderSessionDao
import com.xiaoshuo.yijianhuanming.data.ReaderSessionEntity
import com.xiaoshuo.yijianhuanming.reader.HomeScreen
import com.xiaoshuo.yijianhuanming.reader.ReaderScreen
import com.xiaoshuo.yijianhuanming.reader.ReaderSettingsSheet
import com.xiaoshuo.yijianhuanming.library.LibraryViewModel
import com.xiaoshuo.yijianhuanming.library.ReadingProgress
import com.xiaoshuo.yijianhuanming.library.ReadingProgressCoordinator
import com.xiaoshuo.yijianhuanming.library.initializeEpubProgress
import com.xiaoshuo.yijianhuanming.library.txtRatio
import com.xiaoshuo.yijianhuanming.navigation.AdaptiveReaderChrome
import com.xiaoshuo.yijianhuanming.navigation.AppNavHost
import com.xiaoshuo.yijianhuanming.navigation.NameReplacerTheme
import com.xiaoshuo.yijianhuanming.navigation.ReaderDestinationState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var readerSessionDao: ReaderSessionDao

    private val inputResolver by lazy { AndroidInputResolver(this) }
    private val documentMetadataResolver by lazy { DocumentMetadataResolver(contentResolver) }
    private val urlEntryResolver by lazy { UrlEntryResolver(inputResolver) }
    private var readerInput by mutableStateOf<ReaderInput?>(null)
    private var requestUrlDialog by mutableStateOf(false)
    private var txtDocument by mutableStateOf<TxtReaderDocument?>(null)
    private var epubDocument by mutableStateOf<EpubReaderDocument?>(null)
    private var txtSource: TxtContentSource? = null
    private var txtProgressCoordinator: ReadingProgressCoordinator? = null
    private var epubSource: EpubContentSource? = null
    private var epubProgressCoordinator: ReadingProgressCoordinator? = null
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            resolveInput(
                Intent(Intent.ACTION_OPEN_DOCUMENT, it).addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                ),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        savedInstanceState?.let(::restoreReaderInput)
        setContent {
            val libraryViewModel: LibraryViewModel = viewModel()
            val libraryState by libraryViewModel.state.collectAsState()
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            NameReplacerTheme(darkTheme = isSystemInDarkTheme()) {
                AppNavHost(
                    destination = readerInput?.toDestinationState(),
                    library = {
                        AdaptiveReaderChrome(
                            supportingContent = {
                                ReaderSettingsSheet(
                                    onClearHistory = {
                                        scope.launch { libraryViewModel.clearHistory() }
                                    },
                                    onClearRules = {
                                        scope.launch { libraryViewModel.clearRules(null) }
                                    },
                                    onClearEpubCache = {
                                        scope.launch { libraryViewModel.clearEpubCache() }
                                    },
                                )
                            },
                        ) {
                            HomeScreen(
                                onOpenUrl = ::openUrlFromDialog,
                                onOpenDocument = {
                                    openDocument.launch(
                                        arrayOf("text/plain", "application/epub+zip"),
                                    )
                                },
                                recent = libraryState.recent,
                                onOpenRecent = { openRecent(it) },
                                resolveUrl = urlEntryResolver::resolve,
                                openUrlDialogInitially = requestUrlDialog,
                                onUrlDialogShown = { requestUrlDialog = false },
                            )
                        }
                    },
                    reader = {
                        ReaderContent(
                            input = readerInput,
                            onClearHistory = {
                                scope.launch { libraryViewModel.clearHistory() }
                            },
                            onClearEpubCache = {
                                scope.launch { libraryViewModel.clearEpubCache() }
                            },
                        )
                    },
                )
            }
        }
        if (savedInstanceState == null && intent.action != Intent.ACTION_MAIN) {
            resolveInput(intent)
        }
    }

    @androidx.compose.runtime.Composable
    private fun ReaderContent(
        input: ReaderInput?,
        onClearHistory: () -> Unit,
        onClearEpubCache: () -> Unit,
    ) {
        when (input) {
                is ReaderInput.WebUrl -> ReaderScreen(
                    url = input.uri.toString(),
                    onClose = { readerInput = null },
                    confirmedCleartextUrl = input.uri.toString()
                        .takeIf { input.uri.scheme.equals("http", ignoreCase = true) },
                    onOpenOtherUrl = {
                        readerInput = null
                        requestUrlDialog = true
                    },
                    onClearHistory = onClearHistory,
                    onClearEpubCache = onClearEpubCache,
                )
                is ReaderInput.TxtDocument -> txtDocument?.let { document ->
                    ReaderScreen(
                        url = document.readerUrl,
                        profile = WebViewProfile.LOCAL_READER,
                        txtPathHandler = document.pathHandler,
                        txtDocument = document,
                        txtSourceId = input.uri.toString(),
                        txtProgressCoordinator = txtProgressCoordinator,
                        onClose = ::closeTxt,
                        onClearHistory = onClearHistory,
                        onClearEpubCache = onClearEpubCache,
                    )
                } ?: HomeScreen(
                    onOpenUrl = ::openUrlFromDialog,
                    onOpenDocument = {},
                    resolveUrl = urlEntryResolver::resolve,
                )
                is ReaderInput.EpubDocument -> epubDocument?.let { document ->
                    ReaderScreen(
                        url = document.initialUrl,
                        profile = WebViewProfile.LOCAL_READER,
                        epubDocument = document,
                        epubProgressCoordinator = epubProgressCoordinator,
                        onClose = ::closeEpub,
                        onClearHistory = onClearHistory,
                        onClearEpubCache = onClearEpubCache,
                    )
                } ?: HomeScreen(
                    onOpenUrl = ::openUrlFromDialog,
                    onOpenDocument = {},
                    resolveUrl = urlEntryResolver::resolve,
                )
                else -> Unit
            }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveInput(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        readerInput?.let { input ->
            outState.putString(STATE_INPUT_TYPE, input.toDestinationState().inputType)
            outState.putString(STATE_INPUT_URI, input.toDestinationState().uri)
            outState.putBoolean(
                STATE_PERSISTED_PERMISSION,
                when (input) {
                    is ReaderInput.TxtDocument -> input.persistedReadPermission
                    is ReaderInput.EpubDocument -> input.persistedReadPermission
                    is ReaderInput.WebUrl -> false
                },
            )
        }
    }

    private fun resolveInput(intent: Intent) {
        lifecycleScope.launch {
            inputResolver.resolve(intent)
                .onSuccess(::onReaderInput)
                .onFailure {
                    Toast.makeText(
                        this@MainActivity,
                        it.message ?: "无法识别输入",
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    private fun onReaderInput(input: ReaderInput) {
        when (input) {
            is ReaderInput.WebUrl -> {
                if (input.uri.scheme.equals("https", ignoreCase = true)) {
                    readerInput = input
                    upsertSessionMetadata(input.uri.toString(), "WEB", input.uri.host.orEmpty())
                } else {
                    Toast.makeText(this, "HTTP 页面需要明确确认", Toast.LENGTH_LONG).show()
                }
            }
            is ReaderInput.TxtDocument -> openTxt(input)
            is ReaderInput.EpubDocument -> openEpub(input)
        }
    }

    private fun openUrlFromDialog(input: ReaderInput.WebUrl) {
        readerInput = input
        upsertSessionMetadata(input.uri.toString(), "WEB", input.uri.host.orEmpty())
    }

    private fun restoreReaderInput(state: Bundle) {
        val type = state.getString(STATE_INPUT_TYPE) ?: return
        val uri = state.getString(STATE_INPUT_URI)?.let(android.net.Uri::parse) ?: return
        val persisted = state.getBoolean(STATE_PERSISTED_PERMISSION)
        onReaderInput(
            when (type) {
                "WEB" -> ReaderInput.WebUrl(uri)
                "TXT" -> ReaderInput.TxtDocument(uri, persisted)
                "EPUB" -> ReaderInput.EpubDocument(uri, persisted)
                else -> return
            },
        )
    }

    private fun openRecent(session: ReaderSessionEntity) {
        val uri = android.net.Uri.parse(session.uri)
        onReaderInput(
            when (session.type) {
                "EPUB" -> ReaderInput.EpubDocument(uri, true)
                "TXT" -> ReaderInput.TxtDocument(uri, true)
                else -> ReaderInput.WebUrl(uri)
            },
        )
    }

    private fun openTxt(input: ReaderInput.TxtDocument) {
        readerInput = input
        txtDocument = null
        lifecycleScope.launch {
            val saved = readerSessionDao.findBySourceId(input.uri.toString())
            txtProgressCoordinator = ReadingProgressCoordinator(lifecycleScope) { progress ->
                readerSessionDao.saveProgress(
                    sourceId = input.uri.toString(),
                    chapterId = null,
                    scrollRatio = progress.scrollRatio,
                    textOffset = progress.textOffset,
                    textTotalAtSave = progress.textTotalAtSave,
                    lastOpenedAt = progress.lastOpenedAt,
                )
            }
            val source = TxtContentSource(
                context = this@MainActivity,
                uri = input.uri,
                resumePosition = saved?.let {
                    TxtResumePosition(
                        textOffset = it.textOffset,
                        textTotalAtSave = it.textTotalAtSave,
                        scrollRatio = it.scrollRatio,
                    )
                },
            )
            txtSource = source
            source.open()
                .onSuccess { result ->
                    when (result) {
                        is TxtOpenResult.Ready -> {
                            txtProgressCoordinator?.rememberConfirmed(
                                ReadingProgress(
                                    textOffset = result.document.initialOffset,
                                    textTotalAtSave = result.document.totalUtf16Units,
                                    scrollRatio = txtRatio(
                                        result.document.initialOffset,
                                        result.document.totalUtf16Units,
                                    ),
                                    lastOpenedAt = System.currentTimeMillis(),
                                ),
                            )
                            txtDocument = result.document
                            upsertSessionMetadata(
                                sourceId = input.uri.toString(),
                                type = "TXT",
                                title = documentMetadataResolver.resolve(
                                    DocumentKind.TXT,
                                    input.uri.toString(),
                                ),
                            )
                        }
                        is TxtOpenResult.NeedsEncodingSelection -> {
                            readerInput = null
                            val names = result.candidates.joinToString { it.charsetName }
                            Toast.makeText(
                                this@MainActivity,
                                "无法确定 TXT 编码，请选择编码：$names",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
                .onFailure {
                    readerInput = null
                    Toast.makeText(
                        this@MainActivity,
                        it.message ?: "无法打开 TXT",
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    private fun closeTxt() {
        val source = txtSource
        readerInput = null
        txtDocument = null
        txtSource = null
        txtProgressCoordinator = null
        lifecycleScope.launch { source?.close() }
    }

    private fun openEpub(input: ReaderInput.EpubDocument) {
        readerInput = input
        epubDocument = null
        lifecycleScope.launch {
            val saved = readerSessionDao.findBySourceId(input.uri.toString())
            val savedLocation = saved?.chapterId?.let { EpubLocation(it, saved.scrollRatio) }
            val progressCoordinator = ReadingProgressCoordinator(lifecycleScope) { progress ->
                readerSessionDao.saveProgress(
                    sourceId = input.uri.toString(),
                    chapterId = progress.chapterId,
                    scrollRatio = progress.scrollRatio,
                    textOffset = null,
                    textTotalAtSave = null,
                    lastOpenedAt = progress.lastOpenedAt,
                )
            }
            epubProgressCoordinator = progressCoordinator
            val source = EpubContentSource(this@MainActivity, input.uri, savedLocation)
            epubSource = source
            val result = source.open()
            result.getOrNull()?.let { document ->
                readerSessionDao.upsertMetadataPreservingProgress(
                    sourceId = input.uri.toString(),
                    type = "EPUB",
                    title = documentMetadataResolver.resolve(
                        DocumentKind.EPUB,
                        input.uri.toString(),
                    ),
                    uri = input.uri.toString(),
                    lastOpenedAt = System.currentTimeMillis(),
                )
                initializeEpubProgress(
                    location = document.initialLocation,
                    repaired = document.initialLocationRepaired,
                    coordinator = progressCoordinator,
                )
                epubDocument = document
            }
            result.exceptionOrNull()?.let {
                readerInput = null
                epubSource = null
                epubProgressCoordinator = null
                Toast.makeText(
                    this@MainActivity,
                    it.message ?: "无法打开 EPUB",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun upsertSessionMetadata(sourceId: String, type: String, title: String) {
        lifecycleScope.launch {
            readerSessionDao.upsertMetadataPreservingProgress(
                sourceId = sourceId,
                type = type,
                title = title.ifBlank { sourceId },
                uri = sourceId,
                lastOpenedAt = System.currentTimeMillis(),
            )
        }
    }

    private fun closeEpub() {
        val source = epubSource
        readerInput = null
        epubDocument = null
        epubSource = null
        epubProgressCoordinator = null
        lifecycleScope.launch { source?.close() }
    }

    private fun ReaderInput.toDestinationState() = ReaderDestinationState(
        inputType = when (this) {
            is ReaderInput.WebUrl -> "WEB"
            is ReaderInput.TxtDocument -> "TXT"
            is ReaderInput.EpubDocument -> "EPUB"
        },
        uri = when (this) {
            is ReaderInput.WebUrl -> uri.toString()
            is ReaderInput.TxtDocument -> uri.toString()
            is ReaderInput.EpubDocument -> uri.toString()
        },
    )

    companion object {
        private const val STATE_INPUT_TYPE = "reader.input.type"
        private const val STATE_INPUT_URI = "reader.input.uri"
        private const val STATE_PERSISTED_PERMISSION = "reader.input.persisted"
    }
}
