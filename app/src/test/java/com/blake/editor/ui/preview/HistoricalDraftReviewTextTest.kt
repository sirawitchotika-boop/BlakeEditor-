package com.blake.editor.ui.preview

import com.blake.editor.session.v2.DocumentIdentity
import com.blake.editor.session.v2.HistoricalDraftDifference
import com.blake.editor.session.v2.RecoveryDraft
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalDraftReviewTextTest {
    private val id = DocumentIdentity.newUntitled()

    private fun difference(
        original: String = "Original changed text",
        v2: RecoveryDraft? = null,
        name: String = "Older note.txt"
    ) = HistoricalDraftDifference(
        id = id,
        originalSourceUri = null,
        originalDisplayName = name,
        originalContent = original,
        existingV2 = v2
    )

    @Test fun unmatchedOriginalIsLabeledAsNewCopyNotReplacement() {
        val entry = difference()
        assertTrue(HistoricalDraftReviewText.choiceLabel(entry)
            .contains("No matching V2 draft"))
        val details = HistoricalDraftReviewText.details(entry)
        assertTrue(details.contains("Original changed text"))
        assertTrue(details.contains("No matching V2 draft exists"))
        assertTrue(details.contains("FULL Original text"))
        assertTrue(details.contains("separate, unlinked recovery draft"))
        assertTrue(details.contains("not replaced"))
    }

    @Test fun differentOriginalAndV2ShowBothVersions() {
        val existing = RecoveryDraft(
            id, null, "V2 draft", "PLAIN_TEXT", "keep V2 edits", revision = 7)
        val entry = difference(v2 = existing)
        val details = HistoricalDraftReviewText.details(entry)
        assertTrue(HistoricalDraftReviewText.choiceLabel(entry)
            .contains("V2 text differs"))
        assertTrue(details.contains("ORIGINAL"))
        assertTrue(details.contains("Original changed text"))
        assertTrue(details.contains("V2"))
        assertTrue(details.contains("keep V2 edits"))
        assertTrue(details.contains("will stay unchanged"))
        assertTrue(details.contains("must be reviewed again"))
    }

    @Test fun trashedV2IsMarkedClearlyAndRemainsProtected() {
        val trashed = RecoveryDraft(
            id, null, "Trashed V2", "PLAIN_TEXT", "keep trashed text",
            revision = 9, trashedAtMs = 123L)
        val entry = difference(v2 = trashed)
        assertTrue(HistoricalDraftReviewText.choiceLabel(entry)
            .contains("V2 in Trash"))
        val details = HistoricalDraftReviewText.details(entry)
        assertTrue(details.contains("keep trashed text"))
        assertTrue(details.contains("V2 draft is in Trash"))
        assertTrue(details.contains("will remain there"))
    }

    @Test fun hugeDraftsHaveBoundedPreviewsButFullCopyNotice() {
        val hiddenSuffix = "UNSEEN_ORIGINAL_SUFFIX"
        val original = "O".repeat(4_000) + hiddenSuffix
        val existing = RecoveryDraft(
            id, null, "V2", "PLAIN_TEXT", "V".repeat(4_000) + "UNSEEN_V2_SUFFIX")
        val details = HistoricalDraftReviewText.details(difference(original, existing))
        assertFalse(details.contains(hiddenSuffix))
        assertFalse(details.contains("UNSEEN_V2_SUFFIX"))
        assertTrue(details.contains("Preview cut at 3000 of"))
        assertTrue(details.contains("FULL Original text"))
        assertTrue(details.length < 7_500)
    }

    @Test fun optionLabelBoundsUnusuallyLongOriginalNames() {
        val entry = difference(name = "X".repeat(4_000))
        val label = HistoricalDraftReviewText.choiceLabel(entry)
        assertTrue(label.length < 140)
        assertFalse(label.contains("X".repeat(100)))
    }

    @Test fun linkedUriIsNeverPrintedInPlainTextReview() {
        val secretUri = "content://private.example/document/very-private-reference"
        val entry = HistoricalDraftDifference(
            id = DocumentIdentity.forUri(secretUri),
            originalSourceUri = secretUri,
            originalDisplayName = "linked.txt",
            originalContent = "linked edits",
            existingV2 = null
        )
        val details = HistoricalDraftReviewText.details(entry)
        val label = HistoricalDraftReviewText.choiceLabel(entry)
        assertFalse(details.contains(secretUri))
        assertFalse(label.contains(secretUri))
        assertTrue(details.contains("separate, unlinked recovery draft"))
    }
}
