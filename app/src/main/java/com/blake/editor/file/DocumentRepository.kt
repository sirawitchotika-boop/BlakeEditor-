package com.blake.editor.file

import android.net.Uri
import com.blake.editor.core.model.OpenedDocument

interface DocumentRepository {
    suspend fun read(uri: Uri): Result<Pair<OpenedDocument, String>>
    /** Safe overwrite: requires a fingerprint established by read() in this app session. */
    suspend fun write(uri: Uri, content: String): Result<Unit>
    /** SAF CreateDocument destination; never overwrite an existing nonempty file. */
    suspend fun writeNewDocument(uri: Uri, content: String): Result<Unit> = write(uri, content)
    suspend fun create(parentUri: Uri?, displayName: String, content: String): Result<OpenedDocument>
    suspend fun getMetadata(uri: Uri): Result<OpenedDocument>
}
