package com.blake.editor.data

import com.blake.editor.model.ScratchDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DraftRecoveryStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun roundTripsLargeThaiAndUnicodeDraftsAtomically() {
        val store = DraftRecoveryStore(File(tempFolder.root, "blake_v1/drafts.bin"))
        val bigThaiText = "สวัสดีครับ บันทึกข้อความภาษาไทย ✨\n".repeat(3000) // > 64 KiB UTF-8
        val draft = ScratchDraft(
            id = "d-1",
            title = "บันทึก.md",
            uriString = "content://provider/doc/1",
            mimeType = "text/markdown",
            content = bigThaiText,
            isDirty = true,
            updatedAtMs = 123456789L
        )

        store.upsert(draft, setActive = true)
        val loaded = store.load()

        assertEquals("d-1", loaded.activeDraftId)
        assertEquals(1, loaded.drafts.size)
        assertEquals(draft, loaded.drafts.first())
    }

    @Test
    fun removeUpdatesActiveFallbackAndHandlesCorruptFileSafely() {
        val file = File(tempFolder.root, "drafts.bin")
        val store = DraftRecoveryStore(file)
        store.upsert(ScratchDraft("a", "A.md", null, "text/markdown", "one", true, 100L), true)
        store.upsert(ScratchDraft("b", "B.md", null, "text/markdown", "two", false, 200L), true)

        val afterRemove = store.remove("b")
        assertEquals("a", afterRemove.activeDraftId)
        assertEquals(1, afterRemove.drafts.size)

        file.writeBytes(byteArrayOf(1, 2, 3))
        val recovered = store.load()
        assertNull(recovered.activeDraftId)
        assertTrue(recovered.drafts.isEmpty())
    }
}
