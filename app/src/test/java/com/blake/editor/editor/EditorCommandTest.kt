package com.blake.editor.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorCommandTest {

    @Test
    fun testShowPreviewCommand() {
        val md = "# Heading\nSome text"
        val cmd = EditorCommand.ShowPreview(md)
        assertEquals(md, cmd.markdownText)
    }

    @Test
    fun testHidePreviewCommand() {
        val cmd = EditorCommand.HidePreview
        assertTrue(cmd is EditorCommand.HidePreview)
    }
}
