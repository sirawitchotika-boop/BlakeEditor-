package com.blake.editor.editor

/** UTF-16 character range matching Android EditText selection offsets. */
data class TextMatch(val start: Int, val end: Int) {
    init {
        require(start >= 0 && end > start) { "Invalid match range: [$start, $end)" }
    }
}

data class ReplaceResult(
    val text: String,
    val replacedCount: Int,
    val newCursorOffset: Int
)

/**
 * Pure literal (non-regex) document search and replace engine.
 * Designed for live match highlighting and instant jump-to-match scrolling.
 */
object DocumentSearch {
    const val MAX_HIGHLIGHT_MATCHES = 10_000
    const val MAX_REPLACE_MATCHES = 100_000

    fun findAll(
        text: String,
        query: String,
        ignoreCase: Boolean = true,
        limit: Int = MAX_HIGHLIGHT_MATCHES
    ): List<TextMatch> {
        if (query.isEmpty() || text.isEmpty() || limit <= 0) return emptyList()
        val results = ArrayList<TextMatch>()
        var cursor = 0
        while (cursor <= text.length - query.length && results.size < limit) {
            val found = text.indexOf(query, cursor, ignoreCase)
            if (found < 0) break
            results.add(TextMatch(found, found + query.length))
            cursor = found + query.length
        }
        return results
    }

    /** Picks the first match at or after cursorOffset, wrapping to index 0 if none remain. */
    fun nearestMatchIndex(matches: List<TextMatch>, cursorOffset: Int): Int {
        if (matches.isEmpty()) return -1
        val safeCursor = cursorOffset.coerceAtLeast(0)
        val forward = matches.indexOfFirst { it.start >= safeCursor }
        return if (forward >= 0) forward else 0
    }

    fun nextIndex(currentIndex: Int, totalCount: Int): Int {
        if (totalCount <= 0) return -1
        if (currentIndex < 0 || currentIndex >= totalCount) return 0
        return (currentIndex + 1) % totalCount
    }

    fun previousIndex(currentIndex: Int, totalCount: Int): Int {
        if (totalCount <= 0) return -1
        if (currentIndex < 0 || currentIndex >= totalCount) return totalCount - 1
        return (currentIndex - 1 + totalCount) % totalCount
    }

    fun replaceAt(text: String, match: TextMatch, replacement: String): ReplaceResult {
        require(match.start >= 0 && match.end <= text.length) { "Match out of bounds" }
        val updated = buildString(text.length - (match.end - match.start) + replacement.length) {
            append(text, 0, match.start)
            append(replacement)
            append(text, match.end, text.length)
        }
        return ReplaceResult(
            text = updated,
            replacedCount = 1,
            newCursorOffset = match.start + replacement.length
        )
    }

    /**
     * Non-overlapping literal replace-all. Replacing "a" with "aa" always terminates.
     */
    fun replaceAll(
        text: String,
        query: String,
        replacement: String,
        ignoreCase: Boolean = true
    ): ReplaceResult {
        require(query.isNotEmpty()) { "Enter text to find" }
        val output = StringBuilder(text.length)
        var cursor = 0
        var count = 0
        while (cursor < text.length) {
            val found = text.indexOf(query, cursor, ignoreCase)
            if (found < 0) break
            if (count >= MAX_REPLACE_MATCHES) {
                throw IllegalArgumentException("Too many matches. Use a longer search phrase.")
            }
            output.append(text, cursor, found)
            output.append(replacement)
            cursor = found + query.length
            count++
        }
        if (count == 0) return ReplaceResult(text, 0, 0)
        output.append(text, cursor, text.length)
        return ReplaceResult(output.toString(), count, output.length)
    }
}
