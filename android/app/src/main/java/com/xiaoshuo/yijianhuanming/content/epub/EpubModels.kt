package com.xiaoshuo.yijianhuanming.content.epub

import java.nio.charset.StandardCharsets
import java.util.Base64

data class EpubChapter(
    val id: String,
    val title: String,
    val href: String,
    val previousChapterId: String?,
    val nextChapterId: String?,
)

data class EpubTocEntry(
    val title: String,
    val chapterId: String,
    val fragment: String? = null,
    val children: List<EpubTocEntry> = emptyList(),
)

data class EpubLocation(
    val chapterId: String,
    val scrollRatio: Double,
) {
    fun encode(): String {
        val encodedId = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(chapterId.toByteArray(StandardCharsets.UTF_8))
        return "$encodedId:$scrollRatio"
    }

    companion object {
        fun decode(value: String): EpubLocation? = runCatching {
            val parts = value.split(':', limit = 2)
            require(parts.size == 2)
            val chapterId = String(
                Base64.getUrlDecoder().decode(parts[0]),
                StandardCharsets.UTF_8,
            )
            val ratio = parts[1].toDouble()
            require(chapterId.isNotBlank() && ratio.isFinite())
            EpubLocation(chapterId, ratio)
        }.getOrNull()
    }
}

data class EpubBook(
    val title: String,
    val packagePath: String,
    val chapters: List<EpubChapter>,
    val tableOfContents: List<EpubTocEntry>,
    val allowedResources: Set<String>,
) {
    fun previousChapter(chapterId: String): EpubChapter? =
        chapter(chapter(chapterId)?.previousChapterId)

    fun nextChapter(chapterId: String): EpubChapter? =
        chapter(chapter(chapterId)?.nextChapterId)

    fun chapter(chapterId: String?): EpubChapter? =
        chapters.firstOrNull { it.id == chapterId }

    fun restoreLocation(location: EpubLocation?): EpubLocation {
        val first = chapters.firstOrNull()
            ?: throw EpubValidationException("EPUB 书脊没有可阅读章节")
        return location
            ?.takeIf { it.scrollRatio.isFinite() && it.scrollRatio in 0.0..1.0 }
            ?.takeIf { candidate -> chapters.any { it.id == candidate.chapterId } }
            ?: EpubLocation(first.id, 0.0)
    }
}
