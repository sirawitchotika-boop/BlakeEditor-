package com.blake.editor.editor.v2

/** UTF-16 ranges match Android EditText selection offsets. */
data class TextMatch(val start: Int, val end: Int) {
    init { require(start >= 0 && end > start) }
}

data class ReplaceAllResult(val text: String, val count: Int)

data class InlineInsertResult(val text: String, val cursor: Int)

/**
 * Literal (not regex) search to avoid unexpected escapes or destructive edits.
 * All methods are pure and keep the original source unchanged until the user
 * explicitly chooses Replace or Replace All in the UI.
 */
object EditorFindReplace {
    private const val MAX_REPLACE_MATCHES = 100_000

    fun next(text: String, query: String, from: Int, ignoreCase: Boolean = false): TextMatch? {
        if (query.isEmpty()) return null
        val fromSafe = from.coerceIn(0, text.length)
        val found = text.indexOf(query, fromSafe, ignoreCase).takeIf { it >= 0 }
            ?: text.indexOf(query, 0, ignoreCase).takeIf { it >= 0 }
            ?: return null
        return TextMatch(found, found + query.length)
    }

    /** "before" is an exclusive boundary. Wrap to the last match if necessary. */
    fun previous(text: String, query: String, before: Int,
        ignoreCase: Boolean = false
    ): TextMatch? {
        if (query.isEmpty()) return null
        val safeBefore = before.coerceIn(0, text.length)
        val first = if (safeBefore > 0) {
            text.lastIndexOf(query, safeBefore - 1, ignoreCase)
        } else -1
        val found = if (first >= 0) first else {
            text.lastIndexOf(query, text.length, ignoreCase)
        }
        return if (found >= 0) TextMatch(found, found + query.length) else null
    }

    fun selectionMatches(text: String, start: Int, end: Int, query: String,
        ignoreCase: Boolean = false
    ): Boolean = query.isNotEmpty() && start >= 0 && end <= text.length &&
        start < end && text.substring(start, end).equals(query, ignoreCase)

    /**
     * Case-insensitive comparison follows Kotlin's Unicode ignoreCase rules.
     * Replacement is non-overlapping and never treats replacement as a new
     * search target, so replacing "a" with "aa" always terminates.
     *
     * Refuse extremely broad replacements instead of silently changing only
     * some occurrences.
     */
    fun replaceAll(text: String, query: String, replacement: String,
        ignoreCase: Boolean = false
    ): ReplaceAllResult {
        require(query.isNotEmpty()) { "Enter text to find" }
        val output = StringBuilder(text.length)
        var cursor = 0
        var count = 0
        while (cursor < text.length) {
            val found = text.indexOf(query, cursor, ignoreCase)
            if (found < 0) break
            if (count >= MAX_REPLACE_MATCHES) {
                throw IllegalArgumentException(
                    "Too many matches. Use a longer search phrase."
                )
            }
            output.append(text, cursor, found)
            output.append(replacement)
            cursor = found + query.length
            count++
        }
        if (count == 0) return ReplaceAllResult(text, 0)
        output.append(text, cursor, text.length)
        return ReplaceAllResult(output.toString(), count)
    }

    /** Inserts a toolbar symbol at the cursor or replaces the active selection. */
    fun insert(text: String, selectionStart: Int, selectionEnd: Int,
        insertion: String
    ): InlineInsertResult {
        require(insertion.isNotEmpty()) { "Insertion is empty" }
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(0, text.length)
        val lo = minOf(start, end)
        val hi = maxOf(start, end)
        return InlineInsertResult(
            text.substring(0, lo) + insertion + text.substring(hi),
            lo + insertion.length
        )
    }
}
