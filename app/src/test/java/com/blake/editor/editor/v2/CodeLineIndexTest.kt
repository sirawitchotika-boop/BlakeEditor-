package com.blake.editor.editor.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeLineIndexTest {
    @Test fun lfAndCrLfEachStartOneNewLine() {
        val source = "one\nThai สวัสดี\r\nthird"
        val index = CodeLineIndex().apply { rebuild(source) }
        assertEquals(3, index.lineCount)
        assertEquals(1, index.lineAt(0))
        assertEquals(2, index.lineAt(4))
        assertEquals(3, index.lineAt(source.indexOf("third")))
        assertTrue(index.isLogicalLineStart(source.indexOf("third")))
        assertFalse(index.isLogicalLineStart(source.indexOf("Thai") + 2))
    }
    @Test fun bareCrAndTrailingNewlineAreHandled() {
        val index = CodeLineIndex().apply { rebuild("a\rb\n") }
        assertEquals(3, index.lineCount)
        assertEquals(3, index.lineAt(4))
        assertTrue(index.isLogicalLineStart(4))
    }
    @Test fun immediateRepeatedTextRebuildsKeepLineStateValid() {
        // Plain Kotlin coverage; Android constructor callback ordering still
        // requires an on-device smoke test of PreviewCodeEditText.
        val index = CodeLineIndex()
        index.rebuild("")
        index.rebuild("val answer = 42")
        assertEquals(1, index.lineCount)
        index.rebuild("val text = \"สวัสดีครับ 🌏\"\nreturn text")
        assertEquals(2, index.lineCount)
        assertEquals(2, index.lineAt(
            "val text = \"สวัสดีครับ 🌏\"\nreturn text".indexOf("return")))
        index.rebuild("again")
        assertEquals(1, index.lineCount)
    }

    @Test fun emptyFileHasFirstLine() {
        val index = CodeLineIndex()
        index.rebuild("")
        assertEquals(1, index.lineCount)
        assertTrue(index.isLogicalLineStart(0))
    }
    @Test fun emojiUsesUtf16OffsetsWithoutBreakingLineNumbers() {
        val source = "🌏\nabc"
        val index = CodeLineIndex().apply { rebuild(source) }
        assertEquals(2, index.lineAt(source.indexOf("abc")))
    }
    @Test fun recalculationAfterEditRemovesOldOffsets() {
        val index = CodeLineIndex().apply { rebuild("one\ntwo\nthree") }
        assertEquals(3, index.lineCount)
        index.rebuild("one two three")
        assertEquals(1, index.lineCount)
    }
}
