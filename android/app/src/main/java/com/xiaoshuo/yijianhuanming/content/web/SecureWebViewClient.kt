package com.xiaoshuo.yijianhuanming.content.web

import android.net.http.SslError
import android.os.Message
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.xiaoshuo.yijianhuanming.reader.WebRuntimeController
import com.xiaoshuo.yijianhuanming.reader.RuleRuntime

interface WebSecurityCallbacks {
    fun onNavigationBlocked(decision: NavigationDecision, url: String)
    fun onLoginRiskDetected()
}

object NoOpWebSecurityCallbacks : WebSecurityCallbacks {
    override fun onNavigationBlocked(decision: NavigationDecision, url: String) = Unit
    override fun onLoginRiskDetected() = Unit
}

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class SecureWebViewClient(
    private val navigationPolicy: NavigationPolicy,
    private val runtimeController: WebRuntimeController,
    private val callbacks: WebSecurityCallbacks = NoOpWebSecurityCallbacks,
    private val confirmedCleartextUrl: String? = null,
    private val onRuntimeReady: (RuleRuntime) -> Unit = {},
    private val assetLoader: WebViewAssetLoader? = null,
) : WebViewClient() {
    private var pageGeneration: Long = 0
    private var activeUrl: String? = null

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        blockUnlessAllowed(view, request.url.toString())

    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
        blockUnlessAllowed(view, url)

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? = assetLoader?.shouldInterceptRequest(request.url)
        ?: super.shouldInterceptRequest(view, request)

    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
        pageGeneration = runtimeController.beginNavigation()
        activeUrl = url
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        if (url != activeUrl) return
        val finishedGeneration = pageGeneration
        runtimeController.installRuntime(finishedGeneration) { installed ->
            if (installed) {
                onRuntimeReady(runtimeController)
                runtimeController.inspectLoginRisk(finishedGeneration) { hasLoginRisk ->
                    if (hasLoginRisk) callbacks.onLoginRiskDetected()
                }
            }
        }
        super.onPageFinished(view, url)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        callbacks.onNavigationBlocked(
            NavigationDecision.Block("TLS 证书验证失败"),
            error.url,
        )
    }

    override fun onReceivedHttpAuthRequest(
        view: WebView,
        handler: HttpAuthHandler,
        host: String,
        realm: String,
    ) {
        handler.cancel()
        callbacks.onLoginRiskDetected()
    }

    override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
        request.cancel()
        callbacks.onLoginRiskDetected()
    }

    override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
        dontResend.sendToTarget()
    }

    private fun blockUnlessAllowed(view: WebView, url: String): Boolean {
        val decision = navigationPolicy.evaluate(
            rawUrl = url,
            confirmedCleartextUrl = confirmedCleartextUrl,
        )
        if (decision == NavigationDecision.Allow) return false

        view.stopLoading()
        callbacks.onNavigationBlocked(decision, url)
        return true
    }
}
