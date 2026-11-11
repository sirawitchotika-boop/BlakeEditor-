package com.blake.editor.session.v2

/**
 * Read-only Home grouping shared by the V2 preview and future regular UI.
 *
 * Keep the recovery store's existing order within each section. Grouping must
 * never save, delete, or rewrite drafts; unreadable records remain in the
 * original RecoveryListing for the caller to report separately.
 */
data class V2HomeSections(
    val continueWriting: List<RecoveryDraft>,
    val recentlySaved: List<RecoveryDraft>
) {
    companion object {
        fun from(listing: RecoveryListing): V2HomeSections = V2HomeSections(
            continueWriting = listing.drafts.filterNot { it.savedToDisk },
            recentlySaved = listing.drafts.filter { it.savedToDisk }
        )
    }
}
