package com.blake.editor.intent

import android.net.Uri

data class OpenDocumentRequest(
    val uri: Uri,
    val mimeType: String? = null,
    val action: String? = null
)
