package com.blake.editor.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSearchTest {

    @Test
    fun findAllFindsCaseInsensitiveAndThaiMatches() {
        val text = "Hello โลก hello โลก HELLO"
        val en = DocumentSearch.findAll(text, "hello", ignoreCase = true)
        assertEquals(3, en.size)
        assertEquals(TextMatch(0, 5), en[0])
        assertEquals(TextMatch(10, 15), en[1])
        assertEquals(TextMatch(20, 25), en[2])

        val enSensitive = DocumentSearch.findAll(text, "hello", ignoreCase = false)
        assertEquals(listOf(TextMatch(10, 15)), enSensitive)

        val thai = DocumentSearch.findAll(text, "โลก", ignoreCase = true)
        assertEquals(2, thai.size)
    }

    @Test
    fun nearestMatchPicksForwardMatchOrWraps() {
        val matches = listOf(TextMatch(2, 5), TextMatch(12, 15), TextMatch(30, 33))
        assertEquals(0, DocumentSearch.nearestMatchIndex(matches, 0))
        assertEquals(1, DocumentSearch.nearestMatchIndex(matches, 6))
        assertEquals(2, DocumentSearch.nearestMatchIndex(matches, 30))
        assertEquals(0, DocumentSearch.nearestMatchIndex(matches, 50))
        assertEquals(-1, DocumentSearch.nearestMatchIndex(emptyList(), 0))
    }

    @Test
    fun nextAndPreviousWrapCleanly() {
        assertEquals(1, DocumentSearch.nextIndex(0, 3))
        assertEquals(0, DocumentSearch.nextIndex(2, 3))
        assertEquals(2, DocumentSearch.previousIndex(0, 3))
        assertEquals(1, DocumentSearch.previousIndex(2, 3))
    }

    @Test
    fun replaceAtAndReplaceAllTerminateSafely() {
        val single = DocumentSearch.replaceAt("abc def abc", TextMatch(4, 7), "XYZ")
        assertEquals("abc XYZ abc", single.text)
        assertEquals(7, single.newCursorOffset)

        val expanded = DocumentSearch.replaceAll("aaa", "a", "aa")
        assertEquals("aaaaaa", expanded.text)
        assertEquals(3, expanded.replacedCount)
        assertTrue(DocumentSearch.findAll("text", "").isEmpty())
    }
}
