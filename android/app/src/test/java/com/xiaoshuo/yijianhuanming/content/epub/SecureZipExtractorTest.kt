package com.xiaoshuo.yijianhuanming.content.epub

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureZipExtractorTest {
    @Test
    fun extracts_normal_epub2_and_epub3_with_unknown_entry_sizes() {
        listOf("normal-epub2.opf", "normal-epub3.opf").forEach { opf ->
            val root = Files.createTempDirectory("epub-normal").toFile()
            val session = root.resolve("session")
            val archive = epubArchive(resource("normal-container.xml"), resource(opf))

            val result = SecureZipExtractor().extract(ByteArrayInputStream(archive), session)
            val metadata = EpubPackageParser().parse(session)

            assertEquals("OEBPS/content.opf", metadata.packagePath)
            assertTrue(metadata.title.isNotBlank())
            assertTrue(result.totalBytes > 0)
            assertTrue(session.resolve("OEBPS/chapter.xhtml").isFile)
        }
    }

    @Test
    fun rejects_traversal_absolute_backslash_and_nul_names_and_cleans_session() {
        val extractor = SecureZipExtractor()
        listOf("../escape.xhtml", "/absolute.xhtml", "safe\\..\\escape.xhtml").forEach { name ->
            val root = Files.createTempDirectory("epub-path").toFile()
            val session = root.resolve("session")

            val failure = runCatching {
                extractor.extract(ByteArrayInputStream(zipOf(name to "bad")), session)
            }.exceptionOrNull()

            assertTrue("$name should be rejected", failure is EpubValidationException)
            assertFalse(session.exists())
            assertFalse(root.resolve("escape.xhtml").exists())
        }
        assertTrue(
            runCatching { extractor.validateEntryName("bad\u0000.xhtml") }.exceptionOrNull()
                is EpubValidationException,
        )
    }

    @Test
    fun enforces_entry_count_streamed_sizes_and_compression_ratio_then_cleans() {
        val cases = listOf(
            EpubLimits(maxEntries = 2) to zipOf("a" to "1", "b" to "2", "c" to "3"),
            EpubLimits(maxEntryBytes = 4) to zipOf("large" to "12345"),
            EpubLimits(maxTotalBytes = 5) to zipOf("a" to "123", "b" to "456"),
            EpubLimits(maxCompressionRatio = 2) to zipOf("bomb" to "A".repeat(16 * 1024)),
        )

        cases.forEach { (limits, archive) ->
            val session = Files.createTempDirectory("epub-limit").resolve("session").toFile()
            val failure = runCatching {
                SecureZipExtractor(limits).extract(ByteArrayInputStream(archive), session)
            }.exceptionOrNull()

            assertTrue(failure is EpubValidationException)
            assertFalse(session.exists())
        }
        assertEquals(100L * 1024 * 1024, EpubLimits.DEFAULT.maxArchiveBytes)
        assertEquals(500L * 1024 * 1024, EpubLimits.DEFAULT.maxTotalBytes)
        assertEquals(10_000, EpubLimits.DEFAULT.maxEntries)
        assertEquals(50L * 1024 * 1024, EpubLimits.DEFAULT.maxEntryBytes)
        assertEquals(100, EpubLimits.DEFAULT.maxCompressionRatio)
    }

    @Test
    fun rejects_dtd_xxe_drm_and_fixed_layout() {
        val hostilePackages = listOf(
            resource("malicious-dtd.opf"),
            normalOpf("<meta property=\"rendition:layout\">pre-paginated</meta>"),
            normalOpf("<meta name=\"fixed-layout\" content=\"true\"/>"),
        )
        hostilePackages.forEach { opf ->
            val session = extractForParsing(epubArchive(resource("normal-container.xml"), opf))
            assertTrue(
                runCatching { EpubPackageParser().parse(session) }.exceptionOrNull()
                    is EpubValidationException,
            )
        }

        val drm = epubArchive(
            resource("normal-container.xml"),
            resource("normal-epub3.opf"),
            "META-INF/encryption.xml" to "<encryption/>",
        )
        val drmSession = extractForParsing(drm)
        assertTrue(
            runCatching { EpubPackageParser().parse(drmSession) }.exceptionOrNull()
                is EpubValidationException,
        )
    }

    @Test
    fun rejects_a_zip_that_is_not_an_epub_and_cleans_the_session() {
        val archive = epubArchive(
            resource("normal-container.xml"),
            resource("normal-epub3.opf"),
            "mimetype" to "application/zip",
        )
        val session = extractForParsing(archive)

        assertTrue(
            runCatching { EpubPackageParser().parse(session) }.exceptionOrNull()
                is EpubValidationException,
        )
        assertFalse(session.exists())
    }

    @Test
    fun cache_manager_removes_incomplete_sessions_and_evicts_lru_complete_sessions() {
        val root = Files.createTempDirectory("epub-cache").toFile()
        val cache = CacheManager(root, maxBytes = 6)
        val incomplete = cache.createIncompleteSession("broken").apply {
            resolve("part").writeText("partial")
        }
        val old = cache.createIncompleteSession("old").apply {
            resolve("book").writeText("1234")
        }.let(cache::commitSession)
        old.setLastModified(1)
        val recent = cache.createIncompleteSession("recent").apply {
            resolve("book").writeText("5678")
        }.let(cache::commitSession)
        recent.setLastModified(2)

        cache.cleanup()

        assertFalse(incomplete.exists())
        assertFalse(old.exists())
        assertTrue(recent.exists())
        cache.clear()
        assertFalse(recent.exists())
    }

    private fun extractForParsing(archive: ByteArray): File {
        val session = Files.createTempDirectory("epub-package").resolve("session").toFile()
        SecureZipExtractor().extract(ByteArrayInputStream(archive), session)
        return session
    }

    private fun epubArchive(
        container: String,
        opf: String,
        vararg extras: Pair<String, String>,
    ): ByteArray {
        val entries = linkedMapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to container,
            "OEBPS/content.opf" to opf,
            "OEBPS/chapter.xhtml" to "<html><body><p>正文</p></body></html>",
        )
        entries.putAll(extras)
        return zipOf(*entries.map { it.key to it.value }.toTypedArray())
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResource("/fixtures/epub/$name")).readText()

    private fun normalOpf(extraMetadata: String): String =
        """<?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>恶意样书</dc:title>$extraMetadata
              </metadata>
              <manifest/><spine/>
            </package>
        """.trimIndent()
}
