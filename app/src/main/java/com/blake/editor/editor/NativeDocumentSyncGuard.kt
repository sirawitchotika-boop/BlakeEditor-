package com.blake.editor.editor

/**
 * Only a file open, restore or New may reset the native editor contents.
 * Typing, caret updates, autosaves and theme changes retain the IME buffer.
 */
class NativeDocumentSyncGuard {
    private var loadedGeneration: Long? = null

    fun shouldLoad(generation: Long): Boolean {
        if (loadedGeneration == generation) return false
        loadedGeneration = generation
        return true
    }

    companion object {
        /** Convert one-based line/column to native UTF-16 caret position. */
        fun offsetForLineColumn(text: String, line: Int, column: Int): Int {
            var start = 0
            var currentLine = 1
            while (start < text.length && currentLine < line) {
                val newline = text.indexOf('\n', start)
                if (newline < 0) return text.length
                start = newline + 1
                currentLine++
            }
            val end = text.indexOf('\n', start).let {
                if (it < 0) text.length else it
            }
            return (start + column.coerceAtLeast(1) - 1).coerceIn(start, end)
        }
    }
}
