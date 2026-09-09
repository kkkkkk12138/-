package com.xiaoshuo.yijianhuanming.reader

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import com.xiaoshuo.yijianhuanming.content.web.LoginRiskPolicy
import java.util.concurrent.atomic.AtomicLong

class NavigationGenerations {
    private val value = AtomicLong(0)

    fun beginNavigation(): Long = value.incrementAndGet()

    fun invalidate() {
        value.incrementAndGet()
    }

    fun isCurrent(generation: Long): Boolean = value.get() == generation
}

class WebRuntimeController(
    private val webView: WebView,
    private val generations: NavigationGenerations = NavigationGenerations(),
) {
    fun beginNavigation(): Long = generations.beginNavigation()

    fun inspectLoginRisk(generation: Long, onResult: (Boolean) -> Unit) {
        evaluateForGeneration(
            generation = generation,
            script = LoginRiskPolicy.PASSWORD_FORM_GUARD_SCRIPT,
        ) { result ->
            onResult(LoginRiskPolicy.resultHasLoginRisk(result))
        }
    }

    fun installRuntime(generation: Long, onResult: (Boolean) -> Unit = {}) {
        val runtime = runCatching {
            webView.context.assets.open(RUNTIME_ASSET).bufferedReader().use { it.readText() }
        }.getOrElse {
            if (generations.isCurrent(generation)) onResult(false)
            return
        }
        evaluateForGeneration(generation, runtime) {
            onResult(true)
        }
    }

    fun evaluateForGeneration(
        generation: Long,
        script: String,
        onResult: (String?) -> Unit,
    ) {
        if (!generations.isCurrent(generation)) return
        webView.evaluateJavascript(script) { result ->
            if (generations.isCurrent(generation)) {
                onResult(result)
            }
        }
    }

    fun clearRemoteSession() {
        generations.invalidate()
        webView.stopLoading()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        webView.clearFormData()
        webView.clearCache(true)
        webView.clearHistory()
    }

    companion object {
        private const val RUNTIME_ASSET = "name-replacer.js"
    }
}
