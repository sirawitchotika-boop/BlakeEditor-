package com.blake.editor.editor

sealed class EditorEvent {
    object EditorReady : EditorEvent()
    data class ContentChanged(val text: String) : EditorEvent()
    data class CursorPositionChanged(val line: Int, val column: Int) : EditorEvent()
    data class ScrollChanged(val scrollY: Int) : EditorEvent()
    data class ContentDelivered(val text: String) : EditorEvent()
}
