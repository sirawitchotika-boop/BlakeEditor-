package com.blake.editor.file

import java.nio.charset.Charset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalDocumentEncodingPolicyTest {
    @Test fun utf8ThaiAndEmojiRoundTripWithoutBom() {
        val source = "Hello สวัสดี 🌏\r\n".toByteArray(Charsets.UTF_8)
        val (text, format) = OriginalDocumentEncodingPolicy.decodeForOriginal(source).getOrThrow()
        assertEquals(TextFileEncoding.UTF8, format.encoding)
        assertFalse(format.hadBom)
        assertArrayEquals(source, OriginalDocumentEncodingPolicy.encodeForSaving(text, format).getOrThrow())
    }

    @Test fun utf8BomStaysOnEditedSaveAndSaveAs() {
        val original = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "A".toByteArray(Charsets.UTF_8)
        val (_, format) = OriginalDocumentEncodingPolicy.decodeForOriginal(original).getOrThrow()
        assertTrue(format.hadBom)
        val updated = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "B".toByteArray(Charsets.UTF_8)
        assertArrayEquals(updated, OriginalDocumentEncodingPolicy.encodeForSaving("B", format).getOrThrow())
    }

    @Test fun utf16LeBomPreservedAfterEdit() {
        val old = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "Hi สวัสดี".toByteArray(Charsets.UTF_16LE)
        val (text, format) = OriginalDocumentEncodingPolicy.decodeForOriginal(old).getOrThrow()
        assertEquals("Hi สวัสดี", text)
        assertEquals(TextFileEncoding.UTF16_LE, format.encoding)
        assertTrue(format.hadBom)
        val newBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "Updated ไทย".toByteArray(Charsets.UTF_16LE)
        assertArrayEquals(newBytes, OriginalDocumentEncodingPolicy.encodeForSaving("Updated ไทย", format).getOrThrow())
    }

    @Test fun utf16BeBomPreservedAfterEdit() {
        val old = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            "abc".toByteArray(Charsets.UTF_16BE)
        val (_, format) = OriginalDocumentEncodingPolicy.decodeForOriginal(old).getOrThrow()
        assertEquals(TextFileEncoding.UTF16_BE, format.encoding)
        assertArrayEquals(byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            "XYZ".toByteArray(Charsets.UTF_16BE),
            OriginalDocumentEncodingPolicy.encodeForSaving("XYZ", format).getOrThrow())
    }

    @Test fun legacyThaiWithoutChoiceIsRejectedRatherThanCorrupted() {
        val legacy = "สวัสดี".toByteArray(Charset.forName("windows-874"))
        val result = OriginalDocumentEncodingPolicy.decodeForOriginal(legacy)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("encoding choice") == true)
    }

    @Test fun malformedUtf8IsRejectedRatherThanUsingReplacementCharacter() {
        val bad = byteArrayOf(0xC3.toByte(), 0x28)
        assertTrue(OriginalDocumentEncodingPolicy.decodeForOriginal(bad).isFailure)
    }

    @Test fun binaryOrBomlessUtf16IsRejected() {
        assertTrue(OriginalDocumentEncodingPolicy.decodeForOriginal(byteArrayOf(0, 65, 0, 66)).isFailure)
    }

    @Test fun defaultNewDocumentIsUtf8WithoutBom() {
        val text = "สวัสดี 🌏"
        assertArrayEquals(text.toByteArray(Charsets.UTF_8),
            OriginalDocumentEncodingPolicy.encodeForSaving(text, OriginalDocumentEncodingPolicy.utf8).getOrThrow())
    }

    @Test fun unrepresentableLegacyCharactersFailClosed() {
        val fmt = OriginalDocumentFormat(TextFileEncoding.WINDOWS_874, hadBom = false)
        assertTrue(OriginalDocumentEncodingPolicy.encodeForSaving("hello 😄", fmt).isFailure)
    }

    @Test fun encodedBytesOverTwoMiBAreRejectedBeforeProviderWrite() {
        val tooBig = "x".repeat(LargeTextOpenGate.MAX_EDIT_BYTES + 1)
        assertTrue(OriginalDocumentEncodingPolicy.encodeForSaving(tooBig,
            OriginalDocumentEncodingPolicy.utf8).isFailure)
    }
}
