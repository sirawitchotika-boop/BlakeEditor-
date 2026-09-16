package com.blake.editor.file

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedFileTypesTest {

    @Test
    fun testSupportedExtensions() {
        assertTrue(SupportedFileTypes.isSupportedExtension("txt"))
        assertTrue(SupportedFileTypes.isSupportedExtension("TXT"))
        assertTrue(SupportedFileTypes.isSupportedExtension("md"))
        assertTrue(SupportedFileTypes.isSupportedExtension("kt"))
        assertTrue(SupportedFileTypes.isSupportedExtension("json"))
        assertFalse(SupportedFileTypes.isSupportedExtension("pdf"))
        assertFalse(SupportedFileTypes.isSupportedExtension("exe"))
        assertFalse(SupportedFileTypes.isSupportedExtension("mp4"))
    }
}
