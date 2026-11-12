package com.blake.editor.file

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

/** Testable, readback-verified Original file saves using only private backup storage. */
internal class VerifiedOriginalFileWriter(
    private val backupDirectory: File,
    private val readBytes: () -> ByteArray,
    private val writeBytes: (ByteArray) -> Unit
) {
    fun saveExisting(expectedSha256: String, updatedBytes: ByteArray): Result<String> = runCatching {
        val before = readBytes()
        check(fingerprint(before) == expectedSha256) {
            "The source changed outside Blake. Use Save As to keep both versions."
        }
        val backup = createPrivateBackup(before)
        try {
            writeBytes(updatedBytes)
            check(readBytes().contentEquals(updatedBytes)) {
                "The provider did not save the complete file"
            }
        } catch (failure: Exception) {
            throw IOException(
                "Save could not be verified. Original bytes are retained in private backup ${backup.name}. " +
                    "Unsaved edits are still in the recovery draft.",
                failure
            )
        }
        check(backup.delete()) {
            "Saved, but cleanup of private original backup failed; backup retained."
        }
        fingerprint(updatedBytes)
    }

    /** SAF CreateDocument creates an empty destination. Reject a reused/nonempty URI. */
    fun saveNew(updatedBytes: ByteArray): Result<String> = runCatching {
        check(readBytes().isEmpty()) {
            "Save As destination is not empty. Choose another filename."
        }
        writeBytes(updatedBytes)
        check(readBytes().contentEquals(updatedBytes)) {
            "The provider did not preserve the complete new file; recovery draft retained."
        }
        fingerprint(updatedBytes)
    }

    private fun createPrivateBackup(before: ByteArray): File {
        if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
            throw IOException("Unable to create private Original file backup directory")
        }
        val backup = Files.createTempFile(backupDirectory.toPath(), "original-", ".backup").toFile()
        try {
            FileOutputStream(backup).use { output ->
                output.write(before)
                output.fd.sync()
            }
        } catch (failure: Exception) {
            backup.delete()
            throw failure
        }
        return backup
    }

    companion object {
        fun fingerprint(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
