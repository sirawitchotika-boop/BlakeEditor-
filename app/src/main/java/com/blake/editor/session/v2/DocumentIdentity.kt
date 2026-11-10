package com.blake.editor.session.v2

import java.security.MessageDigest
import java.util.UUID

/**
 * Stable, filesystem-safe identity. Multiple untitled documents have different IDs.
 * Existing Android documents use a deterministic ID derived from their URI string.
 */
data class DocumentIdentity private constructor(val value: String) {
    companion object {
        private val safePattern = Regex("(?:new-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|uri-[0-9a-f]{64})")

        fun newUntitled(): DocumentIdentity = DocumentIdentity("new-" + UUID.randomUUID())

        fun forUri(uri: String): DocumentIdentity {
            require(uri.isNotBlank()) { "Document URI must not be blank" }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(uri.toByteArray(Charsets.UTF_8))
            val hash = digest.joinToString("") { "%02x".format(it) }
            return DocumentIdentity("uri-$hash")
        }

        /** A stable ID for the single untitled draft stored by the old app. */
        internal fun legacyUntitled(): DocumentIdentity = DocumentIdentity(
            "new-" + UUID.nameUUIDFromBytes("blake-v1-untitled-draft".toByteArray(Charsets.UTF_8))
        )

        internal fun fromStored(raw: String): DocumentIdentity {
            require(safePattern.matches(raw)) { "Invalid document ID" }
            return DocumentIdentity(raw)
        }
    }
}
