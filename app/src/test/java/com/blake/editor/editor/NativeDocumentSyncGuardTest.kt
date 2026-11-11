package com.blake.editor.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDocumentSyncGuardTest {
    @Test fun textAndCursorEditsMustNeverReloadTheInputConnection() {
        val guard = NativeDocumentSyncGuard()
        assertTrue(guard.shouldLoad(0))
        repeat(100) { assertFalse(guard.shouldLoad(0)) }
        assertTrue(guard.shouldLoad(1))
        assertFalse(guard.shouldLoad(1))
    }

    @Test fun reopeningSameFileMayReloadEvenWithSameText() {
        val guard = NativeDocumentSyncGuard()
        assertTrue(guard.shouldLoad(7))
        assertTrue(guard.shouldLoad(8))
    }

    @Test fun cursorPositionSupportsWrappedLinesThaiAndEmoji() {
        val text = "abc\nสวัสดี 🌏\nend"
        assertEquals(0, NativeDocumentSyncGuard.offsetForLineColumn(text, 1, 1))
        assertEquals(4, NativeDocumentSyncGuard.offsetForLineColumn(text, 2, 1))
        assertEquals(text.indexOf("🌏") + 2,
            NativeDocumentSyncGuard.offsetForLineColumn(text, 2, 10))
        assertEquals(text.indexOf("end"),
            NativeDocumentSyncGuard.offsetForLineColumn(text, 3, 1))
        assertEquals(text.length,
            NativeDocumentSyncGuard.offsetForLineColumn(text, 100, 999))
    }
}
