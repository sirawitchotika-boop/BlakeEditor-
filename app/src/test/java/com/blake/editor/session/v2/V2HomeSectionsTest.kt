package com.blake.editor.session.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V2HomeSectionsTest {
    private fun unsaved(name: String): RecoveryDraft = RecoveryDraft(
        id = DocumentIdentity.newUntitled(),
        sourceUri = null,
        displayName = name,
        language = "PLAIN_TEXT",
        content = "Local draft"
    )

    private fun saved(name: String): RecoveryDraft {
        val uri = "content://v2-home-tests/$name"
        return RecoveryDraft(
            id = DocumentIdentity.forUri(uri),
            sourceUri = uri,
            displayName = name,
            language = "PLAIN_TEXT",
            content = "Saved text",
            sourceEncoding = "UTF8",
            sourceSha256 = "a".repeat(64),
            savedToDisk = true
        )
    }

    @Test fun emptyHomeHasNoDocumentSections() {
        val sections = V2HomeSections.from(RecoveryListing(emptyList(), emptyList()))
        assertTrue(sections.continueWriting.isEmpty())
        assertTrue(sections.recentlySaved.isEmpty())
    }

    @Test fun groupsRecoveryAndSavedFilesWithoutChangingTheirRelativeOrder() {
        val entries = listOf(saved("one"), unsaved("two"), saved("three"), unsaved("four"))
        val listing = RecoveryListing(entries, listOf("unreadable.recovery"))
        val sections = V2HomeSections.from(listing)

        assertEquals(listOf("two", "four"), sections.continueWriting.map { it.displayName })
        assertEquals(listOf("one", "three"), sections.recentlySaved.map { it.displayName })
        assertEquals(entries, listing.drafts)
        assertEquals(listOf("unreadable.recovery"), listing.unreadableFiles)
    }

    @Test fun everyUnsavedDraftStaysVisibleInContinueWriting() {
        val entries = listOf(unsaved("first"), unsaved("second"))
        val sections = V2HomeSections.from(RecoveryListing(entries, emptyList()))
        assertEquals(entries, sections.continueWriting)
        assertTrue(sections.recentlySaved.isEmpty())
    }
}
