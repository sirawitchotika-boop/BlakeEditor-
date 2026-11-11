package com.blake.editor.editor

/**
 * Small in-memory undo/redo history for the original editor's stable native
 * input mode. This is not a substitute for automatic recovery snapshots.
 *
 * Avoid keeping large document copies in RAM. Consecutive typing within
 * GROUP_MILLIS is one undo step; the history is reset on file changes.
 */
class NativeUndoHistory {
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var previousText = ""
    private var lastEditTime = Long.MIN_VALUE
    private var undoChars = 0
    private var redoChars = 0

    companion object {
        const val GROUP_MILLIS = 750L
        const val MAX_SNAPSHOT_CHARS = 100_000
        const val MAX_HISTORY_CHARS = 240_000
        const val MAX_ENTRIES = 24
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun reset(content: String) {
        previousText = content
        undoStack.clear()
        redoStack.clear()
        undoChars = 0
        redoChars = 0
        lastEditTime = Long.MIN_VALUE
    }

    fun edited(next: String, nowMs: Long) {
        if (next == previousText) return
        val newGroup = undoStack.isEmpty() || lastEditTime == Long.MIN_VALUE ||
            nowMs < lastEditTime || nowMs - lastEditTime >= GROUP_MILLIS ||
            redoStack.isNotEmpty()
        if (newGroup) {
            if (previousText.length <= MAX_SNAPSHOT_CHARS) {
                undoStack.addLast(previousText)
                undoChars += previousText.length
                while (undoStack.size > MAX_ENTRIES || undoChars > MAX_HISTORY_CHARS) {
                    undoChars -= undoStack.removeFirst().length
                }
            } else {
                undoStack.clear()
                undoChars = 0
            }
        }
        previousText = next
        redoStack.clear()
        redoChars = 0
        lastEditTime = nowMs
    }

    fun undo(): String? {
        if (undoStack.isEmpty()) return null
        val next = undoStack.removeLast()
        undoChars -= next.length
        if (previousText.length <= MAX_SNAPSHOT_CHARS) {
            redoStack.addLast(previousText)
            redoChars += previousText.length
            while (redoStack.size > MAX_ENTRIES || redoChars > MAX_HISTORY_CHARS) {
                redoChars -= redoStack.removeFirst().length
            }
        }
        previousText = next
        lastEditTime = Long.MIN_VALUE
        return next
    }

    fun redo(): String? {
        if (redoStack.isEmpty()) return null
        val next = redoStack.removeLast()
        redoChars -= next.length
        if (previousText.length <= MAX_SNAPSHOT_CHARS) {
            undoStack.addLast(previousText)
            undoChars += previousText.length
            while (undoStack.size > MAX_ENTRIES || undoChars > MAX_HISTORY_CHARS) {
                undoChars -= undoStack.removeFirst().length
            }
        }
        previousText = next
        lastEditTime = Long.MIN_VALUE
        return next
    }
}
