package com.blake.editor.session.v2

import com.blake.editor.file.DecodedTextFile
import com.blake.editor.file.TextEncodingCodec
import com.blake.editor.file.TextFileEncoding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Verifies save bytes and protects unsaved recovery snapshots.
 *
 * Caller MUST store the latest RecoveryDraft before calling either save method.
 * SAF providers may perform non-atomic writes; on overwrite failures the
 * original bytes are kept in an internal backup, never silently discarded.
 */
class VerifiedDocumentSaver(
    private val backupDirectory: File,
    private val readBytes: (String) -> ByteArray,
    private val writeBytes: (String, ByteArray) -> Unit
) {
    data class SavedFile(
        val uri: String,
        val sha256: String,
        val encoding: TextFileEncoding,
        val hadBom: Boolean,
        val verifiedBytes: Int
    )

    fun saveExisting(snapshot: RecoveryDraft): Result<SavedFile> = runCatching {
        val uri = requireNotNull(snapshot.sourceUri) { "Use Save As for a new file" }
        val expectedHash = requireNotNull(snapshot.sourceSha256) {
            "This older draft has no original-file fingerprint. Use Save As to avoid overwriting external changes."
        }
        val format = requireNotNull(snapshot.sourceEncoding) {
            "File encoding is unknown. Use Save As."
        }
        val encoding = TextFileEncoding.valueOf(format)
        val encoded = encode(snapshot.content, encoding, snapshot.sourceHadBom)
        val original = readBytes(uri)
        require(fingerprint(original) == expectedHash) {
            "The original file changed outside Blake. Save As to keep both versions."
        }
        require(original.size <= MAX_BACKUP_BYTES) { "File is too large for safe overwrite backup" }

        val backup = File(backupDirectory, snapshot.id.value + "-" + snapshot.revision + ".original")
        atomicBackup(backup, original)
        try {
            writeBytes(uri, encoded)
            require(fingerprint(readBytes(uri)) == fingerprint(encoded)) {
                "The storage provider did not preserve the complete saved file"
            }
            // Backup can be removed only after the entire saved file is re-read
            // and verified. Failed writes preserve the backup and draft.
            check(backup.delete()) { "File saved, but cleanup of backup failed; backup retained" }
        } catch (failure: Exception) {
            throw IOException(
                "Save could not be verified. Your draft and original-file backup were retained at " +
                    backup.name,
                failure
            )
        }
        SavedFile(uri, fingerprint(encoded), encoding, snapshot.sourceHadBom, encoded.size)
    }

    fun saveAs(
        snapshot: RecoveryDraft,
        newUri: String,
        encoding: TextFileEncoding = TextFileEncoding.UTF8,
        hadBom: Boolean = false
    ): Result<SavedFile> = runCatching {
        require(newUri.isNotBlank()) { "Save As destination is missing" }
        // Save As never truncates the old document. The Android CreateDocument
        // picker decides and confirms the destination filename.
        val data = encode(snapshot.content, encoding, hadBom)
        writeBytes(newUri, data)
        require(fingerprint(readBytes(newUri)) == fingerprint(data)) {
            "The new file could not be verified; recovery draft was kept"
        }
        SavedFile(newUri, fingerprint(data), encoding, hadBom, data.size)
    }

    companion object {
        const val MAX_BACKUP_BYTES = 2 * 1024 * 1024

        fun fingerprint(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }

        fun afterSave(
            snapshot: RecoveryDraft,
            result: SavedFile,
            displayName: String,
            language: String
        ): RecoveryDraft {
            val sameFile = snapshot.sourceUri == result.uri
            return snapshot.copy(
                id = if (sameFile) snapshot.id else DocumentIdentity.forUri(result.uri),
                sourceUri = result.uri,
                displayName = displayName,
                language = language,
                sourceEncoding = result.encoding.name,
                sourceHadBom = result.hadBom,
                sourceSha256 = result.sha256,
                savedToDisk = true,
                revision = if (sameFile) snapshot.revision + 1 else 1,
                updatedAtMs = System.currentTimeMillis()
            )
        }

        private fun encode(text: String, encoding: TextFileEncoding, hadBom: Boolean): ByteArray =
            TextEncodingCodec.encode(DecodedTextFile("", encoding, hadBom), text).getOrThrow()
    }

    private fun atomicBackup(destination: File, content: ByteArray) {
        if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
            throw IOException("Could not prepare safe-save backup directory")
        }
        val temp = Files.createTempFile(backupDirectory.toPath(), "backup-", ".tmp")
        try {
            FileOutputStream(temp.toFile()).use { stream ->
                stream.write(content)
                stream.fd.sync()
            }
            Files.move(temp, destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
