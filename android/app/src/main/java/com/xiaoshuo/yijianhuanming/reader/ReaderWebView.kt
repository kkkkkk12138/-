package com.xiaoshuo.yijianhuanming.reader

import android.content.Context
import android.os.Message
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import com.xiaoshuo.yijianhuanming.content.web.NavigationDecision
import com.xiaoshuo.yijianhuanming.content.web.NavigationPolicy
import com.xiaoshuo.yijianhuanming.content.web.NavigationRequestKind
import com.xiaoshuo.yijianhuanming.content.web.NoOpWebSecurityCallbacks
import com.xiaoshuo.yijianhuanming.content.web.SecureWebViewClient
import com.xiaoshuo.yijianhuanming.content.web.WebSecurityCallbacks
import com.xiaoshuo.yijianhuanming.content.web.WebViewProfile
import com.xiaoshuo.yijianhuanming.content.txt.TxtAssetPathHandler

class ReaderWebView(
    context: Context,
    val profile: WebViewProfile,
    private val securityCallbacks: WebSecurityCallbacks = NoOpWebSecurityCallbacks,
    confirmedCleartextUrl: String? = null,
    txtPathHandler: TxtAssetPathHandler? = null,
    onRuntimeReady: (RuleRuntime) -> Unit = {},
) : WebView(context) {
    private val navigationPolicy = NavigationPolicy(profile)
    private val runtimeController = WebRuntimeController(this)
    private val assetLoader = if (profile == WebViewProfile.LOCAL_READER) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .apply {
                if (txtPathHandler != null) addPathHandler("/txt/", txtPathHandler)
            }
            .build()
    } else {
        null
    }

    init {
        profile.applyTo(this)
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        webViewClient = SecureWebViewClient(
            navigationPolicy = navigationPolicy,
            runtimeController = runtimeController,
            callbacks = securityCallbacks,
            confirmedCleartextUrl = confirmedCleartextUrl,
            onRuntimeReady = onRuntimeReady,
            assetLoader = assetLoader,
        )
        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                securityCallbacks.onNavigationBlocked(
                    NavigationDecision.Block("不支持网页新窗口"),
                    view.url.orEmpty(),
                )
                return false
            }
        }
        setDownloadListener { url, _, _, _, _ ->
            val decision = navigationPolicy.evaluate(
                rawUrl = url.orEmpty(),
                requestKind = NavigationRequestKind.DOWNLOAD,
            )
            securityCallbacks.onNavigationBlocked(decision, url.orEmpty())
        }
    }

    fun clearSession() {
        runtimeController.clearRemoteSession()
    }

    override fun destroy() {
        if (profile == WebViewProfile.REMOTE_PUBLIC_WEB) {
            clearSession()
        }
        removeAllViews()
        super.destroy()
    }
}
