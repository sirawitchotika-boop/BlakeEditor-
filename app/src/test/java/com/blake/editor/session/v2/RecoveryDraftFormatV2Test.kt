package com.blake.editor.session.v2

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecoveryDraftFormatV2Test {
    private lateinit var root: java.io.File
    private lateinit var store: RecoveryDraftStore

    @Before fun setup() {
        root = Files.createTempDirectory("blake-v2-format").toFile()
        store = RecoveryDraftStore(root)
    }

    @Test fun newFormatPreservesEncodingBomFingerprintAndSavedState() {
        val uri = "content://provider/Thai.txt"
        val original = "สวัสดีครับ\r\n".toByteArray(Charsets.UTF_16LE)
        val doc = RecoveryDraft(
            id = DocumentIdentity.forUri(uri), sourceUri = uri,
            displayName = "Thai.txt", language = "PLAIN_TEXT", content = "สวัสดีครับ\r\n",
            sourceEncoding = "UTF16_LE", sourceHadBom = true,
            sourceSha256 = VerifiedDocumentSaver.fingerprint(original),
            savedToDisk = true
        )
        store.save(doc).getOrThrow()
        val reopened = RecoveryDraftStore(root).load(doc.id).getOrThrow()
        assertEquals(doc, reopened)
    }

    @Test fun editingTextMarksSavedDocumentDirtyButCursorOnlyDoesNot() {
        val uri = "content://provider/a.txt"
        val checksum = VerifiedDocumentSaver.fingerprint("hello".toByteArray())
        val doc = RecoveryDraft(
            DocumentIdentity.forUri(uri), uri, "a.txt", "PLAIN_TEXT", "hello",
            sourceEncoding = "UTF8", sourceSha256 = checksum, savedToDisk = true
        )
        val workspace = DraftWorkspace(store)
        val cursor = workspace.edit(doc, "hello", 3, 3, 0)
        assertTrue(cursor.savedToDisk)
        val changed = workspace.edit(cursor, "hello!", 6, 6, 0)
        assertFalse(changed.savedToDisk)
    }

    @Test fun openingExternallyChangedFileReportsConflictAndKeepsRecovery() {
        val workspace = DraftWorkspace(store)
        val uri = "content://provider/a.txt"
        val oldHash = VerifiedDocumentSaver.fingerprint("old".toByteArray())
        val newHash = VerifiedDocumentSaver.fingerprint("new".toByteArray())
        val loaded = workspace.openFromFile(uri, "a.txt", "PLAIN_TEXT",
            "old", "UTF8", false, oldHash).getOrThrow()
        assertTrue(loaded.savedToDisk)
        assertTrue(workspace.openFromFile(uri, "a.txt", "PLAIN_TEXT",
            "new", "UTF8", false, newHash).isFailure)
        assertEquals("old", workspace.load(loaded.id).getOrThrow()?.content)
    }

    @Test fun canReadOldVersionOneRecoveryFileWithoutDroppingContent() {
        val id = DocumentIdentity.newUntitled()
        val record = ByteArrayOutputStream()
        DataOutputStream(record).use { out ->
            out.writeInt(0x424C4B32)
            out.writeInt(1)
            fun field(text: String) {
                val bytes = text.toByteArray(Charsets.UTF_8)
                out.writeInt(bytes.size)
                out.write(bytes)
            }
            field(id.value)
            out.writeBoolean(false)
            field("Old draft")
            field("PLAIN_TEXT")
            out.writeInt(0)
            out.writeInt(0)
            out.writeInt(0)
            out.writeLong(4)
            out.writeLong(123)
            val text = "สวัสดีครับ".toByteArray(Charsets.UTF_8)
            out.writeInt(text.size)
            out.write(text)
        }
        root.resolve(id.value + ".recovery").writeBytes(record.toByteArray())
        val old = store.load(id).getOrThrow()
        assertNotNull(old)
        assertEquals("สวัสดีครับ", old!!.content)
        assertNull(old.sourceEncoding)
        assertFalse(old.savedToDisk)
        val updated = old.copy(revision = 5)
        assertTrue(store.save(updated).isSuccess)
        assertEquals(updated, store.load(id).getOrThrow())
    }
}
