package com.blake.editor.session

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Replaces a single app-private state file without truncating the old version.
 *
 * Stage and fsync the new bytes in the destination directory, then require
 * an atomic rename. If staging or atomic replacement fails, report failure
 * instead of falling back to an in-place truncating write.
 *
 * This protects one file, not multi-file transactions or external SAF URIs.
 */
internal object AtomicPrivateFileWriter {
    @Synchronized
    fun write(destination: File, writeContent: (FileOutputStream) -> Unit) {
        val parent = destination.parentFile
            ?: throw IOException("State file has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Cannot create state directory")
        }
        if (!parent.isDirectory) {
            throw IOException("State parent is not a directory")
        }

        val staged = Files.createTempFile(parent.toPath(), ".blake-state-", ".tmp")
        try {
            FileOutputStream(staged.toFile()).use { output ->
                writeContent(output)
                output.fd.sync()
            }
            Files.move(
                staged,
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            Files.deleteIfExists(staged)
        }
    }
}
