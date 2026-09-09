package com.xiaoshuo.yijianhuanming.content.txt

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtEncodingDetectorTest {
    @Test
    fun detects_utf8_and_utf16_boms_before_statistical_detection() {
        val detector = TxtEncodingDetector { error("BOM must win") }

        assertEquals(
            EncodingDecision.Confirmed("UTF-8", 3, 100),
            detector.detect(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0x41)),
        )
        assertEquals(
            EncodingDecision.Confirmed("UTF-16LE", 2, 100),
            detector.detect(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x41, 0x00)),
        )
        assertEquals(
            EncodingDecision.Confirmed("UTF-16BE", 2, 100),
            detector.detect(byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0x00, 0x41)),
        )
    }

    @Test
    fun detects_valid_utf8_without_a_bom() {
        val detector = TxtEncodingDetector()

        val result = detector.detect("第一章：沈清辞".toByteArray(Charsets.UTF_8))

        assertTrue(result is EncodingDecision.Confirmed)
        assertEquals("UTF-8", (result as EncodingDecision.Confirmed).charsetName)
    }

    @Test
    fun accepts_high_confidence_gb18030_from_icu() {
        val bytes = "第一章".toByteArray(Charset.forName("GB18030"))
        val detector = TxtEncodingDetector {
            listOf(EncodingCandidate("GB18030", 91), EncodingCandidate("UTF-8", 10))
        }

        assertEquals(
            EncodingDecision.Confirmed("GB18030", 0, 91),
            detector.detect(bytes),
        )
    }

    @Test
    fun returns_candidates_instead_of_auto_continuing_on_low_confidence() {
        val detector = TxtEncodingDetector {
            listOf(EncodingCandidate("GB18030", 49), EncodingCandidate("Big5", 31))
        }

        val result = detector.detect(byteArrayOf(0x81.toByte(), 0x30))

        assertEquals(
            EncodingDecision.NeedsSelection(
                listOf(EncodingCandidate("GB18030", 49), EncodingCandidate("Big5", 31)),
            ),
            result,
        )
    }

    @Test
    fun statistical_detection_never_receives_more_than_64_kib() {
        var observedSize = 0
        val detector = TxtEncodingDetector {
            observedSize = it.size
            listOf(EncodingCandidate("windows-1252", 20))
        }

        detector.detect(ByteArray(TxtLimits.SAMPLE_BYTES * 2) { 0x80.toByte() })

        assertEquals(64 * 1024, TxtLimits.SAMPLE_BYTES)
        assertEquals(TxtLimits.SAMPLE_BYTES, observedSize)
    }
}
