package com.blake.editor.session.v2

import com.blake.editor.file.TextFileEncoding
import java.nio.charset.Charset
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VerifiedDocumentSaverTest {
    private lateinit var files: MutableMap<String, ByteArray>
    private lateinit var dir: java.io.File
    private lateinit var saver: VerifiedDocumentSaver

    @Before fun setup() {
        files = mutableMapOf()
        dir = Files.createTempDirectory("blake-safe-save").toFile()
        saver = VerifiedDocumentSaver(
            dir,
            readBytes = { uri -> files.getValue(uri) },
            writeBytes = { uri, data -> files[uri] = data }
        )
    }

    private fun opened(bytes: ByteArray, charset: TextFileEncoding = TextFileEncoding.UTF8): RecoveryDraft {
        val uri = "content://provider/doc.txt"
        files[uri] = bytes
        return RecoveryDraft(
            id = DocumentIdentity.forUri(uri), sourceUri = uri,
            displayName = "doc.txt", language = "PLAIN_TEXT", content = "Changed",
            sourceEncoding = charset.name, sourceSha256 = VerifiedDocumentSaver.fingerprint(bytes),
            revision = 3
        )
    }

    @Test fun editedUtf8OverwritesOnlyWhenOriginalMatchesAndReadbackVerifies() {
        val doc = opened("Original".toByteArray())
        val result = saver.saveExisting(doc).getOrThrow()
        assertEquals("Changed", String(files.getValue(doc.sourceUri!!)))
        assertEquals(64, result.sha256.length)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test fun externalChangesBlockOverwrite() {
        val doc = opened("Original".toByteArray())
        files[doc.sourceUri!!] = "External edit".toByteArray()
        assertTrue(saver.saveExisting(doc).isFailure)
        assertEquals("External edit", String(files.getValue(doc.sourceUri!!)))
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test fun legacyDraftWithoutFingerprintMustUseSaveAs() {
        val uri = "content://provider/older.txt"
        files[uri] = "Original".toByteArray()
        val doc = RecoveryDraft(DocumentIdentity.forUri(uri), uri, "older.txt",
            "PLAIN_TEXT", "changed")
        assertTrue(saver.saveExisting(doc).isFailure)
        assertEquals("Original", String(files.getValue(uri)))
    }

    @Test fun saveAsKeepsOriginalUnchangedAndVerifiesNewFile() {
        val doc = opened("Original".toByteArray())
        val result = saver.saveAs(doc, "content://provider/new.txt").getOrThrow()
        assertEquals("Original", String(files.getValue(doc.sourceUri!!)))
        assertEquals("Changed", String(files.getValue(result.uri)))
        val newDoc = VerifiedDocumentSaver.afterSave(doc, result, "new.txt", "PLAIN_TEXT")
        assertEquals(DocumentIdentity.forUri(result.uri), newDoc.id)
        assertTrue(newDoc.savedToDisk)
    }

    @Test fun thaiEncodingRemainsSameWhenSavingExistingFile() {
        val thai = "สวัสดีครับ"
        val original = thai.toByteArray(Charset.forName("windows-874"))
        val doc = opened(original, TextFileEncoding.WINDOWS_874).copy(content = thai)
        saver.saveExisting(doc).getOrThrow()
        assertArrayEquals(original, files.getValue(doc.sourceUri!!))
    }

    @Test fun unrepresentableEmojiRejectsLegacySaveWithoutTouchingFile() {
        val original = "สวัสดี".toByteArray(Charset.forName("windows-874"))
        val doc = opened(original, TextFileEncoding.WINDOWS_874)
            .copy(content = "สวัสดี 🌏")
        assertTrue(saver.saveExisting(doc).isFailure)
        assertArrayEquals(original, files.getValue(doc.sourceUri!!))
    }

    @Test fun writeFailureRetainsOriginalBackup() {
        val doc = opened("Original".toByteArray())
        val broken = VerifiedDocumentSaver(dir,
            readBytes = { files.getValue(it) },
            writeBytes = { uri, _ ->
                files[uri] = "PARTIAL".toByteArray()
                throw IllegalStateException("Provider write failed")
            })
        assertTrue(broken.saveExisting(doc).isFailure)
        assertEquals("Original", dir.listFiles()!!.single().readText())
    }

    @Test fun readbackMismatchRetainsBackup() {
        val doc = opened("Original".toByteArray())
        val broken = VerifiedDocumentSaver(dir,
            readBytes = { files.getValue(it) },
            writeBytes = { uri, _ -> files[uri] = "Wrong".toByteArray() })
        assertTrue(broken.saveExisting(doc).isFailure)
        assertEquals("Original", dir.listFiles()!!.single().readText())
    }

    @Test fun saveAsErrorDoesNotChangeOldFile() {
        val doc = opened("Original".toByteArray())
        val broken = VerifiedDocumentSaver(dir,
            readBytes = { files.getValue(it) },
            writeBytes = { _, _ -> throw IllegalStateException("No access") })
        assertTrue(broken.saveAs(doc, "content://provider/new.txt").isFailure)
        assertEquals("Original", String(files.getValue(doc.sourceUri!!)))
    }
}
