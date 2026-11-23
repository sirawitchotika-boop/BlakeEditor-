package com.blake.editor.markdown

enum class MarkdownAction {
    HEADING,
    BOLD,
    ITALIC,
    BULLET_LIST,
    CHECKLIST,
    QUOTE,
    LINK
}

data class FormatEditResult(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int
)

/**
 * Pure-Kotlin formatting actions for the hideable Markdown quick-bar.
 * Wraps active selections or inserts clean markers at the cursor.
 */
object MarkdownFormatActions {

    fun apply(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        action: MarkdownAction
    ): FormatEditResult {
        val lo = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val hi = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        return when (action) {
            MarkdownAction.HEADING -> prefixLine(text, lo, hi, "# ")
            MarkdownAction.BULLET_LIST -> prefixLine(text, lo, hi, "- ")
            MarkdownAction.CHECKLIST -> prefixLine(text, lo, hi, "- [ ] ")
            MarkdownAction.QUOTE -> prefixLine(text, lo, hi, "> ")
            MarkdownAction.BOLD -> wrapSelection(text, lo, hi, "**", "**")
            MarkdownAction.ITALIC -> wrapSelection(text, lo, hi, "*", "*")
            MarkdownAction.LINK -> insertLink(text, lo, hi)
        }
    }

    private fun prefixLine(text: String, lo: Int, hi: Int, prefix: String): FormatEditResult {
        val lineStart = text.lastIndexOf('\n', (lo - 1).coerceAtLeast(0))
            .let { if (it < 0 || lo == 0) 0 else it + 1 }
        // If heading is pressed on a line that already starts with '#', cycle up to ###
        if (prefix == "# " && text.startsWith("### ", lineStart)) {
            val updated = text.removeRange(lineStart, lineStart + 4)
            val delta = -4
            return FormatEditResult(
                text = updated,
                selectionStart = (lo + delta).coerceAtLeast(lineStart),
                selectionEnd = (hi + delta).coerceAtLeast(lineStart)
            )
        }
        if (prefix == "# " && (text.startsWith("## ", lineStart) || text.startsWith("# ", lineStart))) {
            val updated = text.substring(0, lineStart) + "#" + text.substring(lineStart)
            return FormatEditResult(
                text = updated,
                selectionStart = lo + 1,
                selectionEnd = hi + 1
            )
        }
        val updated = text.substring(0, lineStart) + prefix + text.substring(lineStart)
        return FormatEditResult(
            text = updated,
            selectionStart = lo + prefix.length,
            selectionEnd = hi + prefix.length
        )
    }

    private fun wrapSelection(
        text: String,
        lo: Int,
        hi: Int,
        before: String,
        after: String
    ): FormatEditResult {
        val selected = text.substring(lo, hi)
        val updated = text.substring(0, lo) + before + selected + after + text.substring(hi)
        return if (lo == hi) {
            val cursor = lo + before.length
            FormatEditResult(updated, cursor, cursor)
        } else {
            FormatEditResult(updated, lo + before.length, hi + before.length)
        }
    }

    private fun insertLink(text: String, lo: Int, hi: Int): FormatEditResult {
        val selected = text.substring(lo, hi)
        return if (selected.isEmpty()) {
            val snippet = "[](https://)"
            val updated = text.substring(0, lo) + snippet + text.substring(hi)
            FormatEditResult(updated, lo + 1, lo + 1)
        } else {
            val snippet = "[$selected](https://)"
            val updated = text.substring(0, lo) + snippet + text.substring(hi)
            val urlStart = lo + selected.length + 3
            val urlEnd = urlStart + "https://".length
            FormatEditResult(updated, urlStart, urlEnd)
        }
    }
}
