package com.xiaoshuo.yijianhuanming

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.xiaoshuo.yijianhuanming.intake.AndroidInputResolver
import com.xiaoshuo.yijianhuanming.intake.ReaderInput
import com.xiaoshuo.yijianhuanming.content.txt.TxtContentSource
import com.xiaoshuo.yijianhuanming.content.txt.TxtOpenResult
import com.xiaoshuo.yijianhuanming.content.txt.TxtReaderDocument
import com.xiaoshuo.yijianhuanming.content.web.WebViewProfile
import com.xiaoshuo.yijianhuanming.reader.HomeScreen
import com.xiaoshuo.yijianhuanming.reader.ReaderScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val inputResolver by lazy { AndroidInputResolver(this) }
    private var readerInput by mutableStateOf<ReaderInput?>(null)
    private var txtDocument by mutableStateOf<TxtReaderDocument?>(null)
    private var txtSource: TxtContentSource? = null
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
        setContent {
            when (val input = readerInput) {
                is ReaderInput.WebUrl -> ReaderScreen(
                    url = input.uri.toString(),
                    onClose = { readerInput = null },
                )
                is ReaderInput.TxtDocument -> txtDocument?.let { document ->
                    ReaderScreen(
                        url = document.readerUrl,
                        profile = WebViewProfile.LOCAL_READER,
                        txtPathHandler = document.pathHandler,
                        onClose = ::closeTxt,
                    )
                } ?: HomeScreen(
                    onOpenUrl = {},
                    onOpenDocument = {},
                )
                else -> HomeScreen(
                    onOpenUrl = {},
                    onOpenDocument = {
                        openDocument.launch(arrayOf("text/plain", "application/epub+zip"))
                    },
                )
            }
        }
        if (savedInstanceState == null && intent.action != Intent.ACTION_MAIN) {
            resolveInput(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveInput(intent)
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
                } else {
                    Toast.makeText(this, "HTTP 页面需要明确确认", Toast.LENGTH_LONG).show()
                }
            }
            is ReaderInput.TxtDocument -> openTxt(input)
            is ReaderInput.EpubDocument ->
                Toast.makeText(this, "已识别 EPUB 文件", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openTxt(input: ReaderInput.TxtDocument) {
        readerInput = input
        txtDocument = null
        val source = TxtContentSource(this, input.uri)
        txtSource = source
        lifecycleScope.launch {
            source.open()
                .onSuccess { result ->
                    when (result) {
                        is TxtOpenResult.Ready -> txtDocument = result.document
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
        lifecycleScope.launch { source?.close() }
    }
}
