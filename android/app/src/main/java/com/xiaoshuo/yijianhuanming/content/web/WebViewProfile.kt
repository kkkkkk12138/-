package com.xiaoshuo.yijianhuanming.content.web

import android.webkit.WebSettings
import android.webkit.WebView

enum class WebViewProfile {
    REMOTE_PUBLIC_WEB,
    LOCAL_READER;

    @Suppress("DEPRECATION")
    fun applyTo(webView: WebView) {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = this@WebViewProfile == REMOTE_PUBLIC_WEB
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            blockNetworkLoads = this@WebViewProfile == LOCAL_READER
            safeBrowsingEnabled = true
        }
        webView.isSaveEnabled = false
        WebView.setWebContentsDebuggingEnabled(
            webView.context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )
    }
}
