package com.blake.editor.intent

import android.content.Intent
import android.net.Uri

class IncomingIntentParser {

    fun parse(intent: Intent?): OpenDocumentRequest? = Companion.parse(intent)

    companion object {
        fun parse(intent: Intent?): OpenDocumentRequest? {
            if (intent == null) return null
            val action = intent.action ?: return null
            if (action != Intent.ACTION_VIEW && action != Intent.ACTION_EDIT) {
                return null
            }

            val uri: Uri = intent.data
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                ?: return null

            return OpenDocumentRequest(
                uri = uri,
                mimeType = intent.type,
                action = action
            )
        }
    }
}
