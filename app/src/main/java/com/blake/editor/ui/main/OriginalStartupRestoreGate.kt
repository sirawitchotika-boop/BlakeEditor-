package com.blake.editor.ui.main

/**
 * One startup decision per ViewModel lifetime.
 *
 * A recreated Activity can have non-null savedInstanceState with a NEW ViewModel
 * after process death. A configuration change keeps the existing ViewModel.
 * The Bundle is deliberately NOT used to decide whether recovery is required.
 */
internal enum class OriginalStartupAction {
    RESTORE_SESSION,
    OPEN_INCOMING,
    ALREADY_INITIALIZED
}

internal class OriginalStartupRestoreGate {
    private var claimed = false

    fun claimInitialAction(hasIncomingDocument: Boolean): OriginalStartupAction {
        if (claimed) return OriginalStartupAction.ALREADY_INITIALIZED
        claimed = true
        return if (hasIncomingDocument) {
            OriginalStartupAction.OPEN_INCOMING
        } else {
            OriginalStartupAction.RESTORE_SESSION
        }
    }
}
