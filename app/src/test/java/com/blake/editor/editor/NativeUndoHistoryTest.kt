package com.blake.editor.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeUndoHistoryTest {
    @Test fun canUndoAndRedoTypingAndDeleting() {
        val history = NativeUndoHistory()
        history.reset("abc")
        history.edited("abcd", 1000L)
        history.edited("abcde", 1100L)
        history.edited("abcd", 1200L)
        history.edited("abc", 1300L)
        assertTrue(history.canUndo)
        assertEquals("abc", history.undo())
        assertEquals("abc", history.redo())
    }

    @Test fun oneGroupedEditRestoresThaiAndEmoji() {
        val history = NativeUndoHistory()
        history.reset("สวัสดี 🌏")
        history.edited("สวัสดี ", 1000L)
        history.edited("สวัสดี", 1100L)
        history.edited("สวั", 1200L)
        assertEquals("สวัสดี 🌏", history.undo())
        assertEquals("สวั", history.redo())
    }

    @Test fun separateGroupsKeepOldAndNewContent() {
        val history = NativeUndoHistory()
        history.reset("one")
        history.edited("one two", 100L)
        history.edited("one two three", 2000L)
        assertEquals("one two", history.undo())
        assertEquals("one", history.undo())
        assertFalse(history.canUndo)
        assertEquals("one two", history.redo())
        assertEquals("one two three", history.redo())
    }

    @Test fun typingAfterUndoInvalidatesRedo() {
        val history = NativeUndoHistory()
        history.reset("start")
        history.edited("start a", 10L)
        history.undo()
        assertTrue(history.canRedo)
        history.edited("start b", 20L)
        assertFalse(history.canRedo)
        assertNull(history.redo())
    }

    @Test fun openingFileClearsOldUndoHistory() {
        val history = NativeUndoHistory()
        history.reset("document A")
        history.edited("document A!", 10L)
        history.reset("document B")
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertNull(history.undo())
    }

    @Test fun veryLargeDocumentsDoNotPopulateUndoHistory() {
        val history = NativeUndoHistory()
        history.reset("X".repeat(150_000))
        history.edited("X".repeat(149_999), 10L)
        assertFalse(history.canUndo)
    }
}
