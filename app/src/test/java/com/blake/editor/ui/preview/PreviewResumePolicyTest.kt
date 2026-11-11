package com.blake.editor.ui.preview

import com.blake.editor.session.v2.DocumentIdentity
import com.blake.editor.session.v2.RecoveryDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewResumePolicyTest {
    private val id = DocumentIdentity.newUntitled()
    private fun draft(text: String, revision: Long) = RecoveryDraft(
        id = id, sourceUri = null, displayName = "draft.txt",
        language = "PLAIN_TEXT", content = text, revision = revision)

    @Test fun pendingThaiAndEmojiEditsSurviveRecreation() {
        val before = draft("old", 3)
        val edited = before.copy(content = "สวัสดีครับ 🌏", revision = 4,
            selectionStart = 8, selectionEnd = 8, scrollY = 42)
        val pending = PreviewResumePolicy.capture(edited)
        val restored = PreviewResumePolicy.restore(before, pending)!!
        assertEquals(edited.content, restored.content)
        assertEquals(4L, restored.revision)
        assertEquals(8, restored.selectionStart)
        assertEquals(42, restored.scrollY)
        assertFalse(restored.savedToDisk)
    }

    @Test fun neverOverwriteEqualOrNewerDiskRevision() {
        val last = draft("fresh", 11)
        val older = PreviewResumePolicy.capture(draft("old", 10))
        assertNull(PreviewResumePolicy.restore(last, older))
        assertNull(PreviewResumePolicy.restore(last, PreviewResumePolicy.capture(last)))
    }

    @Test fun neverApplySnapshotFromAnotherFile() {
        val wrong = draft("wrong", 5).copy(id = DocumentIdentity.newUntitled())
        assertNull(PreviewResumePolicy.restore(draft("right", 4),
            PreviewResumePolicy.capture(wrong)))
    }

    @Test fun longContentsAreNotPutInAndroidBundle() {
        assertNull(PreviewResumePolicy.capture(draft("x".repeat(40_000), 3)))
        assertTrue(PreviewResumePolicy.capture(draft("🌏".repeat(100), 4)) != null)
    }

    @Test fun corruptedSelectionAndScrollStayWithinBounds() {
        val pending = ResumeSnapshot(id.value, "text", -20, 9_999, -5, 4)
        val restored = PreviewResumePolicy.restore(draft("old", 3), pending)!!
        assertEquals(0, restored.selectionStart)
        assertEquals(4, restored.selectionEnd)
        assertEquals(0, restored.scrollY)
    }

    @Test fun savedDiskFlagIsLostWhenRestoredTextChanges() {
        val fileId = DocumentIdentity.forUri("content://documents/doc/1")
        val saved = RecoveryDraft(fileId, "content://documents/doc/1",
            "saved.txt", "PLAIN_TEXT", "before", revision = 6,
            sourceEncoding = "UTF8", sourceSha256 = "a".repeat(64), savedToDisk = true)
        val pending = ResumeSnapshot(fileId.value, "after", 5, 5, 0, 7)
        val restored = PreviewResumePolicy.restore(saved, pending)!!
        assertFalse(restored.savedToDisk)
    }
}
