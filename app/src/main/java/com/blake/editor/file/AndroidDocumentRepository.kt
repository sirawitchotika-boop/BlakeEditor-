package com.blake.editor.file

import android.content.ContentResolver
import android.net.Uri
import com.blake.editor.core.model.OpenedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Original editor SAF repository. A successful read establishes the exact bytes
 * allowed to be overwritten. The writer checks for external modifications and
 * keeps a private copy if the provider write or readback fails.
 */
class AndroidDocumentRepository(
    private val contentResolver: ContentResolver,
    private val metadataReader: DocumentMetadataReader,
    private val uriPermissionManager: UriPermissionManager,
    private val backupDirectory: File
) : DocumentRepository {
    private val expectedFingerprint = ConcurrentHashMap<String, String>()

    override suspend fun read(uri: Uri): Result<Pair<OpenedDocument, String>> = withContext(Dispatchers.IO) {
        runCatching {
            uriPermissionManager.takePersistablePermissions(uri, isWriteRequired = false)
            val metadata = metadataReader.readMetadata(uri)
            val bytes = readBoundedBytes(uri)
            // Register only after a successful fully bounded read.
            expectedFingerprint[uri.toString()] = VerifiedOriginalFileWriter.fingerprint(bytes)
            Pair(metadata, String(bytes, Charsets.UTF_8))
        }
    }

    override suspend fun write(uri: Uri, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            uriPermissionManager.takePersistablePermissions(uri, isWriteRequired = true)
            val key = uri.toString()
            val expected = expectedFingerprint[key]
                ?: throw IllegalStateException(
                    "This file has no verified opening snapshot (possibly restored from a draft). " +
                        "Use Save As to preserve the existing source."
                )
            val writer = writerFor(uri)
            val newHash = writer.saveExisting(expected, content.toByteArray(Charsets.UTF_8))
                .getOrThrow()
            expectedFingerprint[key] = newHash
        }
    }

    override suspend fun writeNewDocument(uri: Uri, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            uriPermissionManager.takePersistablePermissions(uri, isWriteRequired = true)
            val key = uri.toString()
            check(!expectedFingerprint.containsKey(key)) {
                "The destination is already open. Choose a new filename to preserve the original."
            }
            val newHash = writerFor(uri).saveNew(content.toByteArray(Charsets.UTF_8))
                .getOrThrow()
            expectedFingerprint[key] = newHash
        }
    }

    private fun writerFor(uri: Uri) = VerifiedOriginalFileWriter(
        backupDirectory = backupDirectory,
        readBytes = { readBoundedBytes(uri) },
        writeBytes = { bytes ->
            val out = contentResolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("Unable to open output stream for URI: $uri")
            out.use { stream ->
                stream.write(bytes)
                stream.flush()
            }
        }
    )

    private fun readBoundedBytes(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to open input stream for URI: $uri")
        return input.use { BoundedEditableTextReader.readBytes(it) }
    }

    override suspend fun create(parentUri: Uri?, displayName: String, content: String): Result<OpenedDocument> = withContext(Dispatchers.IO) {
        runCatching {
            throw UnsupportedOperationException("create via SAF uses CreateDocument ActivityResultContract")
        }
    }

    override suspend fun getMetadata(uri: Uri): Result<OpenedDocument> = withContext(Dispatchers.IO) {
        runCatching { metadataReader.readMetadata(uri) }
    }
}
