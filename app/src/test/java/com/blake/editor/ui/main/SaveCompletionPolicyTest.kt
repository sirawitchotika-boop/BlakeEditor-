package com.blake.editor.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class SaveCompletionPolicyTest {
    private val original = SaveStateSnapshot("content://original", 4, 12)

    @Test fun unchangedSaveCanFinish() {
        assertEquals(SaveCompletionDecision.CURRENT,
            SaveCompletionPolicy.decide(original, original, writeSucceeded = true))
    }

    @Test fun newTypingDuringWriteCannotFinishOldSave() {
        assertEquals(SaveCompletionDecision.STALE,
            SaveCompletionPolicy.decide(original, original.copy(editRevision = 13), true))
    }

    @Test fun newTypingDuringDraftCleanupCannotMarkClean() {
        val beforeCleanup = SaveCompletionPolicy.decide(original, original, true)
        val afterCleanup = SaveCompletionPolicy.decide(original, original.copy(editRevision = 14), true)
        assertEquals(SaveCompletionDecision.CURRENT, beforeCleanup)
        assertEquals(SaveCompletionDecision.STALE, afterCleanup)
    }

    @Test fun switchingDocumentsRejectsOldCompletion() {
        assertEquals(SaveCompletionDecision.STALE,
            SaveCompletionPolicy.decide(original, original.copy(uriKey = "content://another", documentGeneration = 5), true))
    }

    @Test fun newDocumentGenerationWithSameUriIsStillStale() {
        assertEquals(SaveCompletionDecision.STALE,
            SaveCompletionPolicy.decide(original, original.copy(documentGeneration = 5), true))
    }

    @Test fun failedWriteMustNotMarkClean() {
        assertEquals(SaveCompletionDecision.FAILED,
            SaveCompletionPolicy.decide(original, original, writeSucceeded = false))
    }

    @Test fun saveAsPendingNewEditsMustStayOnOriginalDocument() {
        assertEquals(SaveCompletionDecision.STALE,
            SaveCompletionPolicy.decide(original, original.copy(editRevision = 15), true))
    }
}
