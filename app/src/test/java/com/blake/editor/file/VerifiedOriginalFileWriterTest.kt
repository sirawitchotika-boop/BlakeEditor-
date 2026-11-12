package com.blake.editor.file

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedOriginalFileWriterTest {
    private fun tempFolder() = Files.createTempDirectory("blake-original-backup-").toFile()
    private fun privateBackups(dir: File) = File(dir, "backups").listFiles()?.toList().orEmpty()
    private fun bytes(value: String) = value.toByteArray(Charsets.UTF_8)

    @Test fun unchangedOriginalCanSaveAndVerifiesReadback() {
        val folder = tempFolder()
        try {
            var disk = bytes("Original")
            val writer = VerifiedOriginalFileWriter(File(folder, "backups"),
                readBytes = { disk.copyOf() }, writeBytes = { disk = it.copyOf() })
            val result = writer.saveExisting(VerifiedOriginalFileWriter.fingerprint(disk), bytes("Updated"))
            assertTrue(result.isSuccess)
            assertEquals(VerifiedOriginalFileWriter.fingerprint(bytes("Updated")), result.getOrThrow())
            assertArrayEquals(bytes("Updated"), disk)
            assertTrue(privateBackups(folder).isEmpty())
        } finally { folder.deleteRecursively() }
    }

    @Test fun externallyEditedDocumentIsRejectedWithoutWriting() {
        val folder = tempFolder()
        try {
            val expected = VerifiedOriginalFileWriter.fingerprint(bytes("Old"))
            var disk = bytes("Other app edited this")
            var wrote = false
            val writer = VerifiedOriginalFileWriter(File(folder, "backups"),
                readBytes = { disk.copyOf() }, writeBytes = { wrote = true; disk = it.copyOf() })
            assertTrue(writer.saveExisting(expected, bytes("My edits")).isFailure)
            assertFalse(wrote)
            assertArrayEquals(bytes("Other app edited this"), disk)
            assertTrue(privateBackups(folder).isEmpty())
        } finally { folder.deleteRecursively() }
    }

    @Test fun partialProviderWriteRetainsOriginalBackup() {
        val folder = tempFolder()
        try {
            var disk = bytes("Keep original")
            val original = disk.copyOf()
            val writer = VerifiedOriginalFileWriter(File(folder, "backups"),
                readBytes = { disk.copyOf() }, writeBytes = { disk = it.copyOfRange(0, 2) })
            assertTrue(writer.saveExisting(VerifiedOriginalFileWriter.fingerprint(disk), bytes("Updated text")).isFailure)
            val backups = privateBackups(folder)
            assertEquals(1, backups.size)
            assertArrayEquals(original, backups.single().readBytes())
        } finally { folder.deleteRecursively() }
    }

    @Test fun throwingProviderRetainsOriginalBackup() {
        val folder = tempFolder()
        try {
            var disk = bytes("Keep")
            val writer = VerifiedOriginalFileWriter(File(folder, "backups"),
                readBytes = { disk.copyOf() }, writeBytes = { disk = bytes(""); throw IOException("provider failed") })
            assertTrue(writer.saveExisting(VerifiedOriginalFileWriter.fingerprint(disk), bytes("Updated")).isFailure)
            assertArrayEquals(bytes("Keep"), privateBackups(folder).single().readBytes())
        } finally { folder.deleteRecursively() }
    }

    @Test fun newEmptyDocumentCanSaveAndReadback() {
        val folder = tempFolder()
        try {
            var disk = byteArrayOf()
            val writer = VerifiedOriginalFileWriter(File(folder, "backups"),
                readBytes = { disk.copyOf() }, writeBytes = { disk = it.copyOf() })
            assertTrue(writer.saveNew(bytes("Saved As")).isSuccess)
            assertArrayEquals(bytes("Saved As"), disk)
        } finally { folder.deleteRecursively() }
    }

    @Test fun nonEmptySaveAsTargetCannotBeOverwritten() {
        val folder = tempFolder()
        try {
            var disk = bytes("Existing file")
            var wrote = false
            val writer = VerifiedOriginalFileWriter(File(folder, "backups"),
                readBytes = { disk.copyOf() }, writeBytes = { wrote = true; disk = it.copyOf() })
            assertTrue(writer.saveNew(bytes("Overwrite")).isFailure)
            assertFalse(wrote)
            assertArrayEquals(bytes("Existing file"), disk)
        } finally { folder.deleteRecursively() }
    }

    @Test fun newFileIncompleteReadbackIsRejected() {
        val folder = tempFolder()
        try {
            var disk = byteArrayOf()
            val writer = VerifiedOriginalFileWriter(File(folder, "backups"),
                readBytes = { disk.copyOf() }, writeBytes = { disk = it.copyOfRange(0, 1) })
            assertTrue(writer.saveNew(bytes("longer")).isFailure)
        } finally { folder.deleteRecursively() }
    }
}
