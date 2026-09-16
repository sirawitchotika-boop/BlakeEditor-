package com.blake.editor.file

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

class UriPermissionManager(private val contentResolver: ContentResolver) {

    fun takePersistablePermissions(uri: Uri, isWriteRequired: Boolean = true) {
        var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (isWriteRequired) {
            flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            // Fallback for providers that don't support persistable permissions
        }
    }
}
