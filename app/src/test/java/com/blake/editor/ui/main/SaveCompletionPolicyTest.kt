package com.blake.editor.ui.main

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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

    @Test fun overlappingSaveWritesAreSerialized() = runBlocking {
        val gate = SerializedDocumentSaveGate()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val first = launch {
            gate.run {
                order.add("first-start")
                firstEntered.complete(Unit)
                releaseFirst.await()
                order.add("first-end")
            }
        }
        firstEntered.await()
        val second = launch {
            gate.run { order.add("second") }
        }
        yield()
        assertEquals(listOf("first-start"), order)
        releaseFirst.complete(Unit)
        first.join()
        second.join()
        assertEquals(listOf("first-start", "first-end", "second"), order)
    }

    @Test fun failedWriteDoesNotKeepSaveGateLocked() = runBlocking {
        val gate = SerializedDocumentSaveGate()
        try {
            gate.run<Unit> { error("simulated failure") }
        } catch (_: IllegalStateException) {
            // Expected. Finally must release the mutex for the next Save.
        }
        assertEquals(42, gate.run { 42 })
    }
}
