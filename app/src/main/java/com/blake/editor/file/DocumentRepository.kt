package com.blake.editor.file

import android.net.Uri
import com.blake.editor.core.model.OpenedDocument

interface DocumentRepository {
    suspend fun read(uri: Uri): Result<Pair<OpenedDocument, String>>
    suspend fun write(uri: Uri, content: String): Result<Unit>
    suspend fun create(parentUri: Uri?, displayName: String, content: String): Result<OpenedDocument>
    suspend fun getMetadata(uri: Uri): Result<OpenedDocument>
}
