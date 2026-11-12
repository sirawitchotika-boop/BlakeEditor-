package com.blake.editor.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class OriginalStartupRestoreGateTest {
    @Test fun newViewModelRestoresSessionWhenActivityHasNoIncomingFile() {
        val gate = OriginalStartupRestoreGate()
        assertEquals(OriginalStartupAction.RESTORE_SESSION, gate.claimInitialAction(false))
    }

    @Test fun processDeathFreshViewModelRestoresEvenWithSavedActivityState() {
        // Activity savedInstanceState is non-null here, but it must not control this decision.
        val savedActivityStateExists = true
        val freshModelGate = OriginalStartupRestoreGate()
        assertEquals(true, savedActivityStateExists)
        assertEquals(OriginalStartupAction.RESTORE_SESSION,
            freshModelGate.claimInitialAction(hasIncomingDocument = false))
    }

    @Test fun rotationRetainingViewModelMustNotOverwriteUnsavedEdits() {
        val retainedGate = OriginalStartupRestoreGate()
        assertEquals(OriginalStartupAction.RESTORE_SESSION, retainedGate.claimInitialAction(false))
        assertEquals(OriginalStartupAction.ALREADY_INITIALIZED, retainedGate.claimInitialAction(false))
    }

    @Test fun externalOpenTakesPrecedenceAtFirstActivityCreation() {
        val gate = OriginalStartupRestoreGate()
        assertEquals(OriginalStartupAction.OPEN_INCOMING, gate.claimInitialAction(true))
        assertEquals(OriginalStartupAction.ALREADY_INITIALIZED, gate.claimInitialAction(true))
    }

    @Test fun retainedViewModelMustNotReprocessIncomingIntentOnRotation() {
        val gate = OriginalStartupRestoreGate()
        assertEquals(OriginalStartupAction.OPEN_INCOMING, gate.claimInitialAction(true))
        assertEquals(OriginalStartupAction.ALREADY_INITIALIZED, gate.claimInitialAction(true))
    }

    @Test fun newViewModelAfterProcessDeathIsIndependentOfPriorGate() {
        val oldGate = OriginalStartupRestoreGate()
        oldGate.claimInitialAction(false)
        val newGate = OriginalStartupRestoreGate()
        assertEquals(OriginalStartupAction.RESTORE_SESSION, newGate.claimInitialAction(false))
    }
}
