package com.blake.editor.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameUtilsTest {

    @Test
    fun testGetExtension() {
        assertEquals("txt", FileNameUtils.getExtension("notes.txt"))
        assertEquals("kt", FileNameUtils.getExtension("Main.kt"))
        assertEquals("markdown", FileNameUtils.getExtension("doc.MARKDOWN"))
        assertEquals("", FileNameUtils.getExtension("README"))
        assertEquals("", FileNameUtils.getExtension(".gitignore"))
        assertEquals("gz", FileNameUtils.getExtension("archive.tar.gz"))
    }

    @Test
    fun testSanitizeFileName() {
        assertEquals("valid_name.txt", FileNameUtils.sanitizeFileName("valid_name.txt"))
        assertEquals("invalid_name.txt", FileNameUtils.sanitizeFileName("invalid/name.txt"))
        assertEquals("all_bad_.txt", FileNameUtils.sanitizeFileName("all?bad*.txt"))
        assertEquals("untitled.txt", FileNameUtils.sanitizeFileName("   "))
    }
}
