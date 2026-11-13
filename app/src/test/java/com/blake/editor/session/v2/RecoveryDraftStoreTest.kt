package com.blake.editor.session.v2

import java.io.File
import java.nio.file.Files
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.blake.editor.session.FileDraftRepository
import com.blake.editor.core.model.EditorSession
import com.blake.editor.core.model.SyntaxMode
import kotlinx.coroutines.runBlocking
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


    private fun runTogether(first: () -> Unit, second: () -> Unit) {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val tasks = listOf(first, second).map { action ->
                executor.submit {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    action()
                }
            }
            assertTrue("Both writers should be ready", ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            tasks.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun concurrentStoresNeverReplaceNewerRecoveryWithStaleText() {
        val secondStore = RecoveryDraftStore(File(dir, "v2"))
        repeat(20) { iteration ->
            val id = DocumentIdentity.newUntitled()
            store.save(draft(id, "baseline", 1)).getOrThrow()
            var newerSaved = false
            runTogether(
                { store.save(draft(id, "older-$iteration", 2)) },
                { newerSaved = secondStore.save(draft(id, "newer-$iteration", 3)).isSuccess }
            )
            assertTrue("Newer revision must save", newerSaved)
            val persisted = store.load(id).getOrThrow()
            assertEquals(3L, persisted?.revision)
            assertEquals("newer-$iteration", persisted?.content)
        }
    }

    @Test fun concurrentSavedRevisionCleanupCannotDeleteNewerRecovery() {
        val secondStore = RecoveryDraftStore(File(dir, "v2"))
        repeat(20) { iteration ->
            val id = DocumentIdentity.newUntitled()
            store.save(draft(id, "old", 1)).getOrThrow()
            var newerSaved = false
            runTogether(
                { store.clearSavedRevision(id, 1).getOrThrow() },
                { newerSaved = secondStore.save(draft(id, "unsaved-$iteration", 2)).isSuccess }
            )
            assertTrue("Newer recovery must save", newerSaved)
            val persisted = store.load(id).getOrThrow()
            assertEquals(2L, persisted?.revision)
            assertEquals("unsaved-$iteration", persisted?.content)
        }
    }

    @Test fun concurrentRecentHidesKeepBothShortcutIds() {
        val secondStore = RecoveryDraftStore(File(dir, "v2"))
        repeat(12) { iteration ->
            val firstUri = "content://example/recent/first-$iteration.txt"
            val secondUri = "content://example/recent/second-$iteration.txt"
            val firstId = DocumentIdentity.forUri(firstUri)
            val secondId = DocumentIdentity.forUri(secondUri)
            store.save(RecoveryDraft(firstId, firstUri, "first.txt", "PLAIN_TEXT",
                "one", sourceEncoding = "UTF8", sourceSha256 = "a".repeat(64),
                savedToDisk = true)).getOrThrow()
            store.save(RecoveryDraft(secondId, secondUri, "second.txt", "PLAIN_TEXT",
                "two", sourceEncoding = "UTF8", sourceSha256 = "b".repeat(64),
                savedToDisk = true)).getOrThrow()
            runTogether(
                { store.hideFromRecent(firstId, 1).getOrThrow() },
                { secondStore.hideFromRecent(secondId, 1).getOrThrow() }
            )
            val hidden = store.list().hiddenRecentIds
            assertTrue(hidden.contains(firstId))
            assertTrue(hidden.contains(secondId))
        }
    }

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

    @Test fun corruptOptionalRecentIndexDoesNotBlockSafeFirstCounterReservation() {
        val existing = draft(DocumentIdentity.newUntitled(), "keep this text")
            .copy(displayName = "Untitled 8")
        store.save(existing).getOrThrow()
        val index = File(dir, "v2/recent-shortcuts-hidden.index")
        index.writeText("damaged sidecar")
        assertEquals(9L, store.reserveUntitledNumber().getOrThrow())
        assertEquals(10L, store.reserveUntitledNumber().getOrThrow())
        assertEquals(existing, store.load(existing.id).getOrThrow())
        assertEquals("damaged sidecar", index.readText())
        assertTrue(store.list().unreadableFiles.contains(index.name))
    }

    @Test fun corruptRecoveryStillBlocksNumberingWhenRecentIndexAlsoCorrupt() {
        val index = File(dir, "v2/recent-shortcuts-hidden.index")
        index.parentFile!!.mkdirs()
        index.writeText("damaged sidecar")
        val recovery = File(dir, "v2/new-00000000-0000-0000-0000-000000000000.recovery")
        recovery.writeText("damaged recovery")
        assertTrue(store.reserveUntitledNumber().isFailure)
        assertEquals("damaged recovery", recovery.readText())
        assertEquals("damaged sidecar", index.readText())
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

    @Test fun recentRemovalHidesShortcutButNeverChangesRecoveryOrRealFile() {
        val uri = "content://example/recent/report.txt"
        val saved = RecoveryDraft(
            id = DocumentIdentity.forUri(uri), sourceUri = uri,
            displayName = "report.txt", language = "PLAIN_TEXT",
            content = "original content", selectionStart = 3,
            selectionEnd = 7, scrollY = 37, revision = 4,
            sourceEncoding = "UTF8", sourceSha256 = "a".repeat(64),
            savedToDisk = true
        )
        store.save(saved).getOrThrow()
        store.hideFromRecent(saved.id, saved.revision).getOrThrow()
        assertEquals(saved, store.load(saved.id).getOrThrow())
        val reopened = RecoveryDraftStore(File(dir, "v2"))
        val listing = reopened.list()
        assertEquals(listOf(saved), listing.drafts)
        assertTrue(listing.hiddenRecentIds.contains(saved.id))
        assertTrue(V2HomeSections.from(listing).recentlySaved.isEmpty())
        reopened.showInRecent(saved.id).getOrThrow()
        assertEquals(listOf(saved), V2HomeSections.from(reopened.list()).recentlySaved)
    }

    @Test fun recentRemovalRejectsStaleMissingUnsavedAndTrashWithoutChanges() {
        val uri = "content://example/recent/saved.txt"
        val saved = RecoveryDraft(
            id = DocumentIdentity.forUri(uri), sourceUri = uri,
            displayName = "saved.txt", language = "PLAIN_TEXT",
            content = "safe", sourceEncoding = "UTF8",
            sourceSha256 = "a".repeat(64), savedToDisk = true,
            revision = 6
        )
        val unsaved = draft(DocumentIdentity.newUntitled(), "not saved", 2)
        store.save(saved).getOrThrow()
        store.save(unsaved).getOrThrow()
        assertTrue(store.hideFromRecent(saved.id, 5).isFailure)
        assertTrue(store.hideFromRecent(unsaved.id, 2).isFailure)
        assertTrue(store.hideFromRecent(DocumentIdentity.newUntitled(), 1).isFailure)
        assertEquals(saved, store.load(saved.id).getOrThrow())
        assertEquals(unsaved, store.load(unsaved.id).getOrThrow())
        assertTrue(store.list().hiddenRecentIds.isEmpty())
        val trashed = store.moveToTrash(unsaved.id, 2).getOrThrow()
        assertTrue(store.hideFromRecent(trashed.id, trashed.revision).isFailure)
        assertEquals(trashed, store.load(trashed.id).getOrThrow())
    }

    @Test fun corruptRecentIndexIsNeverOverwrittenAndRecoveryStillLoads() {
        val uri = "content://example/recent/keep.txt"
        val saved = RecoveryDraft(
            id = DocumentIdentity.forUri(uri), sourceUri = uri,
            displayName = "keep.txt", language = "PLAIN_TEXT",
            content = "never delete", sourceEncoding = "UTF8",
            sourceSha256 = "b".repeat(64), savedToDisk = true
        )
        store.save(saved).getOrThrow()
        val index = File(dir, "v2/recent-shortcuts-hidden.index")
        index.writeText("damaged sidecar")
        assertTrue(store.hideFromRecent(saved.id, 1).isFailure)
        assertTrue(store.showInRecent(saved.id).isFailure)
        val listing = store.list()
        assertTrue(listing.unreadableFiles.contains("recent-shortcuts-hidden.index"))
        assertEquals(saved, listing.drafts.single())
        assertEquals("damaged sidecar", index.readText())
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

    private fun writeOriginalPair(
        directory: File,
        slot: String?,
        text: String,
        name: String = "Original.txt",
        cursorColumn: Int = 2
    ) {
        directory.mkdirs()
        val suffix = if (slot == null) "" else ".${slot}"
        File(directory, "untitled.draft${suffix}").writeText(text, Charsets.UTF_8)
        val props = Properties().apply {
            setProperty("uri", "")
            setProperty("displayName", name)
            setProperty("syntaxMode", "PLAIN_TEXT")
            setProperty("cursorLine", "1")
            setProperty("cursorColumn", cursorColumn.toString())
        }
        File(directory, "untitled.meta${suffix}").outputStream().use {
            props.store(it, "Original draft")
        }
    }

    @Test fun importsActualCommittedOriginalSlotWithoutUsingStaleLegacyPair() = runBlocking {
        val old = File(dir, "drafts")
        writeOriginalPair(old, null, "stale legacy", "stale.txt")
        val initialMeta = File(old, "untitled.meta").readBytes()
        val original = FileDraftRepository(baseDir = old)
        val session = EditorSession(null, "live.txt", SyntaxMode.PLAIN_TEXT, true,
            cursorLine = 1, cursorColumn = 6)
        assertTrue(original.saveDraft(session, "first committed").isSuccess)
        assertTrue(original.saveDraft(session.copy(displayName = "latest.txt", cursorColumn = 9),
            "latest committed").isSuccess)
        assertEquals("1", File(old, "untitled.active").readText())

        val report = store.importLegacy(old)
        assertTrue(report.complete)
        assertEquals(1, report.imported)
        val restored = store.list().drafts.single()
        assertEquals("latest committed", restored.content)
        assertEquals("latest.txt", restored.displayName)
        assertEquals(8, restored.selectionStart)
        assertEquals("stale legacy", File(old, "untitled.draft").readText())
        assertTrue(initialMeta.contentEquals(File(old, "untitled.meta").readBytes()))
    }

    @Test fun committedSlotZeroImportsWhenNoLegacyPairExists() {
        val old = File(dir, "drafts")
        writeOriginalPair(old, "0", "only committed", "slot-zero.txt")
        File(old, "untitled.active").writeText("0")

        val result = store.importLegacy(old)
        assertTrue(result.complete)
        assertEquals(1, result.imported)
        assertEquals("only committed", store.list().drafts.single().content)
        assertEquals("slot-zero.txt", store.list().drafts.single().displayName)
    }

    @Test fun deletedOriginalTombstoneSkipsAllStalePairs() {
        val old = File(dir, "drafts")
        writeOriginalPair(old, null, "old legacy")
        writeOriginalPair(old, "0", "old committed")
        File(old, "untitled.active").writeText("deleted")

        val result = store.importLegacy(old)
        assertTrue(result.complete)
        assertEquals(0, result.imported)
        assertTrue(store.list().drafts.isEmpty())
        assertEquals("deleted", File(old, "untitled.active").readText())
        assertEquals("old legacy", File(old, "untitled.draft").readText())
    }

    @Test fun corruptMarkerBlocksLegacyFallbackAndAllowsRetryAfterRepair() {
        val old = File(dir, "drafts")
        writeOriginalPair(old, null, "stale legacy")
        writeOriginalPair(old, "1", "new real content")
        val marker = File(old, "untitled.active")
        marker.writeText("corrupted")

        val failed = store.importLegacy(old)
        assertFalse(failed.complete)
        assertEquals(listOf("untitled"), failed.failedFiles)
        assertTrue(store.list().drafts.isEmpty())
        assertFalse(File(dir, "v2/legacy-imported.marker").exists())
        assertEquals("stale legacy", File(old, "untitled.draft").readText())

        marker.writeText("1")
        val retried = store.importLegacy(old)
        assertTrue(retried.complete)
        assertEquals("new real content", store.list().drafts.single().content)
    }

    @Test fun incompleteCommittedPairNeverImportsOlderLegacyAndCanRetry() {
        val old = File(dir, "drafts")
        writeOriginalPair(old, null, "stale legacy")
        File(old, "untitled.draft.0").writeText("new partial")
        File(old, "untitled.active").writeText("0")
        val first = store.importLegacy(old)
        assertFalse(first.complete)
        assertTrue(store.list().drafts.isEmpty())
        assertFalse(File(dir, "v2/legacy-imported.marker").exists())

        writeOriginalPair(old, "0", "completed current")
        val retried = store.importLegacy(old)
        assertTrue(retried.complete)
        assertEquals("completed current", store.list().drafts.single().content)
    }

    @Test fun unfinishedUncommittedSlotWithoutLegacyStaysRetryable() {
        val old = File(dir, "drafts")
        old.mkdirs()
        File(old, "untitled.draft.0").writeText("unpublished")
        val first = store.importLegacy(old)
        assertFalse(first.complete)
        assertEquals(listOf("untitled"), first.failedFiles)
        assertFalse(File(dir, "v2/legacy-imported.marker").exists())

        writeOriginalPair(old, "0", "committed")
        File(old, "untitled.active").writeText("0")
        assertTrue(store.importLegacy(old).complete)
        assertEquals("committed", store.list().drafts.single().content)
    }

    @Test fun existingV2RecoveryNeverOverwrittenByOriginalSlotImport() {
        val old = File(dir, "drafts")
        writeOriginalPair(old, "1", "Original content")
        File(old, "untitled.active").writeText("1")
        val id = DocumentIdentity.legacyUntitled()
        val existing = RecoveryDraft(id, null, "V2 edit", "PLAIN_TEXT",
            "keep V2 content", revision = 7)
        store.save(existing).getOrThrow()

        val result = store.importLegacy(old)
        assertTrue(result.complete)
        assertEquals(0, result.imported)
        assertEquals(1, result.alreadyPresent)
        assertEquals(existing, store.load(id).getOrThrow())
        assertEquals("Original content", File(old, "untitled.draft.1").readText())
    }

    @Test fun committedLinkedDocumentKeepsUriAndDoesNotTouchExternalBytes() {
        val old = File(dir, "drafts").apply { mkdirs() }
        val uri = "content://example.provider/document/42"
        val key = DocumentIdentity.forUri(uri).value.removePrefix("uri-")
        val contentFile = File(old, "${key}.draft.1")
        contentFile.writeText("linked unsaved changes")
        val props = Properties().apply {
            setProperty("uri", uri)
            setProperty("displayName", "linked.txt")
            setProperty("syntaxMode", "KOTLIN")
            setProperty("cursorLine", "1")
            setProperty("cursorColumn", "5")
        }
        File(old, "${key}.meta.1").outputStream().use { props.store(it, "Original") }
        File(old, "${key}.active").writeText("1")

        val result = store.importLegacy(old)
        assertTrue(result.complete)
        val imported = store.load(DocumentIdentity.forUri(uri)).getOrThrow()!!
        assertEquals(uri, imported.sourceUri)
        assertEquals("linked unsaved changes", imported.content)
        assertEquals("linked.txt", imported.displayName)
        assertEquals(4, imported.selectionStart)
        assertEquals("linked unsaved changes", contentFile.readText())
    }

    /**
     * Existing migration markers can predate the committed Original slot format.
     * Restart must not silently replace V2 edits with a newer Original snapshot.
     * A future opt-in reconciliation must use a different, user-confirmed path.
     */
    @Test fun historicalImportMarkerPreservesV2EditsWhenOriginalSlotChanges() {
        val old = File(dir, "drafts")
        writeOriginalPair(old, null, "historical Original snapshot")
        assertTrue(store.importLegacy(old).complete)

        val marker = File(dir, "v2/legacy-imported.marker")
        val markerBytes = marker.readBytes()
        val id = DocumentIdentity.legacyUntitled()
        val originalV2 = store.load(id).getOrThrow()!!
        val editedV2 = originalV2.copy(content = "independent V2 edits",
            revision = originalV2.revision + 1)
        store.save(editedV2).getOrThrow()

        writeOriginalPair(old, "0", "newer Original committed text")
        val source = File(old, "untitled.draft.0")
        val sourceBytes = source.readBytes()
        File(old, "untitled.active").writeText("0")

        val reopened = RecoveryDraftStore(File(dir, "v2"))
        val report = reopened.importLegacy(old)
        assertTrue(report.complete)
        assertEquals(0, report.imported)
        assertEquals(0, report.alreadyPresent)
        assertTrue(report.failedFiles.isEmpty())
        assertEquals(editedV2, reopened.load(id).getOrThrow())
        assertEquals(1, reopened.list().drafts.size)
        assertTrue(markerBytes.contentEquals(marker.readBytes()))
        assertTrue(sourceBytes.contentEquals(source.readBytes()))
    }

    @Test fun historicalImportMarkerNeverSilentlyImportsNewOriginalSlot() {
        val old = File(dir, "drafts").apply { mkdirs() }
        // Simulate a completed earlier import that found no Original draft.
        assertTrue(store.importLegacy(old).complete)
        val marker = File(dir, "v2/legacy-imported.marker")
        val markerBytes = marker.readBytes()

        writeOriginalPair(old, "1", "Original appeared after V2 migration")
        val source = File(old, "untitled.draft.1")
        File(old, "untitled.active").writeText("1")

        val report = RecoveryDraftStore(File(dir, "v2")).importLegacy(old)
        assertTrue(report.complete)
        assertEquals(0, report.imported)
        assertTrue(report.failedFiles.isEmpty())
        assertTrue(store.list().drafts.isEmpty())
        assertTrue(markerBytes.contentEquals(marker.readBytes()))
        assertEquals("Original appeared after V2 migration", source.readText())
        assertEquals("1", File(old, "untitled.active").readText())
    }

    @Test fun historicalImportMarkerPreservesV2TrashAfterOriginalTombstone() {
        val old = File(dir, "drafts")
        writeOriginalPair(old, null, "old Original recovery")
        assertTrue(store.importLegacy(old).complete)
        val id = DocumentIdentity.legacyUntitled()
        val existing = store.load(id).getOrThrow()!!
        val trashed = store.moveToTrash(id, existing.revision).getOrThrow()
        val marker = File(dir, "v2/legacy-imported.marker")
        val markerBytes = marker.readBytes()

        // Original now considers the draft deleted, but the V2 Trash is distinct.
        File(old, "untitled.active").writeText("deleted")
        val report = RecoveryDraftStore(File(dir, "v2")).importLegacy(old)
        assertTrue(report.complete)
        assertEquals(0, report.imported)
        assertEquals(trashed, store.load(id).getOrThrow())
        assertEquals(listOf(trashed), store.list().drafts)
        assertTrue(markerBytes.contentEquals(marker.readBytes()))
        assertEquals("deleted", File(old, "untitled.active").readText())
        assertEquals("old Original recovery", File(old, "untitled.draft").readText())
    }

    @Test fun unreadableOriginalDirectoryCannotMarkMigrationComplete() {
        val invalid = File(dir, "drafts")
        invalid.writeText("not a directory")
        val result = store.importLegacy(invalid)
        assertFalse(result.complete)
        assertTrue(result.failedFiles.isNotEmpty())
        assertFalse(File(dir, "v2/legacy-imported.marker").exists())
    }
}
