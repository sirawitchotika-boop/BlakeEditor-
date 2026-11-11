package com.blake.editor.editor

import com.blake.editor.core.model.SyntaxMode
import com.blake.editor.core.model.ThemeMode
import com.blake.editor.editor.v2.CodeSyntaxScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCodePresentationTest {
    @Test fun codeGutterOnlyForCodeDocuments() {
        assertFalse(NativeCodePresentation.showLineNumbers(SyntaxMode.PLAIN_TEXT))
        assertFalse(NativeCodePresentation.showLineNumbers(SyntaxMode.MARKDOWN))
        assertTrue(NativeCodePresentation.showLineNumbers(SyntaxMode.KOTLIN))
        assertTrue(NativeCodePresentation.showLineNumbers(SyntaxMode.PYTHON))
        assertTrue(NativeCodePresentation.showLineNumbers(SyntaxMode.JSON))
        assertTrue(NativeCodePresentation.showLineNumbers(SyntaxMode.RUST))
    }

    @Test fun highlightingHonorsScannerSupportAndCharacterLimit() {
        assertTrue(NativeCodePresentation.shouldHighlight(SyntaxMode.KOTLIN, 120))
        assertTrue(NativeCodePresentation.shouldHighlight(
            SyntaxMode.JSON, CodeSyntaxScanner.MAX_HIGHLIGHT_CHARS))
        assertFalse(NativeCodePresentation.shouldHighlight(SyntaxMode.PLAIN_TEXT, 10))
        assertFalse(NativeCodePresentation.shouldHighlight(SyntaxMode.MARKDOWN, 10))
        assertFalse(NativeCodePresentation.shouldHighlight(SyntaxMode.RUST, 10))
        assertFalse(NativeCodePresentation.shouldHighlight(
            SyntaxMode.KOTLIN, CodeSyntaxScanner.MAX_HIGHLIGHT_CHARS + 1))
    }

    @Test fun codeColorsAdaptToThemeWithoutChangingCharacters() {
        val source = "val message = \"สวัสดีครับ 🌏\""
        val original = source.toCharArray()
        val tokens = CodeSyntaxScanner.scan(source, SyntaxMode.KOTLIN.name)
        assertTrue(tokens.isNotEmpty())
        assertTrue(tokens.all { it.start >= 0 && it.end <= source.length })
        for (theme in ThemeMode.values()) {
            val colors = NativeCodePresentation.colorsFor(theme)
            assertNotEquals(colors.keyword, colors.string)
            assertNotEquals(colors.comment, colors.number)
        }
        assertEquals(String(original), source)
        assertNotEquals(
            NativeCodePresentation.colorsFor(ThemeMode.DARK).keyword,
            NativeCodePresentation.colorsFor(ThemeMode.EYE_CARE).keyword)
    }

    @Test fun renderSpanCountIsBounded() {
        assertTrue(NativeCodePresentation.MAX_RENDER_TOKENS in 1..10_000)
    }
}
