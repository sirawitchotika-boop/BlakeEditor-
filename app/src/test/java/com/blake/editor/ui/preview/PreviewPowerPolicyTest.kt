package com.blake.editor.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewPowerPolicyTest {
    @Test fun pureCursorMovementWaitsLongerThanTyping() {
        assertEquals(600L, PreviewPowerPolicy.autosaveDelay(true))
        assertEquals(1800L, PreviewPowerPolicy.autosaveDelay(false))
    }

    @Test fun unchangedRecoveryRevisionDoesNotWriteAgain() {
        assertFalse(PreviewPowerPolicy.needsWrite("file-a", 5, "file-a", 5))
        assertFalse(PreviewPowerPolicy.needsWrite("file-a", 5, "file-a", 6))
        assertTrue(PreviewPowerPolicy.needsWrite("file-a", 6, "file-a", 5))
    }

    @Test fun changingDocumentsMustNotSkipRecoveryWrite() {
        assertTrue(PreviewPowerPolicy.needsWrite("file-b", 1, "file-a", 999))
        assertTrue(PreviewPowerPolicy.needsWrite("file-b", 1, null, -1))
    }
}
