package com.xiaoshuo.yijianhuanming.content.txt

import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.FileInputStream

class TxtAssetPathHandler(
    private val manifest: TxtChunkManifest,
) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        val match = PATH.matchEntire(path.trimStart('/')) ?: return null
        if (match.groupValues[1] != manifest.sessionId) return null
        val index = match.groupValues[2].toIntOrNull() ?: return null
        val chunk = manifest.chunks.getOrNull(index) ?: return null
        if (!chunk.file.isFile) return null

        return WebResourceResponse(
            "text/html",
            "UTF-8",
            200,
            "OK",
            mapOf(
                "Cache-Control" to "no-store",
                "X-Content-Type-Options" to "nosniff",
                "X-Character-Start" to chunk.startCharacterOffset.toString(),
                "X-Character-End" to chunk.endCharacterOffset.toString(),
            ),
            FileInputStream(chunk.file),
        )
    }

    private companion object {
        val PATH = Regex("([A-Za-z0-9_-]{1,80})/(\\d+)")
    }
}
