package com.blake.editor.session.v2

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DraftWorkspaceTest {
    private lateinit var workspace: DraftWorkspace
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("blake-v2-workspace").toFile()
        workspace = DraftWorkspace(RecoveryDraftStore(directory))
    }

    @Test fun newDocumentsGetSeparateRecoveryRecords() {
        val first = workspace.createUntitled().getOrThrow()
        val second = workspace.createUntitled().getOrThrow()
        assertNotEquals(first.id, second.id)
        assertEquals("Untitled 1", first.displayName)
        assertEquals("Untitled 2", second.displayName)

        val changed = workspace.edit(first, "first text", 10, 10, 0)
        assertTrue(workspace.returnHome(changed).isSuccess)
        assertEquals("first text", workspace.load(first.id).getOrThrow()?.content)
        assertEquals("", workspace.load(second.id).getOrThrow()?.content)
    }

    @Test fun reservedNumbersSurviveRenameDeletionAndNewStoreInstance() {
        val first = workspace.createUntitled().getOrThrow()
        val edited = workspace.edit(first, "private text", 7, 7, 14)
        val renamed = edited.copy(displayName = "My Notes", revision = edited.revision + 1)
        workspace.persist(renamed).getOrThrow()
        assertEquals(first.id, workspace.load(first.id).getOrThrow()?.id)
        assertEquals("private text", workspace.load(first.id).getOrThrow()?.content)

        val second = workspace.createUntitled().getOrThrow()
        assertEquals("Untitled 2", second.displayName)

        // Simulate a later permanent deletion only inside this temporary test directory.
        assertTrue(File(directory, first.id.value + ".recovery").delete())
        val reopenedWorkspace = DraftWorkspace(RecoveryDraftStore(directory))
        val third = reopenedWorkspace.createUntitled().getOrThrow()
        assertEquals("Untitled 3", third.displayName)
        assertNotEquals(first.id, third.id)
        assertEquals("Untitled 2", reopenedWorkspace.load(second.id).getOrThrow()?.displayName)
    }

    @Test fun renameThroughWorkspaceDoesNotConsumeTheNextUntitledNumber() {
        val first = workspace.createUntitled().getOrThrow()
        val edited = workspace.edit(first, "Draft text", 4, 4, 25)
        workspace.persist(edited).getOrThrow()
        val renamed = workspace.renameDraft(first.id, edited.revision, "Planning").getOrThrow()
        assertEquals(first.id, renamed.id)
        assertEquals("Draft text", workspace.load(renamed.id).getOrThrow()?.content)
        assertEquals("Untitled 2", workspace.createUntitled().getOrThrow().displayName)
    }

    @Test fun trashedLinkedDraftIsNotSilentlyReopenedOrSwitchedTo() {
        val source = "content://provider/docs/private.txt"
        val linked = workspace.openFromFile(
            source, "private.txt", "PLAIN_TEXT", "original", "UTF8", false,
            "a".repeat(64)
        ).getOrThrow()
        val changed = workspace.edit(linked, "unsaved changes", 6, 9, 33)
        workspace.persist(changed).getOrThrow()
        val moved = workspace.moveToTrash(linked.id, changed.revision).getOrThrow()
        assertEquals("unsaved changes", moved.content)
        assertTrue(workspace.openFromFile(source, "private.txt", "PLAIN_TEXT",
            "new external content", "UTF8", false, "b".repeat(64)).isFailure)
        val other = workspace.createUntitled().getOrThrow()
        assertTrue(workspace.switchTo(other, linked.id).isFailure)
        assertEquals(moved, workspace.load(linked.id).getOrThrow())
        assertEquals(moved, V2HomeSections.from(workspace.list()).trash.single())
    }

    @Test fun workspaceRestoreAndExplicitDeletionStayScopedToRecovery() {
        val first = workspace.createUntitled().getOrThrow()
        val second = workspace.createUntitled().getOrThrow()
        val original = workspace.edit(first, "keep this", 2, 5, 19)
        workspace.persist(original).getOrThrow()
        val moved = workspace.moveToTrash(first.id, original.revision).getOrThrow()
        val restored = workspace.restoreFromTrash(moved.id, moved.revision, false).getOrThrow()
        assertFalse(restored.recoveredAsCopy)
        assertEquals("keep this", workspace.load(first.id).getOrThrow()?.content)
        val movedAgain = workspace.moveToTrash(first.id, restored.draft.revision).getOrThrow()
        workspace.deleteTrashedPermanently(movedAgain.id, movedAgain.revision).getOrThrow()
        assertNull(workspace.load(first.id).getOrThrow())
        assertEquals(second, workspace.load(second.id).getOrThrow())
    }

    @Test fun explicitlyReopeningHiddenSavedFileRestoresItsRecentShortcut() {
        val uri = "content://example.provider/doc/recent.txt"
        val saved = workspace.openFromFile(uri, "recent.txt", "PLAIN_TEXT",
            "original", encoding = "UTF8", sourceSha256 = "c".repeat(64)).getOrThrow()
        assertTrue(saved.savedToDisk)
        workspace.removeFromRecent(saved.id, saved.revision).getOrThrow()
        assertTrue(V2HomeSections.from(workspace.list()).recentlySaved.isEmpty())
        val reopened = DraftWorkspace(RecoveryDraftStore(directory))
        assertEquals(saved, reopened.openFromFile(uri, "recent.txt", "PLAIN_TEXT",
            "original", encoding = "UTF8", sourceSha256 = "c".repeat(64)).getOrThrow())
        assertEquals(listOf(saved), V2HomeSections.from(reopened.list()).recentlySaved)
    }

    @Test fun switchingPersistsOutgoingTextAndCursorBeforeLoading() {
        val first = workspace.createUntitled().getOrThrow()
        val second = workspace.createUntitled().getOrThrow()
        val changed = workspace.edit(first, "สวัสดีครับ 🌏", 4, 4, 90)
        val switched = workspace.switchTo(changed, second.id).getOrThrow()
        assertEquals(second.id, switched.id)
        val restored = workspace.load(first.id).getOrThrow()!!
        assertEquals("สวัสดีครับ 🌏", restored.content)
        assertEquals(4, restored.selectionStart)
        assertEquals(90, restored.scrollY)
    }

    @Test fun switchIsBlockedIfOutgoingSnapshotIsStale() {
        val first = workspace.createUntitled().getOrThrow()
        val second = workspace.createUntitled().getOrThrow()
        val newer = workspace.edit(first, "newer", 5, 5, 0)
        assertTrue(workspace.persist(newer).isSuccess)
        assertTrue(workspace.switchTo(first, second.id).isFailure)
        assertEquals("newer", workspace.load(first.id).getOrThrow()?.content)
    }

    @Test fun reopeningSameFileResumesItsDraftInsteadOfDiscardingEdits() {
        val uri = "content://example.provider/doc/notes.txt"
        val opened = workspace.openFromFile(uri, "notes.txt", "PLAIN_TEXT", "original").getOrThrow()
        val modified = workspace.edit(opened, "unsaved change", 13, 13, 0)
        workspace.persist(modified).getOrThrow()
        val reopened = workspace.openFromFile(uri, "notes.txt", "PLAIN_TEXT", "external version")
            .getOrThrow()
        assertEquals("unsaved change", reopened.content)
        assertEquals(modified.revision, reopened.revision)
    }

    @Test fun simpleCursorMoveProducesNewSnapshotAndIdleStateDoesNot() {
        val current = workspace.createUntitled().getOrThrow()
        assertEquals(current, workspace.edit(current, "", 0, 0, 0))
        val changed = workspace.edit(current, "x", 1, 1, 0)
        assertEquals(current.revision + 1, changed.revision)
        assertEquals(changed, workspace.edit(changed, "x", 1, 1, 0))
    }

    @Test fun emptyOrMissingTargetIsNeverInvented() {
        val first = workspace.createUntitled().getOrThrow()
        val missing = DocumentIdentity.newUntitled()
        assertTrue(workspace.switchTo(first, missing).isFailure)
        assertNotNull(workspace.load(first.id).getOrThrow())
    }
}
