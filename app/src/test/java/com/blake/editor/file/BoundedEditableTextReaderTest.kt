package com.blake.editor.file

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedEditableTextReaderTest {
    @Test fun preservesThaiEnglishEmojiForSmallDocuments() {
        val content = "Hello สวัสดีครับ 🌏\nSecond line"
        assertEquals(content, BoundedEditableTextReader.readUtf8(
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))))
    }

    @Test fun refusesLargeTextEvenIfCallerSkipsExternalIntentGate() {
        val bytes = ByteArray(LargeTextOpenGate.MAX_EDIT_BYTES + 1) { 65 }
        val thrown = runCatching {
            BoundedEditableTextReader.readUtf8(ByteArrayInputStream(bytes))
        }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
        assertTrue(thrown?.message?.contains("Large Text Reader") == true)
    }

    @Test fun exactTwoMiBBoundaryIsAllowed() {
        val bytes = ByteArray(LargeTextOpenGate.MAX_EDIT_BYTES) { 65 }
        assertEquals(bytes.size,
            BoundedEditableTextReader.readUtf8(ByteArrayInputStream(bytes)).length)
    }
}
