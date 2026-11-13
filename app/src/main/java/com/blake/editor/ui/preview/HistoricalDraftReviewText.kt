package com.blake.editor.ui.preview

import com.blake.editor.session.v2.HistoricalDraftDifference

/**
 * Plain-text presentation for an explicitly opened Original/V2 review.
 * Never include URI values, file-provider details or private content in logs.
 * Large drafts are previewed, not loaded into an unbounded Android dialog.
 */
internal object HistoricalDraftReviewText {
    private const val PREVIEW_CHARS = 3_000

    fun choiceLabel(diff: HistoricalDraftDifference): String {
        val state = when {
            diff.existingV2 == null -> "No matching V2 draft"
            diff.existingV2.trashedAtMs != null -> "V2 in Trash"
            else -> "V2 text differs"
        }
        return diff.originalDisplayName.take(80) + " · " + state
    }

    fun details(diff: HistoricalDraftDifference): String {
        val v2 = diff.existingV2
        val v2State = when {
            v2 == null -> "No matching V2 draft exists"
            v2.trashedAtMs != null -> "V2 draft is in Trash and will remain there"
            else -> "V2 draft will stay unchanged"
        }
        return buildString {
            append("Review both versions. Neither one is automatically chosen.\n\n")
            append("ORIGINAL — " + diff.originalDisplayName + "\n")
            append(preview(diff.originalContent))
            append("\n\nV2 — " + v2State + "\n")
            if (v2 == null) {
                append("(no V2 text)\n")
            } else {
                append(preview(v2.content))
            }
            append("\n\nKeeping Original makes a separate, unlinked recovery draft ")
            append("containing the FULL Original text. Existing V2 drafts, Trash, ")
            append("Original drafts and external files are not replaced. ")
            append("A changed version must be reviewed again.")
        }
    }

    private fun preview(content: String): String {
        val shown = content.take(PREVIEW_CHARS)
        return if (content.length > PREVIEW_CHARS) {
            "$shown\n[Preview cut at $PREVIEW_CHARS of " + content.length + " characters]"
        } else {
            shown
        }
    }
}
