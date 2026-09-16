package com.blake.editor.editor

import android.webkit.JavascriptInterface

class EditorBridge(
    private val onEvent: (EditorEvent) -> Unit
) {

    @JavascriptInterface
    fun onEditorReady() {
        onEvent(EditorEvent.EditorReady)
    }

    @JavascriptInterface
    fun onContentChanged(text: String) {
        onEvent(EditorEvent.ContentChanged(text))
    }

    @JavascriptInterface
    fun onCursorPositionChanged(line: Int, column: Int) {
        onEvent(EditorEvent.CursorPositionChanged(line, column))
    }

    @JavascriptInterface
    fun onScrollChanged(scrollY: Int) {
        onEvent(EditorEvent.ScrollChanged(scrollY))
    }

    @JavascriptInterface
    fun onContentDelivered(text: String) {
        onEvent(EditorEvent.ContentDelivered(text))
    }
}
