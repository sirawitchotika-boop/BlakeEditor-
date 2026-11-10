package com.blake.editor.ui.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputProbeSamplesTest {
    @Test fun sampleSetIncludesThaiMixedEmojiWrapAndCode() {
        assertEquals(
            listOf("Thai", "Mixed", "Emoji", "Wrapped", "Code"),
            InputProbeSamples.all.map { it.name }
        )
    }

    @Test fun thaiAndCodeSamplesContainCombiningMarks() {
        assertTrue(InputProbeSamples.all.first().content.contains('\u0E31'))
        assertTrue(InputProbeSamples.all.last().content.contains("สวัสดีครับ"))
    }

    @Test fun emojiSampleContainsSurrogatePairs() {
        val emoji = InputProbeSamples.all[2].content
        assertTrue(emoji.length > Character.codePointCount(emoji, 0, emoji.length))
    }
}
