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
}
