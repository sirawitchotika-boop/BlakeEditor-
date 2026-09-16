package com.blake.editor.file

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.blake.editor.core.model.OpenedDocument

class DocumentMetadataReader(private val contentResolver: ContentResolver) {

    fun readMetadata(uri: Uri): OpenedDocument {
        var displayName = "untitled.txt"
        var sizeBytes = 0L

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex) ?: "untitled.txt"
                }

                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }

        val mimeType = contentResolver.getType(uri) ?: "text/plain"

        return OpenedDocument(
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            isReadOnly = false
        )
    }
}
