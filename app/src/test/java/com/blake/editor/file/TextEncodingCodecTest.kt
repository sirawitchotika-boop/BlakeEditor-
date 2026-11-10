package com.blake.editor.file

import java.nio.charset.Charset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEncodingCodecTest {

    @Test fun utf8RoundTripPreservesThaiEmojiAndCrLf() {
        val original = "สวัสดีครับ 🌏\r\nSecond line\r\n".toByteArray(Charsets.UTF_8)
        val decoded = (TextEncodingCodec.detect(original) as EncodingDetection.Ready).document
        assertEquals(TextFileEncoding.UTF8, decoded.encoding)
        assertFalse(decoded.hadBom)
        assertArrayEquals(original, TextEncodingCodec.encode(decoded, decoded.text).getOrThrow())
    }

    @Test fun utf8BomIsPreserved() {
        val original = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "สวัสดี".toByteArray(Charsets.UTF_8)
        val decoded = (TextEncodingCodec.detect(original) as EncodingDetection.Ready).document
        assertTrue(decoded.hadBom)
        assertArrayEquals(original, TextEncodingCodec.encode(decoded, decoded.text).getOrThrow())
    }

    @Test fun utf16LeBomRoundTrip() {
        val original = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "สวัสดี\r\n".toByteArray(Charsets.UTF_16LE)
        val decoded = (TextEncodingCodec.detect(original) as EncodingDetection.Ready).document
        assertEquals(TextFileEncoding.UTF16_LE, decoded.encoding)
        assertArrayEquals(original, TextEncodingCodec.encode(decoded, decoded.text).getOrThrow())
    }

    @Test fun utf16BeBomRoundTrip() {
        val original = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            "Hello".toByteArray(Charsets.UTF_16BE)
        val decoded = (TextEncodingCodec.detect(original) as EncodingDetection.Ready).document
        assertEquals(TextFileEncoding.UTF16_BE, decoded.encoding)
        assertArrayEquals(original, TextEncodingCodec.encode(decoded, decoded.text).getOrThrow())
    }

    @Test fun legacyThaiRequiresExplicitSelection() {
        val original = "สวัสดีครับ".toByteArray(Charset.forName("windows-874"))
        val detection = TextEncodingCodec.detect(original)
        assertTrue(detection is EncodingDetection.ChooseEncoding)
        val selected = TextEncodingCodec.decodeWith(original, TextFileEncoding.WINDOWS_874)
            .getOrThrow()
        assertEquals("สวัสดีครับ", selected.text)
        assertArrayEquals(original, TextEncodingCodec.encode(selected, selected.text).getOrThrow())
    }

    @Test fun unrepresentableEmojiIsRejectedInsteadOfReplaced() {
        val original = "สวัสดี".toByteArray(Charset.forName("windows-874"))
        val decoded = TextEncodingCodec.decodeWith(original, TextFileEncoding.WINDOWS_874)
            .getOrThrow()
        assertTrue(TextEncodingCodec.encode(decoded, "สวัสดี 👩‍💻").isFailure)
    }

    @Test fun malformedUtf8NeverProducesReplacementCharacters() {
        val badBytes = byteArrayOf(0xC3.toByte(), 0x28)
        assertTrue(TextEncodingCodec.decodeWith(badBytes, TextFileEncoding.UTF8).isFailure)
        assertFalse(TextEncodingCodec.detect(badBytes) is EncodingDetection.Ready)
    }

    @Test fun likelyBinaryDataIsNotAcceptedAsText() {
        val bytes = byteArrayOf(0x00, 0x01, 0x02)
        assertTrue(TextEncodingCodec.detect(bytes) is EncodingDetection.Unsupported)
    }

    @Test fun unchangedCrLineEndingsRemainByteIdentical() {
        val original = "a\rb\rc".toByteArray(Charsets.UTF_8)
        val decoded = (TextEncodingCodec.detect(original) as EncodingDetection.Ready).document
        assertArrayEquals(original, TextEncodingCodec.encode(decoded, decoded.text).getOrThrow())
    }
}
