package com.blake.editor.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class V2EntryPolicyTest {
    @Test fun aCleanIdleOriginalCanOpenV2() {
        assertEquals(V2EntryDecision.OPEN, V2EntryPolicy.decide(false, false))
    }

    @Test fun dirtyOriginalRequiresConfirmationWithoutDiscardingText() {
        assertEquals(V2EntryDecision.CONFIRM_UNSAVED,
            V2EntryPolicy.decide(true, false))
    }

    @Test fun activeFileOperationAlwaysBlocksSwitchingEvenWithUnsavedEdits() {
        assertEquals(V2EntryDecision.BLOCK_BUSY,
            V2EntryPolicy.decide(false, true))
        assertEquals(V2EntryDecision.BLOCK_BUSY,
            V2EntryPolicy.decide(true, true))
    }
}
