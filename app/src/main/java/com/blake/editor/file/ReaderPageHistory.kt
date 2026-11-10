package com.blake.editor.file

/**
 * Records only successfully loaded pages.
 *
 * The reader loads asynchronously, so next/previous history must not change
 * when a page fails to load. State is independent of Android UI code and can
 * be regression-tested on the JVM.
 */
class ReaderPageHistory {
    private val previousStarts = ArrayDeque<Long>()

    var pageNumber: Int = 1
        private set

    val canGoBack: Boolean
        get() = previousStarts.isNotEmpty()

    fun previousStart(): Long? = previousStarts.lastOrNull()

    /** Call only after the next page loads successfully. */
    fun recordNext(currentStart: Long) {
        require(currentStart >= 0L) { "Invalid page start" }
        previousStarts.addLast(currentStart)
        pageNumber++
    }

    /** Call only after the previous page loads successfully. */
    fun recordPrevious() {
        require(previousStarts.isNotEmpty()) { "No earlier page" }
        previousStarts.removeLast()
        pageNumber--
    }

    fun reset() {
        previousStarts.clear()
        pageNumber = 1
    }
}
