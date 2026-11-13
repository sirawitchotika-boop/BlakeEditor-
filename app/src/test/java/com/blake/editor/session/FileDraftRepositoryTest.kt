package com.blake.editor.session

import com.blake.editor.core.model.EditorSession
import com.blake.editor.core.model.SyntaxMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileDraftRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var repository: FileDraftRepository

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("blake_draft_test").toFile()
        repository = FileDraftRepository(baseDir = tempDir)
    }

    @Test
    fun testSaveDraftAndLoadDraftForUntitled() = runBlocking {
        val session = EditorSession(
            uri = null,
            displayName = "untitled.txt",
            syntaxMode = SyntaxMode.KOTLIN,
            isDirty = true,
            cursorLine = 12,
            cursorColumn = 5,
            scrollPosition = 200
        )
        val content = "Sample draft recovery test content"

        val saveResult = repository.saveDraft(session, content)
        assertTrue(saveResult.isSuccess)
        assertTrue(repository.hasDraft(null))

        val loadResult = repository.loadDraft(null)
        assertTrue(loadResult.isSuccess)

        val pair = loadResult.getOrNull()
        assertNotNull(pair)
        assertEquals("untitled.txt", pair!!.first.displayName)
        assertEquals(SyntaxMode.KOTLIN, pair.first.syntaxMode)
        assertTrue(pair.first.isDirty)
        assertEquals(12, pair.first.cursorLine)
        assertEquals(5, pair.first.cursorColumn)
        assertEquals(200, pair.first.scrollPosition)
        assertEquals(content, pair.second)
    }

    @Test
    fun testClearDraftRemovesFiles() = runBlocking {
        val session = EditorSession(
            uri = null,
            displayName = "scratch.txt",
            syntaxMode = SyntaxMode.PLAIN_TEXT,
            isDirty = true
        )
        repository.saveDraft(session, "Temporary draft content")
        assertTrue(repository.hasDraft(null))

        val clearResult = repository.clearDraft(null)
        assertTrue(clearResult.isSuccess)
        assertFalse(repository.hasDraft(null))

        val loadResult = repository.loadDraft(null)
        assertTrue(loadResult.isSuccess)
        assertNull(loadResult.getOrNull())
    }

    @Test
    fun testLoadNonExistentDraftReturnsNull() = runBlocking {
        assertFalse(repository.hasDraft(null))
        val loadResult = repository.loadDraft(null)
        assertTrue(loadResult.isSuccess)
        assertNull(loadResult.getOrNull())
    }

    @Test
    fun legacyPairIsReadableWithoutMigrationAndNotOverwritten() = runBlocking {
        val legacyContent = File(tempDir, "untitled.draft")
        val legacyMeta = File(tempDir, "untitled.meta")
        legacyContent.writeText("legacy saved draft", Charsets.UTF_8)
        val legacyProps = java.util.Properties().apply {
            setProperty("uri", "")
            setProperty("displayName", "old.txt")
            setProperty("syntaxMode", SyntaxMode.KOTLIN.name)
            setProperty("isDirty", "true")
            setProperty("cursorLine", "17")
        }
        legacyMeta.outputStream().use { legacyProps.store(it, "Legacy draft") }
        val originalMetaBytes = legacyMeta.readBytes()

        val firstLoad = repository.loadDraft(null).getOrThrow()!!
        assertEquals("legacy saved draft", firstLoad.second)
        assertEquals("old.txt", firstLoad.first.displayName)
        assertEquals(17, firstLoad.first.cursorLine)

        val updated = firstLoad.first.copy(displayName = "updated.txt", cursorLine = 25)
        assertTrue(repository.saveDraft(updated, "new safe draft").isSuccess)
        assertEquals("legacy saved draft", legacyContent.readText(Charsets.UTF_8))
        assertTrue(originalMetaBytes.contentEquals(legacyMeta.readBytes()))
        assertEquals("0", File(tempDir, "untitled.active").readText())

        val restored = FileDraftRepository(tempDir).loadDraft(null).getOrThrow()!!
        assertEquals("new safe draft", restored.second)
        assertEquals("updated.txt", restored.first.displayName)
        assertEquals(25, restored.first.cursorLine)
        assertTrue(File(tempDir, "untitled.draft.0").exists())
        val persisted = java.util.Properties()
        File(tempDir, "untitled.meta.0").inputStream().use { persisted.load(it) }
        assertEquals("updated.txt", persisted.getProperty("displayName"))
    }

    @Test
    fun interruptedInactiveSlotNeverMixesTextAndMetadata() = runBlocking {
        val old = EditorSession(null, "old.txt", SyntaxMode.KOTLIN, true, cursorLine = 3)
        val newer = old.copy(displayName = "new.txt", cursorLine = 22)
        assertTrue(repository.saveDraft(old, "old contents").isSuccess)
        assertEquals("0", File(tempDir, "untitled.active").readText())

        // Simulate process death after the next content replacement, but
        // before the metadata write and active-marker commit.
        File(tempDir, "untitled.draft.1").writeText("orphaned partial")
        val afterCrash = FileDraftRepository(tempDir).loadDraft(null).getOrThrow()!!
        assertEquals("old contents", afterCrash.second)
        assertEquals("old.txt", afterCrash.first.displayName)

        assertTrue(repository.saveDraft(newer, "new contents").isSuccess)
        assertEquals("1", File(tempDir, "untitled.active").readText())

        // Later interruption while rewriting the inactive older slot.
        File(tempDir, "untitled.draft.0").writeText("another partial")
        val restored = FileDraftRepository(tempDir).loadDraft(null).getOrThrow()!!
        assertEquals("new contents", restored.second)
        assertEquals("new.txt", restored.first.displayName)
        assertEquals(22, restored.first.cursorLine)
    }

    @Test
    fun corruptCommitMarkerFailsClosedWithoutDestroyingDraft() = runBlocking {
        val session = EditorSession(null, "keep.txt", SyntaxMode.PLAIN_TEXT, true)
        assertTrue(repository.saveDraft(session, "important draft").isSuccess)
        val committed = File(tempDir, "untitled.draft.0")
        File(tempDir, "untitled.active").writeText("unknown marker")

        assertTrue(repository.hasDraft(null))
        assertTrue(repository.loadDraft(null).isFailure)
        assertTrue(repository.saveDraft(session, "dangerous overwrite").isFailure)
        assertEquals("important draft", committed.readText(Charsets.UTF_8))
    }

    @Test
    fun incompleteCommittedPairFailsInsteadOfSilentlyRevertingToLegacy() = runBlocking {
        val session = EditorSession(null, "new.txt", SyntaxMode.PLAIN_TEXT, true)
        File(tempDir, "untitled.draft").writeText("older legacy content")
        File(tempDir, "untitled.meta").writeText("displayName=older.txt")
        assertTrue(repository.saveDraft(session, "fresh content").isSuccess)
        assertTrue(File(tempDir, "untitled.meta.0").delete())

        assertTrue(repository.hasDraft(null))
        assertTrue(repository.loadDraft(null).isFailure)
        assertEquals("older legacy content", File(tempDir, "untitled.draft").readText())
    }

    @Test
    fun clearTombstoneDoesNotResurrectLeftoverLegacyPair() = runBlocking {
        val session = EditorSession(null, "before.txt", SyntaxMode.PLAIN_TEXT, true)
        assertTrue(repository.saveDraft(session, "before text").isSuccess)
        assertTrue(repository.clearDraft(null).isSuccess)
        assertEquals("deleted", File(tempDir, "untitled.active").readText())

        // Simulate leftover legacy files after interrupted cleanup/old backup.
        File(tempDir, "untitled.draft").writeText("stale content")
        File(tempDir, "untitled.meta").writeText("displayName=stale.txt")
        val reopened = FileDraftRepository(tempDir)
        assertFalse(reopened.hasDraft(null))
        assertNull(reopened.loadDraft(null).getOrThrow())

        assertTrue(reopened.saveDraft(session.copy(displayName = "new.txt"), "fresh draft").isSuccess)
        assertEquals("fresh draft", reopened.loadDraft(null).getOrThrow()!!.second)
    }

    @Test
    fun partialLegacyPairReportsFailureInsteadOfForgettingSavedText() = runBlocking {
        File(tempDir, "untitled.draft").writeText("recover me")
        assertTrue(repository.hasDraft(null))
        assertTrue(repository.loadDraft(null).isFailure)
    }
}
