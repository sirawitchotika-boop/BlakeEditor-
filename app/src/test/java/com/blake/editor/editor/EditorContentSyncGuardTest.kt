package com.blake.editor.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorContentSyncGuardTest {
    @Test fun initialDocumentIsLoadedOnce() {
        val guard = EditorContentSyncGuard()
        assertTrue(guard.shouldApplyNativeContent("Existing document"))
        assertFalse(guard.shouldApplyNativeContent("Existing document"))
    }

    @Test fun userTypingDoesNotTriggerEchoSetValue() {
        val guard = EditorContentSyncGuard()
        guard.shouldApplyNativeContent("hello")
        guard.onWebEditorChanged("hello w")
        assertFalse(guard.shouldApplyNativeContent("hello w"))
        guard.onWebEditorChanged("hello wo")
        assertFalse(guard.shouldApplyNativeContent("hello wo"))
        guard.onWebEditorChanged("hello w")
        assertFalse(guard.shouldApplyNativeContent("hello w"))
        guard.onWebEditorChanged("hello")
        assertFalse(guard.shouldApplyNativeContent("hello"))
    }

    @Test fun thaiEmojiAndDeletionAreNeverEchoedBack() {
        val guard = EditorContentSyncGuard()
        guard.shouldApplyNativeContent("")
        for (text in listOf("ส", "สว", "สวั", "สวัสดี", "สวัสดี 🌏",
                "สวัสดี ", "สวัสดี", "สวั", "")) {
            guard.onWebEditorChanged(text)
            assertFalse(guard.shouldApplyNativeContent(text))
        }
    }

    @Test fun externalOpenAndNewDocumentAreStillSent() {
        val guard = EditorContentSyncGuard()
        guard.onWebEditorChanged("old draft")
        assertTrue(guard.shouldApplyNativeContent("another document"))
        assertFalse(guard.shouldApplyNativeContent("another document"))
        assertTrue(guard.shouldApplyNativeContent(""))
        assertFalse(guard.shouldApplyNativeContent(""))
    }

    @Test fun cursorOnlyUpdatesCannotResetContent() {
        val guard = EditorContentSyncGuard()
        guard.shouldApplyNativeContent("first line\nsecond line\n")
        assertFalse(guard.shouldApplyNativeContent("first line\nsecond line\n"))
        assertFalse(guard.shouldApplyNativeContent("first line\nsecond line\n"))
    }
}
