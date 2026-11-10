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
