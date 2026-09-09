package com.xiaoshuo.yijianhuanming.content.txt

import com.ibm.icu.text.CharsetDetector
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

data class EncodingCandidate(
    val charsetName: String,
    val confidence: Int,
)

sealed interface EncodingDecision {
    data class Confirmed(
        val charsetName: String,
        val bomBytes: Int,
        val confidence: Int,
    ) : EncodingDecision

    data class NeedsSelection(
        val candidates: List<EncodingCandidate>,
    ) : EncodingDecision
}

class TxtEncodingDetector(
    private val statisticalDetector: (ByteArray) -> List<EncodingCandidate> = ::detectWithIcu,
) {
    fun detect(bytes: ByteArray): EncodingDecision {
        val sample = bytes.copyOf(minOf(bytes.size, TxtLimits.SAMPLE_BYTES))
        detectBom(sample)?.let { return it }
        if (isValidUtf8(sample)) {
            return EncodingDecision.Confirmed("UTF-8", bomBytes = 0, confidence = 100)
        }

        val candidates = statisticalDetector(sample)
            .filter { it.charsetName.isNotBlank() }
            .distinctBy { it.charsetName.uppercase() }
            .sortedByDescending(EncodingCandidate::confidence)
            .take(MAX_CANDIDATES)
        val best = candidates.firstOrNull()
        return if (best != null && best.confidence >= MIN_CONFIDENCE) {
            EncodingDecision.Confirmed(best.charsetName, bomBytes = 0, best.confidence)
        } else {
            EncodingDecision.NeedsSelection(candidates)
        }
    }

    private fun detectBom(sample: ByteArray): EncodingDecision.Confirmed? = when {
        sample.startsWith(UTF8_BOM) -> EncodingDecision.Confirmed("UTF-8", 3, 100)
        sample.startsWith(UTF16_LE_BOM) -> EncodingDecision.Confirmed("UTF-16LE", 2, 100)
        sample.startsWith(UTF16_BE_BOM) -> EncodingDecision.Confirmed("UTF-16BE", 2, 100)
        else -> null
    }

    private fun isValidUtf8(sample: ByteArray): Boolean = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(sample))
        true
    } catch (_: CharacterCodingException) {
        false
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    companion object {
        private const val MIN_CONFIDENCE = 60
        private const val MAX_CANDIDATES = 4
        private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        private val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        private val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

        private fun detectWithIcu(sample: ByteArray): List<EncodingCandidate> =
            CharsetDetector()
                .setText(sample)
                .detectAll()
                .map { EncodingCandidate(it.name, it.confidence) }
    }
}
