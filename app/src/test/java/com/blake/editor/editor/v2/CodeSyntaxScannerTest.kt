package com.blake.editor.editor.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSyntaxScannerTest {
    @Test fun kotlinRecognizesKeywordStringCommentAndNumber() {
        val code = "val message = \"hello\" // comment\nval number = 42"
        val tokens = CodeSyntaxScanner.scan(code, "KOTLIN")
        fun has(word: String, kind: CodeTokenKind): Boolean =
            tokens.any { code.substring(it.start, it.end) == word && it.kind == kind }
        assertTrue(has("val", CodeTokenKind.KEYWORD))
        assertTrue(has("\"hello\"", CodeTokenKind.STRING))
        assertTrue(has("// comment", CodeTokenKind.COMMENT))
        assertTrue(has("42", CodeTokenKind.NUMBER))
    }

    @Test fun keywordsInsideStringsOrCommentsAreNotHighlightedSeparately() {
        val code = "val text = \"class return\" // for while\nclass Example"
        val words = CodeSyntaxScanner.scan(code, "KOTLIN")
            .filter { it.kind == CodeTokenKind.KEYWORD }
            .map { code.substring(it.start, it.end) }
        assertEquals(listOf("val", "class"), words)
    }

    @Test fun thaiEmojiInStringsRemainUntouchedAndSpansUseUtf16Offsets() {
        val code = "val greeting = \"สวัสดีครับ 🌏\""
        val tokens = CodeSyntaxScanner.scan(code, "KOTLIN")
        assertTrue(tokens.any { code.substring(it.start, it.end) ==
            "\"สวัสดีครับ 🌏\"" && it.kind == CodeTokenKind.STRING })
        assertTrue(tokens.all { it.start >= 0 && it.end <= code.length })
    }

    @Test fun pythonTripleQuoteAndHashCommentHaveNoOverlaps() {
        val code = "def foo():\n  \"\"\"hello\n  return 9\"\"\"\n  # comment\n  return 2"
        val tokens = CodeSyntaxScanner.scan(code, "PYTHON")
        assertTrue(tokens.zipWithNext().all { (a, b) -> a.end <= b.start })
        assertTrue(tokens.any { it.kind == CodeTokenKind.COMMENT })
        assertTrue(tokens.any { it.kind == CodeTokenKind.STRING })
    }

    @Test fun jsonAndHtmlAreSupported() {
        val json = """{"name":"hello","ok":true,"count":10}"""
        val jsonTokens = CodeSyntaxScanner.scan(json, "JSON")
        assertTrue(jsonTokens.any { it.kind == CodeTokenKind.KEYWORD })
        assertTrue(jsonTokens.any { it.kind == CodeTokenKind.NUMBER })
        val html = "<div class=\"note\">Thai สวัสดี</div>"
        val tags = CodeSyntaxScanner.scan(html, "HTML")
            .filter { it.kind == CodeTokenKind.TAG }
        assertEquals(2, tags.size)
    }

    @Test fun unsupportedAndLongTextAreLeftAlone() {
        assertTrue(CodeSyntaxScanner.scan("hello", "PLAIN_TEXT").isEmpty())
        assertTrue(CodeSyntaxScanner.scan("x".repeat(80_001), "KOTLIN").isEmpty())
    }

    @Test fun incompleteStringIsSafe() {
        val source = "val message = \"unfinished"
        val tokens = CodeSyntaxScanner.scan(source, "KOTLIN")
        assertTrue(tokens.all { it.end <= source.length })
    }
}
