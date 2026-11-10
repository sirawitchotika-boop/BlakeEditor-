package com.blake.editor.editor.v2

/** Maps UTF-16 character offsets to real document line numbers. */
class CodeLineIndex {
    private var starts = intArrayOf(0)

    val lineCount: Int get() = starts.size

    fun rebuild(text: CharSequence) {
        var buffer = IntArray(128)
        var count = 1
        buffer[0] = 0
        for (i in text.indices) {
            val c = text[i]
            val isBreak = c == '\n' || (c == '\r' &&
                (i + 1 >= text.length || text[i + 1] != '\n'))
            if (isBreak) {
                if (count == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
                buffer[count++] = i + 1
            }
        }
        starts = buffer.copyOf(count)
    }

    /** True only at the start of an actual line, not a visually wrapped row. */
    fun isLogicalLineStart(offset: Int): Boolean =
        starts.binarySearch(offset) >= 0

    /** One-based line number for a UTF-16 offset. */
    fun lineAt(offset: Int): Int {
        val index = starts.binarySearch(offset.coerceAtLeast(0))
        return if (index >= 0) index + 1 else -index - 1
    }
}
