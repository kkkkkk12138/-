package com.xiaoshuo.yijianhuanming.reader

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import com.xiaoshuo.yijianhuanming.content.web.LoginRiskPolicy
import com.xiaoshuo.yijianhuanming.data.ReplaceRule
import com.xiaoshuo.yijianhuanming.data.forRuntime
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

interface RuleRuntime {
    suspend fun applyRules(rules: List<ReplaceRule>): Result<Unit>
    suspend fun restoreOriginalText(): Result<Unit>
}

class WebRuntimeScriptEncoder {
    fun applyRules(rules: List<ReplaceRule>): String {
        if (rules.isEmpty()) {
            return "window.__NAME_REPLACER__.restoreOriginalText()"
        }
        val payload = rules.forRuntime().joinToString(prefix = "[", postfix = "]") { rule ->
            """{"id":${quote(rule.id)},"source":${quote(rule.source)},"target":${quote(rule.target)},"order":${rule.order}}"""
        }
        return "window.__NAME_REPLACER__.applyRules(JSON.parse(${quote(payload)}))"
    }

    fun extractPayload(script: String): String {
        val start = script.indexOf("JSON.parse(")
        require(start >= 0) { "Script has no JSON payload" }
        val encoded = script.substring(start + "JSON.parse(".length, script.lastIndexOf("))"))
        return decodeQuotedString(encoded)
    }

    private fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '<' -> append("\\u003C")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> if (character.code < 0x20) {
                    append("\\u%04x".format(character.code))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private fun decodeQuotedString(value: String): String {
        require(value.length >= 2 && value.first() == '"' && value.last() == '"')
        val result = StringBuilder()
        var index = 1
        while (index < value.lastIndex) {
            val character = value[index++]
            if (character != '\\') {
                result.append(character)
                continue
            }
            when (val escaped = value[index++]) {
                '"', '\\', '/' -> result.append(escaped)
                'b' -> result.append('\b')
                'f' -> result.append('\u000C')
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                'u' -> {
                    result.append(value.substring(index, index + 4).toInt(16).toChar())
                    index += 4
                }
                else -> error("Invalid JSON escape: $escaped")
            }
        }
        return result.toString()
    }
}

class NavigationGenerations {
    private val value = AtomicLong(0)

    fun beginNavigation(): Long = value.incrementAndGet()

    fun invalidate() {
        value.incrementAndGet()
    }

    fun isCurrent(generation: Long): Boolean = value.get() == generation

    fun current(): Long = value.get()
}

class WebRuntimeController(
    private val webView: WebView,
    private val generations: NavigationGenerations = NavigationGenerations(),
    private val encoder: WebRuntimeScriptEncoder = WebRuntimeScriptEncoder(),
) : RuleRuntime {
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

    fun applyRules(
        generation: Long,
        rules: List<ReplaceRule>,
        onResult: (Boolean) -> Unit,
    ) {
        val operation = encoder.applyRules(rules)
        val script = """
            (function() {
              try {
                if (!window.__NAME_REPLACER__) return false;
                const result = $operation;
                return result === undefined || result.ok === true;
              } catch (_) {
                return false;
              }
            })()
        """.trimIndent()
        evaluateForGeneration(generation, script) { result ->
            onResult(result == "true")
        }
    }

    override suspend fun applyRules(rules: List<ReplaceRule>): Result<Unit> =
        evaluateCurrentRules(rules)

    override suspend fun restoreOriginalText(): Result<Unit> =
        evaluateCurrentRules(emptyList())

    private suspend fun evaluateCurrentRules(rules: List<ReplaceRule>): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            val generation = generations.current()
            applyRules(generation, rules) { success ->
                if (continuation.isActive) {
                    continuation.resume(
                        if (success) Result.success(Unit)
                        else Result.failure(IllegalStateException("网页换名失败")),
                    )
                }
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
