package com.blake.editor.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownFormatActionsTest {

    @Test
    fun boldWrapsSelectionOrPlacesCursorInsideMarkers() {
        val wrapped = MarkdownFormatActions.apply("hello world", 6, 11, MarkdownAction.BOLD)
        assertEquals("hello **world**", wrapped.text)
        assertEquals(8, wrapped.selectionStart)
        assertEquals(13, wrapped.selectionEnd)

        val inserted = MarkdownFormatActions.apply("hello ", 6, 6, MarkdownAction.BOLD)
        assertEquals("hello ****", inserted.text)
        assertEquals(8, inserted.selectionStart)
        assertEquals(8, inserted.selectionEnd)
    }

    @Test
    fun headingCyclesFromH1ToH3ThenRemoves() {
        val h1 = MarkdownFormatActions.apply("Title", 5, 5, MarkdownAction.HEADING)
        assertEquals("# Title", h1.text)
        val h2 = MarkdownFormatActions.apply(h1.text, h1.selectionStart, h1.selectionEnd, MarkdownAction.HEADING)
        assertEquals("## Title", h2.text)
        val h3 = MarkdownFormatActions.apply(h2.text, h2.selectionStart, h2.selectionEnd, MarkdownAction.HEADING)
        assertEquals("### Title", h3.text)
        val plain = MarkdownFormatActions.apply(h3.text, h3.selectionStart, h3.selectionEnd, MarkdownAction.HEADING)
        assertEquals("Title", plain.text)
    }

    @Test
    fun checklistPrefixesCurrentLineOnly() {
        val result = MarkdownFormatActions.apply("line one\nline two", 12, 12, MarkdownAction.CHECKLIST)
        assertEquals("line one\n- [ ] line two", result.text)
    }

    @Test
    fun linkWrapsSelectionAndSelectsUrlPlaceholder() {
        val result = MarkdownFormatActions.apply("Visit Blake now", 6, 11, MarkdownAction.LINK)
        assertEquals("Visit [Blake](https://) now", result.text)
        assertEquals("https://", result.text.substring(result.selectionStart, result.selectionEnd))
    }
}
