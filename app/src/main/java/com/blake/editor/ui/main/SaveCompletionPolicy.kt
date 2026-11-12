package com.blake.editor.ui.main

/** Identity of a Save/Save As snapshot, independent of Android UI classes. */
internal data class SaveStateSnapshot(
    val uriKey: String?,
    val documentGeneration: Long,
    val editRevision: Long
)

internal enum class SaveCompletionDecision { CURRENT, STALE, FAILED }

internal object SaveCompletionPolicy {
    fun decide(
        saved: SaveStateSnapshot,
        current: SaveStateSnapshot,
        writeSucceeded: Boolean
    ): SaveCompletionDecision = when {
        !writeSucceeded -> SaveCompletionDecision.FAILED
        saved != current -> SaveCompletionDecision.STALE
        else -> SaveCompletionDecision.CURRENT
    }
}
