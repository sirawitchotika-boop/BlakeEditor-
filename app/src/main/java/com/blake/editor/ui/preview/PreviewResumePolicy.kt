package com.blake.editor.ui.preview

import com.blake.editor.session.v2.RecoveryDraft

/**
 * Small extra safety net for Android process recreation and fold/unfold.
 * Large drafts remain in the atomic recovery store; do not put them in
 * Bundle because Android's Binder transaction has a limited size.
 */
data class ResumeSnapshot(
    val id: String,
    val content: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val scrollY: Int,
    val revision: Long
)

object PreviewResumePolicy {
    const val MAX_BUNDLE_TEXT_BYTES = 32 * 1024

    fun capture(draft: RecoveryDraft): ResumeSnapshot? {
        if (draft.content.toByteArray(Charsets.UTF_8).size > MAX_BUNDLE_TEXT_BYTES) {
            return null
        }
        return ResumeSnapshot(draft.id.value, draft.content,
            draft.selectionStart, draft.selectionEnd, draft.scrollY, draft.revision)
    }

    /**
     * Only apply an in-memory snapshot if it belongs to this same document
     * and is newer than the atomic recovery record. Never roll back edits
     * that have already been persisted by another Activity instance.
     */
    fun restore(disk: RecoveryDraft, pending: ResumeSnapshot?): RecoveryDraft? {
        if (pending == null || pending.id != disk.id.value ||
            pending.revision <= disk.revision || pending.revision < 1
        ) return null
        val start = pending.selectionStart.coerceIn(0, pending.content.length)
        val end = pending.selectionEnd.coerceIn(0, pending.content.length)
        return disk.copy(
            content = pending.content,
            selectionStart = start,
            selectionEnd = end,
            scrollY = pending.scrollY.coerceAtLeast(0),
            revision = pending.revision,
            savedToDisk = disk.savedToDisk && pending.content == disk.content,
            updatedAtMs = maxOf(System.currentTimeMillis(), disk.updatedAtMs)
        )
    }
}
