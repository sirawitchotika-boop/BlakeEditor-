package com.blake.editor.core.model

import android.net.Uri

data class OpenedDocument(
    val uri: Uri?,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isReadOnly: Boolean = false
)
