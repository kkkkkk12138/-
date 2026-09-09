package com.xiaoshuo.yijianhuanming.reader

import com.xiaoshuo.yijianhuanming.data.ReplaceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRuntimeScriptEncoderTest {
    private val encoder = WebRuntimeScriptEncoder()

    @Test
    fun serializes_hostile_rule_text_as_an_inert_json_string() {
        val hostile = ReplaceRule("1", "\"</script>\n\u2028", "\\新名", 0)

        val script = encoder.applyRules(listOf(hostile))

        assertFalse(script.contains("applyRules([{\"id\""))
        assertFalse(script.contains("</script>"))
        assertFalse(script.contains('\u2028'))
        assertEquals(
            """[{"id":"1","source":"\"\u003C/script>\n\u2028","target":"\\新名","order":0}]""",
            encoder.extractPayload(script),
        )
    }

    @Test
    fun empty_rules_restore_original_text_instead_of_applying_an_empty_payload() {
        val script = encoder.applyRules(emptyList())

        assertTrue(script.contains("restoreOriginalText()"))
        assertFalse(script.contains("applyRules"))
    }
}
