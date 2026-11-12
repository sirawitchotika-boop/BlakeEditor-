package com.blake.editor.ui.main

/**
 * Opening V2 never replaces the Original document or its draft.
 * Block an active file operation and warn before leaving unsaved text.
 */
enum class V2EntryDecision { OPEN, CONFIRM_UNSAVED, BLOCK_BUSY }

object V2EntryPolicy {
    fun decide(isDirty: Boolean, isBusy: Boolean): V2EntryDecision = when {
        isBusy -> V2EntryDecision.BLOCK_BUSY
        isDirty -> V2EntryDecision.CONFIRM_UNSAVED
        else -> V2EntryDecision.OPEN
    }
}
