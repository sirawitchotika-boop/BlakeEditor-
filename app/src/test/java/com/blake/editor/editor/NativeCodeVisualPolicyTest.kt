package com.blake.editor.editor

import com.blake.editor.core.model.ThemeMode
import com.blake.editor.editor.v2.CodeSyntaxScanner
import com.blake.editor.editor.v2.CodeTokenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCodeVisualPolicyTest {
    @Test fun supportedCodeModesEnableNativeColorAndLineNumbers() {
        listOf("KOTLIN", "JAVA", "JAVASCRIPT", "TYPESCRIPT", "PYTHON",
            "JSON", "HTML", "XML", "CSS", "YAML").forEach { mode ->
            assertTrue("Missing $mode", NativeCodeVisualPolicy.shouldDecorate(mode, 32))
        }
        listOf("SHELL", "C_CPP", "RUST", "GO", "SQL").forEach { mode ->
            assertTrue("Missing line numbers for $mode",
                NativeCodeVisualPolicy.shouldDecorate(mode, 32))
            assertFalse("Unexpected syntax colors for $mode",
                NativeCodeVisualPolicy.shouldHighlight(mode, 32))
        }
        listOf("PLAIN_TEXT", "MARKDOWN").forEach { mode ->
            assertFalse("Unexpected gutter for $mode",
                NativeCodeVisualPolicy.shouldDecorate(mode, 32))
        }
    }

    @Test fun largeTextNeverStartsExpensiveHighlighting() {
        val max = CodeSyntaxScanner.MAX_HIGHLIGHT_CHARS
        assertTrue(NativeCodeVisualPolicy.shouldDecorate("KOTLIN", max))
        assertFalse(NativeCodeVisualPolicy.shouldDecorate("KOTLIN", max + 1))
        assertFalse(NativeCodeVisualPolicy.shouldDecorate("KOTLIN", Int.MAX_VALUE))
        assertFalse(NativeCodeVisualPolicy.shouldHighlight("KOTLIN", max + 1))
        assertTrue(NativeCodeVisualPolicy.shouldHighlight("KOTLIN", max))
    }

    @Test fun themesHaveDistinctHighContrastPaletteIncludingEyeCare() {
        val dark = NativeCodeVisualPolicy.tokenPalette(ThemeMode.DARK)
        val amoled = NativeCodeVisualPolicy.tokenPalette(ThemeMode.AMOLED)
        val paper = NativeCodeVisualPolicy.tokenPalette(ThemeMode.EYE_CARE)
        assertNotEquals(dark.gutterBackground, amoled.gutterBackground)
        assertNotEquals(dark.keyword, paper.keyword)
        assertNotEquals(dark.comment, paper.comment)
        assertEquals(0xFFEDE4D3.toInt(), paper.gutterBackground)
        for (kind in CodeTokenKind.values()) {
            assertNotEquals("Color for $kind", 0, dark.forKind(kind))
            assertNotEquals("Paper color for $kind", 0, paper.forKind(kind))
        }
    }

    @Test fun scanningNeverRewritesThaiEmojiOrSourceOffsets() {
        val text = "val greeting = \"สวัสดีครับ 🌏\"\nreturn greeting"
        val before = text.toByteArray(Charsets.UTF_8)
        val tokens = CodeSyntaxScanner.scan(text, "KOTLIN")
        assertTrue(tokens.any { it.kind == CodeTokenKind.STRING &&
            text.substring(it.start, it.end).contains("สวัสดีครับ 🌏") })
        assertTrue(tokens.all { it.start >= 0 && it.end <= text.length })
        assertTrue(before.contentEquals(text.toByteArray(Charsets.UTF_8)))
    }
}
