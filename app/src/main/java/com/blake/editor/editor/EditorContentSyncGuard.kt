package com.blake.editor.editor

/**
 * Prevent native state updates from feeding the text just typed in the
 * WebView back through Ace.setValue(), which resets its caret/undo state.
 *
 * Call methods only on Android's main thread, just like ViewModel updates.
 */
class EditorContentSyncGuard {
    private var lastSentOrObservedText: String? = null

    /** Native state must be sent to the editor only if it actually differs. */
    fun shouldApplyNativeContent(text: String): Boolean {
        if (lastSentOrObservedText == text) return false
        lastSentOrObservedText = text
        return true
    }

    /** Typing from the web editor already exists there. Never send it back. */
    fun onWebEditorChanged(text: String) {
        lastSentOrObservedText = text
    }
}
