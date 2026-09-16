package com.blake.editor.file

import android.content.ContentResolver
import android.net.Uri
import com.blake.editor.core.model.OpenedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class AndroidDocumentRepository(
    private val contentResolver: ContentResolver,
    private val metadataReader: DocumentMetadataReader,
    private val uriPermissionManager: UriPermissionManager
) : DocumentRepository {

    override suspend fun read(uri: Uri): Result<Pair<OpenedDocument, String>> = withContext(Dispatchers.IO) {
        runCatching {
            uriPermissionManager.takePersistablePermissions(uri, isWriteRequired = false)
            val metadata = metadataReader.readMetadata(uri)

            val inputStream = contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open input stream for URI: $uri")

            val content = inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }

            Pair(metadata, content)
        }
    }

    override suspend fun write(uri: Uri, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            uriPermissionManager.takePersistablePermissions(uri, isWriteRequired = true)

            val outputStream = contentResolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("Unable to open output stream for URI: $uri")

            outputStream.use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                    writer.write(content)
                    writer.flush()
                }
            }
        }
    }

    override suspend fun create(parentUri: Uri?, displayName: String, content: String): Result<OpenedDocument> = withContext(Dispatchers.IO) {
        runCatching {
            throw UnsupportedOperationException("create via SAF uses CreateDocument ActivityResultContract")
        }
    }

    override suspend fun getMetadata(uri: Uri): Result<OpenedDocument> = withContext(Dispatchers.IO) {
        runCatching {
            metadataReader.readMetadata(uri)
        }
    }
}
