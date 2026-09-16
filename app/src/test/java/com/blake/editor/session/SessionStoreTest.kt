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

class SessionStoreTest {

    private lateinit var tempFile: File
    private lateinit var store: SessionStore

    @Before
    fun setUp() {
        val tempDir = Files.createTempDirectory("blake_session_test").toFile()
        tempFile = File(tempDir, "session_state.properties")
        store = SessionStore(sessionFile = tempFile)
    }

    @Test
    fun testSaveAndLoadSession() = runBlocking {
        assertFalse(store.hasSession())

        val session = EditorSession(
            uri = null,
            displayName = "MainActivity.kt",
            syntaxMode = SyntaxMode.KOTLIN,
            isDirty = true,
            cursorLine = 42,
            cursorColumn = 10,
            scrollPosition = 350
        )

        val saveResult = store.saveSession(session)
        assertTrue(saveResult.isSuccess)
        assertTrue(store.hasSession())

        val loadResult = store.loadSession()
        assertTrue(loadResult.isSuccess)

        val loaded = loadResult.getOrNull()
        assertNotNull(loaded)
        assertEquals("MainActivity.kt", loaded!!.displayName)
        assertEquals(SyntaxMode.KOTLIN, loaded.syntaxMode)
        assertTrue(loaded.isDirty)
        assertEquals(42, loaded.cursorLine)
        assertEquals(10, loaded.cursorColumn)
        assertEquals(350, loaded.scrollPosition)
        assertNull(loaded.uri)
    }

    @Test
    fun testClearSession() = runBlocking {
        val session = EditorSession(
            uri = null,
            displayName = "notes.txt",
            syntaxMode = SyntaxMode.PLAIN_TEXT
        )
        store.saveSession(session)
        assertTrue(store.hasSession())

        val clearResult = store.clearSession()
        assertTrue(clearResult.isSuccess)
        assertFalse(store.hasSession())

        val loadResult = store.loadSession()
        assertTrue(loadResult.isSuccess)
        assertNull(loadResult.getOrNull())
    }

    @Test
    fun testLoadEmptySessionReturnsNull() = runBlocking {
        assertFalse(store.hasSession())
        val loadResult = store.loadSession()
        assertTrue(loadResult.isSuccess)
        assertNull(loadResult.getOrNull())
    }
}
