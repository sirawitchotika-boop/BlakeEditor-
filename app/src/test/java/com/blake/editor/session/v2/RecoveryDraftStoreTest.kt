package com.blake.editor.session.v2

import java.io.File
import java.nio.file.Files
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecoveryDraftStoreTest {
    private lateinit var dir: File
    private lateinit var store: RecoveryDraftStore

    @Before fun setUp() {
        dir = Files.createTempDirectory("blake-v2-drafts").toFile()
        store = RecoveryDraftStore(File(dir, "v2"))
    }

    private fun draft(id: DocumentIdentity, text: String, version: Long = 1): RecoveryDraft =
        RecoveryDraft(id, null, "Untitled", "PLAIN_TEXT", text, revision = version)

    @Test fun twoUntitledDocumentsCannotOverwriteEachOther() {
        val one = DocumentIdentity.newUntitled()
        val two = DocumentIdentity.newUntitled()
        assertNotEquals(one, two)
        assertTrue(store.save(draft(one, "first")).isSuccess)
        assertTrue(store.save(draft(two, "second")).isSuccess)
        assertEquals("first", store.load(one).getOrThrow()?.content)
        assertEquals("second", store.load(two).getOrThrow()?.content)
        assertEquals(2, store.list().drafts.size)
        assertTrue(store.list().unreadableFiles.isEmpty())
    }

    @Test fun reservationIsDurableEvenIfTheDraftIsNotCreated() {
        // A crash between counter reservation and draft creation must skip that number.
        assertEquals(1L, store.reserveUntitledNumber().getOrThrow())
        val next = DraftWorkspace(RecoveryDraftStore(File(dir, "v2")))
            .createUntitled().getOrThrow()
        assertEquals("Untitled 2", next.displayName)
    }

    @Test fun firstCounterStartsAbovePreexistingNumberedDrafts() {
        val older = draft(DocumentIdentity.newUntitled(), "older")
            .copy(displayName = "Untitled 8")
        store.save(older).getOrThrow()
        val newDraft = DraftWorkspace(store).createUntitled().getOrThrow()
        assertEquals("Untitled 9", newDraft.displayName)
        assertEquals("Untitled 8", store.load(older.id).getOrThrow()?.displayName)
    }

    @Test fun invalidCounterNeverSilentlyResetsOrChangesExistingDraft() {
        val old = draft(DocumentIdentity.newUntitled(), "preserve me")
        store.save(old).getOrThrow()
        assertEquals(1L, store.reserveUntitledNumber().getOrThrow())
        val counter = File(dir, "v2/next-untitled-number.counter")
        counter.writeText("corrupt")
        assertTrue(DraftWorkspace(store).createUntitled().isFailure)
        assertEquals(old, store.load(old.id).getOrThrow())
        assertEquals("corrupt", counter.readText())
    }

    @Test fun unreadableRecoveryBlocksUnsafeCounterInitialization() {
        val broken = File(dir, "v2/new-00000000-0000-0000-0000-000000000000.recovery")
        broken.parentFile!!.mkdirs()
        broken.writeText("damaged")
        assertTrue(store.reserveUntitledNumber().isFailure)
        assertEquals("damaged", broken.readText())
    }

    @Test fun renameKeepsEveryOtherFieldAndSurvivesReopen() {
        val original = RecoveryDraft(
            DocumentIdentity.newUntitled(), null, "Untitled 1", "MARKDOWN",
            "Unsent text 🌏", selectionStart = 2, selectionEnd = 6, scrollY = 87,
            revision = 5, updatedAtMs = 5
        )
        store.save(original).getOrThrow()
        val renamed = store.renameDisplayName(original.id, 5, "  Writing ideas  ").getOrThrow()
        assertEquals("Writing ideas", renamed.displayName)
        assertEquals(6L, renamed.revision)
        assertEquals(original, renamed.copy(
            displayName = original.displayName,
            revision = original.revision,
            updatedAtMs = original.updatedAtMs
        ))
        assertEquals(renamed, RecoveryDraftStore(File(dir, "v2")).load(original.id).getOrThrow())
    }

    @Test fun renameRejectsInvalidAndStaleRequestsWithoutDiscardingBuffer() {
        val original = draft(DocumentIdentity.newUntitled(), "private unsaved text", 2)
        store.save(original).getOrThrow()
        assertTrue(store.renameDisplayName(original.id, 2, "  ").isFailure)
        assertTrue(store.renameDisplayName(original.id, 2, "a".repeat(9000)).isFailure)
        assertEquals(original, store.load(original.id).getOrThrow())
        val newer = original.copy(content = "newer buffer", revision = 3)
        store.save(newer).getOrThrow()
        assertTrue(store.renameDisplayName(original.id, 2, "Stale name").isFailure)
        assertEquals(newer, store.load(original.id).getOrThrow())
        assertTrue(store.renameDisplayName(DocumentIdentity.newUntitled(), 1, "Missing").isFailure)
        assertEquals(newer, store.load(original.id).getOrThrow())
    }

    @Test fun unchangedAndDuplicateCustomNamesDoNotChangeIdentity() {
        val one = draft(DocumentIdentity.newUntitled(), "one")
        val two = draft(DocumentIdentity.newUntitled(), "two")
        store.save(one).getOrThrow()
        store.save(two).getOrThrow()
        val first = store.renameDisplayName(one.id, 1, "My Notes").getOrThrow()
        val second = store.renameDisplayName(two.id, 1, "My Notes").getOrThrow()
        assertNotEquals(first.id, second.id)
        assertEquals("My Notes", first.displayName)
        assertEquals("My Notes", second.displayName)
        assertEquals(first, store.renameDisplayName(one.id, 2, " My Notes ").getOrThrow())
    }

    @Test fun renameCannotTouchDraftAlreadyMarkedTrashed() {
        val trashed = draft(DocumentIdentity.newUntitled(), "safe")
            .copy(trashedAtMs = 123L)
        store.save(trashed).getOrThrow()
        assertTrue(store.renameDisplayName(trashed.id, 1, "Hidden").isFailure)
        assertEquals(trashed, store.load(trashed.id).getOrThrow())
    }

    @Test fun renamingLinkedUnsavedFileKeepsItsUriAndHash() {
        val uri = "content://provider/docs/important.txt"
        val original = RecoveryDraft(
            id = DocumentIdentity.forUri(uri), sourceUri = uri,
            displayName = "important.txt", language = "PLAIN_TEXT",
            content = "Unsaved change", selectionStart = 4, selectionEnd = 7, scrollY = 13,
            revision = 3, sourceEncoding = "UTF8",
            sourceSha256 = "a".repeat(64), savedToDisk = false
        )
        store.save(original).getOrThrow()
        val renamed = store.renameDisplayName(original.id, 3, "Private idea").getOrThrow()
        assertEquals(original.sourceUri, renamed.sourceUri)
        assertEquals(original.id, renamed.id)
        assertEquals(original.sourceSha256, renamed.sourceSha256)
        assertEquals(original.content, renamed.content)
        assertEquals(original.selectionStart, renamed.selectionStart)
        assertEquals(original.selectionEnd, renamed.selectionEnd)
        assertEquals(original.scrollY, renamed.scrollY)
        assertEquals(original.savedToDisk, renamed.savedToDisk)
    }

    @Test fun movingDraftToTrashKeepsTheFullRecoveryRecordAfterRestart() {
        val source = "content://provider/docs/notes.txt"
        val original = RecoveryDraft(
            id = DocumentIdentity.forUri(source), sourceUri = source,
            displayName = "notes.txt", language = "PLAIN_TEXT",
            content = "Unsaved text 🌏\nAnother line",
            selectionStart = 2, selectionEnd = 9, scrollY = 117,
            revision = 6, sourceEncoding = "UTF8", sourceHadBom = true,
            sourceSha256 = "b".repeat(64), savedToDisk = false
        )
        store.save(original).getOrThrow()
        val trashed = store.moveToTrash(original.id, original.revision).getOrThrow()
        assertEquals(original.id, trashed.id)
        assertEquals(original, trashed.copy(
            trashedAtMs = null,
            revision = original.revision,
            updatedAtMs = original.updatedAtMs
        ))
        assertTrue(trashed.trashedAtMs != null)
        val restarted = RecoveryDraftStore(File(dir, "v2"))
        assertEquals(trashed, restarted.load(original.id).getOrThrow())
        assertEquals(listOf(trashed), restarted.list().drafts)
    }

    @Test fun failedAndStaleTrashMovesDoNotHideOrRemoveTheDraft() {
        val original = draft(DocumentIdentity.newUntitled(), "newer text", 4)
        store.save(original).getOrThrow()
        assertTrue(store.moveToTrash(original.id, 3).isFailure)
        assertEquals(original, store.load(original.id).getOrThrow())
        assertTrue(store.moveToTrash(DocumentIdentity.newUntitled(), 1).isFailure)
        assertEquals(original, store.load(original.id).getOrThrow())
        val moved = store.moveToTrash(original.id, 4).getOrThrow()
        assertTrue(store.moveToTrash(original.id, moved.revision).isFailure)
        assertEquals(moved, store.load(original.id).getOrThrow())
    }

    @Test fun savedFileCannotBeMovedIntoUnfinishedDraftTrash() {
        val source = "content://provider/docs/saved.txt"
        val saved = RecoveryDraft(
            id = DocumentIdentity.forUri(source), sourceUri = source,
            displayName = "saved.txt", language = "PLAIN_TEXT",
            content = "saved", sourceEncoding = "UTF8",
            sourceSha256 = "a".repeat(64), savedToDisk = true
        )
        store.save(saved).getOrThrow()
        assertTrue(store.moveToTrash(saved.id, saved.revision).isFailure)
        assertEquals(saved, store.load(saved.id).getOrThrow())
    }

    @Test fun restoresUntitledDraftWithContentCursorScrollAndName() {
        val original = RecoveryDraft(
            id = DocumentIdentity.newUntitled(), sourceUri = null,
            displayName = "Important", language = "MARKDOWN",
            content = "Keep unsaved words 🌏", selectionStart = 3,
            selectionEnd = 8, scrollY = 123, revision = 4
        )
        store.save(original).getOrThrow()
        val trashed = store.moveToTrash(original.id, 4).getOrThrow()
        val result = store.restoreFromTrash(trashed.id, trashed.revision, false).getOrThrow()
        assertFalse(result.recoveredAsCopy)
        assertEquals(original.id, result.draft.id)
        assertEquals(original.content, result.draft.content)
        assertEquals(original.displayName, result.draft.displayName)
        assertEquals(original.selectionStart, result.draft.selectionStart)
        assertEquals(original.selectionEnd, result.draft.selectionEnd)
        assertEquals(original.scrollY, result.draft.scrollY)
        assertNull(result.draft.trashedAtMs)
        assertEquals(result.draft, RecoveryDraftStore(File(dir, "v2"))
            .load(original.id).getOrThrow())
    }

    @Test fun unchangedVerifiedLinkedFileRestoresTheSameDraftIdentity() {
        val uri = "content://provider/document.txt"
        val original = RecoveryDraft(
            id = DocumentIdentity.forUri(uri), sourceUri = uri,
            displayName = "document.txt", language = "PLAIN_TEXT",
            content = "unsaved", sourceEncoding = "UTF8",
            sourceSha256 = "a".repeat(64), savedToDisk = false
        )
        store.save(original).getOrThrow()
        val trashed = store.moveToTrash(original.id, 1).getOrThrow()
        val restored = store.restoreFromTrash(trashed.id, trashed.revision, true).getOrThrow()
        assertFalse(restored.recoveredAsCopy)
        assertEquals(uri, restored.draft.sourceUri)
        assertEquals(original.sourceSha256, restored.draft.sourceSha256)
        assertEquals(original.id, restored.draft.id)
        assertNull(restored.draft.trashedAtMs)
    }

    @Test fun changedLinkedSourceRestoresDetachedCopyAndKeepsOriginalTrash() {
        val uri = "content://provider/document.txt"
        val original = RecoveryDraft(
            id = DocumentIdentity.forUri(uri), sourceUri = uri,
            displayName = "document.txt", language = "PLAIN_TEXT",
            content = "never lose unsaved data", selectionStart = 2,
            selectionEnd = 5, scrollY = 48, revision = 3,
            sourceEncoding = "UTF8", sourceHadBom = true,
            sourceSha256 = "a".repeat(64), savedToDisk = false
        )
        store.save(original).getOrThrow()
        val trashed = store.moveToTrash(original.id, 3).getOrThrow()
        val result = store.restoreFromTrash(trashed.id, trashed.revision, false).getOrThrow()
        assertTrue(result.recoveredAsCopy)
        assertNotEquals(original.id, result.draft.id)
        assertNull(result.draft.sourceUri)
        assertNull(result.draft.sourceSha256)
        assertEquals("Recovered — document.txt", result.draft.displayName)
        assertEquals(original.content, result.draft.content)
        assertEquals(original.selectionStart, result.draft.selectionStart)
        assertEquals(original.selectionEnd, result.draft.selectionEnd)
        assertEquals(original.scrollY, result.draft.scrollY)
        assertEquals(original.sourceEncoding, result.draft.sourceEncoding)
        assertEquals(original.sourceHadBom, result.draft.sourceHadBom)
        assertFalse(result.draft.savedToDisk)
        assertNull(result.draft.trashedAtMs)
        assertEquals(trashed, store.load(original.id).getOrThrow())
        val restarted = RecoveryDraftStore(File(dir, "v2"))
        assertEquals(result.draft, restarted.load(result.draft.id).getOrThrow())
        assertEquals(2, restarted.list().drafts.size)
    }

    @Test fun unverifiedLinkedSourceAlwaysRestoresDetachedEvenIfCallerSaysMatches() {
        val uri = "content://provider/unknown.txt"
        val original = RecoveryDraft(DocumentIdentity.forUri(uri), uri, "unknown.txt",
            "PLAIN_TEXT", "preserve", revision = 3)
        store.save(original).getOrThrow()
        val trashed = store.moveToTrash(original.id, 3).getOrThrow()
        val restored = store.restoreFromTrash(trashed.id, trashed.revision, true).getOrThrow()
        assertTrue(restored.recoveredAsCopy)
        assertNull(restored.draft.sourceUri)
        assertEquals(trashed, store.load(original.id).getOrThrow())
    }

    @Test fun staleRestoreAndNonTrashDeletionFailWithoutChangingRecords() {
        val original = draft(DocumentIdentity.newUntitled(), "important", 2)
        store.save(original).getOrThrow()
        assertTrue(store.restoreFromTrash(original.id, 2, false).isFailure)
        assertTrue(store.deleteTrashedPermanently(original.id, 2).isFailure)
        assertEquals(original, store.load(original.id).getOrThrow())
        val trashed = store.moveToTrash(original.id, 2).getOrThrow()
        assertTrue(store.restoreFromTrash(original.id, 2, false).isFailure)
        assertTrue(store.deleteTrashedPermanently(original.id, 2).isFailure)
        assertTrue(store.restoreFromTrash(DocumentIdentity.newUntitled(), 1, false).isFailure)
        assertEquals(trashed, store.load(original.id).getOrThrow())
    }

    @Test fun confirmedDeletionCanOnlyRemoveTrashedRecoveryRecord() {
        val one = draft(DocumentIdentity.newUntitled(), "one")
        val two = draft(DocumentIdentity.newUntitled(), "two")
        store.save(one).getOrThrow()
        store.save(two).getOrThrow()
        val trashed = store.moveToTrash(one.id, one.revision).getOrThrow()
        assertFalse(store.clearSavedRevision(one.id, trashed.revision).getOrThrow())
        store.deleteTrashedPermanently(trashed.id, trashed.revision).getOrThrow()
        assertNull(store.load(one.id).getOrThrow())
        assertEquals(two, store.load(two.id).getOrThrow())
        assertTrue(store.deleteTrashedPermanently(trashed.id, trashed.revision).isFailure)
    }

    @Test fun multipleIndependentDraftsMoveAndDeleteWithoutTouchingUnselected() {
        val drafts = (1..3).map {
            draft(DocumentIdentity.newUntitled(), "text $it")
        }
        drafts.forEach { store.save(it).getOrThrow() }
        val moved = drafts.take(2).map { store.moveToTrash(it.id, it.revision).getOrThrow() }
        assertEquals(2, store.list().drafts.count { it.trashedAtMs != null })
        assertEquals(drafts[2], store.load(drafts[2].id).getOrThrow())
        moved.forEach { store.deleteTrashedPermanently(it.id, it.revision).getOrThrow() }
        assertEquals(listOf(drafts[2]), store.list().drafts)
    }

    @Test fun sameExistingFileGetsSameIdentity() {
        val uri = "content://com.android.externalstorage.documents/document/primary%3ANotes%2Fhello.txt"
        val id = DocumentIdentity.forUri(uri)
        assertEquals(id, DocumentIdentity.forUri(uri))
        assertNotEquals(id, DocumentIdentity.newUntitled())
        val saved = RecoveryDraft(id, uri, "hello.txt", "PLAIN_TEXT", "สวัสดีครับ")
        assertTrue(store.save(saved).isSuccess)
        assertEquals("สวัสดีครับ", store.load(id).getOrThrow()?.content)
    }

    @Test fun olderAsyncDraftCannotOverwriteNewerRevision() {
        val id = DocumentIdentity.newUntitled()
        assertTrue(store.save(draft(id, "newer", 5)).isSuccess)
        assertTrue(store.save(draft(id, "older", 4)).isFailure)
        assertEquals("newer", store.load(id).getOrThrow()?.content)
    }

    @Test fun equalRevisionsWithDifferentTextAreRejected() {
        val id = DocumentIdentity.newUntitled()
        assertTrue(store.save(draft(id, "A", 2)).isSuccess)
        assertTrue(store.save(draft(id, "B", 2)).isFailure)
        assertEquals("A", store.load(id).getOrThrow()?.content)
    }

    @Test fun saveRevisionGuardDoesNotDeleteNewerDraft() {
        val id = DocumentIdentity.newUntitled()
        assertTrue(store.save(draft(id, "old", 3)).isSuccess)
        assertTrue(store.save(draft(id, "unsaved newer", 4)).isSuccess)
        assertFalse(store.clearSavedRevision(id, 3).getOrThrow())
        assertEquals("unsaved newer", store.load(id).getOrThrow()?.content)
        assertTrue(store.clearSavedRevision(id, 4).getOrThrow())
        assertNull(store.load(id).getOrThrow())
    }

    @Test fun contentAndCaretSurviveFreshRepositoryInstance() {
        val id = DocumentIdentity.newUntitled()
        val original = RecoveryDraft(
            id, null, "My Notes", "MARKDOWN", "สวัสดีครับ 👩‍💻\r\nNext",
            selectionStart = 5, selectionEnd = 7, scrollY = 80, revision = 7
        )
        assertTrue(store.save(original).isSuccess)
        val reopened = RecoveryDraftStore(File(dir, "v2")).load(id).getOrThrow()
        assertEquals(original, reopened)
    }

    @Test fun corruptedRecoveryIsReportedAndNeverDiscarded() {
        val id = DocumentIdentity.newUntitled()
        assertTrue(store.save(draft(id, "good")).isSuccess)
        val bad = File(dir, "v2/new-00000000-0000-0000-0000-000000000000.recovery")
        bad.writeText("corrupt")
        val listing = store.list()
        assertEquals(1, listing.drafts.size)
        assertEquals(1, listing.unreadableFiles.size)
        assertEquals("corrupt", bad.readText())
    }

    @Test fun importsLegacyUntitledWithoutRemovingItsFilesAndIsIdempotent() {
        val old = File(dir, "drafts")
        old.mkdirs()
        val meta = File(old, "untitled.meta")
        val content = File(old, "untitled.draft")
        content.writeText("old draft text")
        val props = Properties().apply {
            setProperty("uri", "")
            setProperty("displayName", "untitled.txt")
            setProperty("syntaxMode", "PLAIN_TEXT")
            setProperty("cursorLine", "1")
            setProperty("cursorColumn", "4")
            setProperty("scrollPosition", "23")
        }
        meta.outputStream().use { props.store(it, "legacy") }
        val first = store.importLegacy(old)
        assertTrue(first.complete)
        assertEquals(1, first.imported)
        assertEquals("old draft text", store.list().drafts.first().content)
        assertEquals(3, store.list().drafts.first().selectionStart)
        assertTrue(content.exists())
        assertTrue(meta.exists())
        val second = store.importLegacy(old)
        assertEquals(0, second.imported)
        assertEquals(1, store.list().drafts.size)
    }

    @Test fun failedImportLeavesOriginalAndCanBeRetried() {
        val old = File(dir, "drafts")
        old.mkdirs()
        File(old, "untitled.meta").writeText("displayName=Untitled")
        val failed = store.importLegacy(old)
        assertFalse(failed.complete)
        assertEquals(1, failed.failedFiles.size)
        val original = File(old, "untitled.draft")
        original.writeText("restored")
        assertTrue(store.importLegacy(old).complete)
        assertEquals("restored", store.list().drafts.single().content)
    }
}
