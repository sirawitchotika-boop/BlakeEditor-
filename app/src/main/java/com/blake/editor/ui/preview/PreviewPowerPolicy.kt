package com.blake.editor.ui.preview

/**
 * A quiet editor should not repeatedly rewrite recovery files.
 * These waits are UI-visible-only debounce intervals, not background timers.
 */
object PreviewPowerPolicy {
    const val TEXT_IDLE_MILLIS = 600L
    const val CURSOR_IDLE_MILLIS = 1_800L

    fun autosaveDelay(pendingTextChange: Boolean): Long =
        if (pendingTextChange) TEXT_IDLE_MILLIS else CURSOR_IDLE_MILLIS

    fun needsWrite(documentId: String, revision: Long,
        savedId: String?, savedRevision: Long
    ): Boolean = documentId != savedId || revision > savedRevision
}
