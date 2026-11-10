package com.blake.editor.session.v2

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DraftWorkspaceTest {
    private lateinit var workspace: DraftWorkspace

    @Before
    fun setUp() {
        val dir = Files.createTempDirectory("blake-v2-workspace").toFile()
        workspace = DraftWorkspace(RecoveryDraftStore(dir))
    }

    @Test fun newDocumentsGetSeparateRecoveryRecords() {
        val first = workspace.createUntitled().getOrThrow()
        val second = workspace.createUntitled().getOrThrow()
        assertNotEquals(first.id, second.id)

        val changed = workspace.edit(first, "first text", 10, 10, 0)
        assertTrue(workspace.returnHome(changed).isSuccess)
        assertEquals("first text", workspace.load(first.id).getOrThrow()?.content)
        assertEquals("", workspace.load(second.id).getOrThrow()?.content)
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
