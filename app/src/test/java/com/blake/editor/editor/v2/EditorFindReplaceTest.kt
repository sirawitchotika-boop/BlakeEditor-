package com.blake.editor.editor.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorFindReplaceTest {
    @Test fun nextWrapsAndPreviousReturnsPreviousMatch() {
        val text = "one two one three"
        assertEquals(TextMatch(8, 11), EditorFindReplace.next(text, "one", 3))
        assertEquals(TextMatch(0, 3), EditorFindReplace.next(text, "one", 11))
        assertEquals(TextMatch(8, 11), EditorFindReplace.previous(text, "one", 11))
        assertEquals(TextMatch(0, 3), EditorFindReplace.previous(text, "one", 8))
        assertEquals(TextMatch(8, 11), EditorFindReplace.previous(text, "one", 0))
    }

    @Test fun emptyAndMissingQueriesNeverSelectOrModifyText() {
        assertNull(EditorFindReplace.next("abc", "", 0))
        assertNull(EditorFindReplace.previous("abc", "", 2))
        assertNull(EditorFindReplace.next("abc", "zzz", 0))
        assertEquals(ReplaceAllResult("abc", 0),
            EditorFindReplace.replaceAll("abc", "not found", "changed"))
        assertTrue(runCatching {
            EditorFindReplace.replaceAll("abc", "", "X")
        }.isFailure)
    }

    @Test fun mixedThaiEnglishEmojiSearchUsesAndroidUtf16Offsets() {
        val text = "Thai สวัสดีครับ 🌏 Thai สวัสดีครับ"
        val second = text.lastIndexOf("สวัสดีครับ")
        assertEquals(TextMatch(second, second + "สวัสดีครับ".length),
            EditorFindReplace.next(text, "สวัสดีครับ", 10))
        val emoji = text.indexOf("🌏")
        assertEquals(TextMatch(emoji, emoji + 2),
            EditorFindReplace.next(text, "🌏", 0))
        assertEquals("Thai ไทย 🌏 Thai ไทย",
            EditorFindReplace.replaceAll(text, "สวัสดีครับ", "ไทย").text)
    }

    @Test fun caseInsensitiveReplacementAndLiteralSymbols() {
        assertEquals(ReplaceAllResult("x x x", 3),
            EditorFindReplace.replaceAll("A a a", "a", "x", true))
        assertEquals(ReplaceAllResult("[ok] [ok]", 2),
            EditorFindReplace.replaceAll("a.* a.*", "a.*", "[ok]"))
        assertEquals(ReplaceAllResult("A x", 1),
            EditorFindReplace.replaceAll("A a", "a", "x", false))
    }

    @Test fun replacementContainingItsOwnQueryTerminates() {
        assertEquals(ReplaceAllResult("aaaaaa", 3),
            EditorFindReplace.replaceAll("aaa", "a", "aa"))
        assertEquals(ReplaceAllResult("", 3),
            EditorFindReplace.replaceAll("aAa", "a", "", true))
    }

    @Test fun selectionMatchingRequiresExactText() {
        val text = "val x = 10"
        assertTrue(EditorFindReplace.selectionMatches(text, 0, 3, "val"))
        assertFalse(EditorFindReplace.selectionMatches(text, 0, 2, "val"))
        assertTrue(EditorFindReplace.selectionMatches("Kotlin", 0, 6, "kotlin", true))
    }

    @Test fun quickToolbarReplacesOnlySelectionAndPreservesThaiEmoji() {
        val text = "สวัสดีครับ 🌏 code"
        val start = text.indexOf("code")
        val inserted = EditorFindReplace.insert(text, start, start + 4, "{}")
        assertEquals("สวัสดีครับ 🌏 {}", inserted.text)
        assertEquals(text.indexOf("code") + 2, inserted.cursor)
        val tab = EditorFindReplace.insert("line", 0, 0, "    ")
        assertEquals("    line", tab.text)
        assertEquals(4, tab.cursor)
    }
}
