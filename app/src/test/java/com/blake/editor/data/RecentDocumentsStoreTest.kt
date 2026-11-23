package com.blake.editor.data

import com.blake.editor.model.RecentFile
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File

class RecentDocumentsStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun deduplicatesOrdersByMostRecentAndRemovesCleanly() {
        val store = RecentDocumentsStore(File(tempFolder.root, "recents.bin"))
        store.record(RecentFile("content://doc/1", "notes.md", "text/markdown", 100L))
        store.record(RecentFile("content://doc/2", "todo.txt", "text/plain", 200L))
        store.record(RecentFile("content://doc/1", "notes.md", "text/markdown", 300L))

        val loaded = store.load()
        assertEquals(2, loaded.size)
        assertEquals("content://doc/1", loaded[0].uriString)
        assertEquals("content://doc/2", loaded[1].uriString)

        val afterRemove = store.remove("content://doc/1")
        assertEquals(1, afterRemove.size)
        assertEquals("content://doc/2", afterRemove[0].uriString)
    }
}
