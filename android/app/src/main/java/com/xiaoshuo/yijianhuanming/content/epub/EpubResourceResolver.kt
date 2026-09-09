package com.xiaoshuo.yijianhuanming.content.epub

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

class EpubResourceResolver(
    private val sessionId: String,
    allowedPaths: Set<String>,
) {
    private val allowed = allowedPaths.mapNotNullTo(hashSetOf()) { normalize(it) }

    init {
        require(SESSION.matches(sessionId)) { "EPUB 会话标识无效" }
    }

    fun resolvePath(basePath: String, reference: String): String? {
        if (
            reference.isBlank() ||
            '\u0000' in reference ||
            reference.startsWith("/") ||
            reference.startsWith("\\") ||
            reference.startsWith("//")
        ) {
            return null
        }
        if (SCHEME.matches(reference)) return null
        val relativePath = reference.substringBefore('#').substringBefore('?')
            .takeIf(String::isNotBlank) ?: return normalize(basePath)
        val parent = Paths.get(basePath.replace('\\', '/')).parent ?: Paths.get("")
        val resolved = normalize(parent.resolve(relativePath).normalize().toString()) ?: return null
        return resolved.takeIf(allowed::contains)
    }

    fun resolveUrl(basePath: String, reference: String): String? =
        resolvePath(basePath, reference)?.let(::appAssetsUrl)

    fun appAssetsUrl(path: String): String {
        val normalized = normalize(path)
            ?.takeIf(allowed::contains)
            ?: throw EpubValidationException("EPUB 资源不在会话白名单")
        val encoded = normalized.split('/').joinToString("/") {
            URLEncoder.encode(it, StandardCharsets.UTF_8.name()).replace("+", "%20")
        }
        return "$APP_ASSETS_ROOT/$sessionId/$encoded"
    }

    private fun normalize(path: String): String? = runCatching {
        val normalized = Paths.get(path.replace('\\', '/')).normalize().toString().replace('\\', '/')
        normalized.takeUnless {
            it.isBlank() || it == ".." || it.startsWith("../") || it.startsWith("/")
        }
    }.getOrNull()

    companion object {
        const val APP_ASSETS_ROOT = "https://appassets.androidplatform.net/epub"
        private val SESSION = Regex("[A-Za-z0-9._-]{1,80}")
        private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:.*")
    }
}
