package com.blake.editor.file

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedFileTypesTest {

    @Test
    fun acceptsTextAndMarkdownExtensionsOnly() {
        assertTrue(SupportedFileTypes.isSupportedExtension("txt"))
        assertTrue(SupportedFileTypes.isSupportedExtension("MD"))
        assertTrue(SupportedFileTypes.isSupportedExtension("markdown"))
        assertTrue(SupportedFileTypes.isSupportedExtension("log"))

        assertFalse(SupportedFileTypes.isSupportedExtension("kt"))
        assertFalse(SupportedFileTypes.isSupportedExtension("java"))
        assertFalse(SupportedFileTypes.isSupportedExtension("js"))
        assertFalse(SupportedFileTypes.isSupportedExtension("py"))
        assertFalse(SupportedFileTypes.isSupportedExtension("json"))
    }

    @Test
    fun detectsMarkdownByExtensionOrMime() {
        assertTrue(SupportedFileTypes.isMarkdownFile("notes.md"))
        assertTrue(SupportedFileTypes.isMarkdownFile("README.MARKDOWN"))
        assertTrue(SupportedFileTypes.isMarkdownFile("note", "text/markdown"))
        assertFalse(SupportedFileTypes.isMarkdownFile("plain.txt", "text/plain"))
    }
}
