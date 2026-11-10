package com.blake.editor.file

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedTextReaderTest {
    @Test fun bomlessUtf8FileOpensUsingDefaultEncoding() {
        val bytes = "Page 1: hello สวัสดีครับ\nPage 2: end".toByteArray(Charsets.UTF_8)
        assertEquals(TextFileEncoding.UTF8,
            PagedTextReader.initialEncoding(ByteArrayInputStream(bytes)))
        val first = PagedTextReader.readPage(ByteArrayInputStream(bytes),
            TextFileEncoding.UTF8, 0, 12)
        assertTrue(first.content.isNotEmpty())
        assertTrue(first.hasMore)
        val next = PagedTextReader.readPage(ByteArrayInputStream(bytes),
            TextFileEncoding.UTF8, first.nextChar, 12)
        assertTrue(next.content.isNotEmpty())
    }

    @Test fun bomlessEmptyFileUsesUtf8RatherThanReportingCannotOpen() {
        assertEquals(TextFileEncoding.UTF8,
            PagedTextReader.initialEncoding(ByteArrayInputStream(byteArrayOf())))
    }

    @Test fun markedUtf16StillUsesBomEncoding() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "Hello".toByteArray(Charsets.UTF_16LE)
        assertEquals(TextFileEncoding.UTF16_LE,
            PagedTextReader.initialEncoding(ByteArrayInputStream(bytes)))
    }

    @Test fun pagesReassembleThaiEnglishAndEmoji() {
        val original = "ABC สวัสดีครับ 🌍\r\nSecond line\nThird"
        val bytes = original.toByteArray(Charsets.UTF_8)
        val parts = mutableListOf<String>()
        var offset = 0L
        while (true) {
            val page = PagedTextReader.readPage(
                ByteArrayInputStream(bytes), TextFileEncoding.UTF8, offset, 7
            )
            parts.add(page.content)
            if (!page.hasMore) break
            offset = page.nextChar
        }
        assertEquals(original, parts.joinToString(""))
    }

    @Test fun utf8BomIsSkippedForEveryPage() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "hello".toByteArray(Charsets.UTF_8)
        assertEquals(TextFileEncoding.UTF8, PagedTextReader.sniffBom(ByteArrayInputStream(bytes)))
        val first = PagedTextReader.readPage(ByteArrayInputStream(bytes), TextFileEncoding.UTF8, 0, 3)
        assertEquals("hel", first.content)
        assertTrue(first.hasMore)
        assertEquals("lo", PagedTextReader.readPage(
            ByteArrayInputStream(bytes), TextFileEncoding.UTF8, first.nextChar, 3
        ).content)
    }

    @Test fun utf16LeBomAndStrictMismatch() {
        val text = "Thai สวัสดี 🌏"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            text.toByteArray(Charsets.UTF_16LE)
        assertEquals(TextFileEncoding.UTF16_LE, PagedTextReader.sniffBom(ByteArrayInputStream(bytes)))
        assertEquals(text, PagedTextReader.readPage(
            ByteArrayInputStream(bytes), TextFileEncoding.UTF16_LE, 0
        ).content)
        assertTrue(runCatching {
            PagedTextReader.readPage(ByteArrayInputStream(bytes), TextFileEncoding.UTF8, 0)
        }.isFailure)
    }

    @Test fun chosenThaiLegacyEncodingDisplaysProperly() {
        val text = "สวัสดีครับ\r\nทดสอบ"
        val bytes = text.toByteArray(Charset.forName("windows-874"))
        assertEquals(text, PagedTextReader.readPage(ByteArrayInputStream(bytes),
            TextFileEncoding.WINDOWS_874, 0).content)
    }

    @Test fun invalidUtf8AndBinaryAreRejected() {
        assertTrue(runCatching {
            PagedTextReader.readPage(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)),
                TextFileEncoding.UTF8, 0)
        }.isFailure)
        assertTrue(runCatching {
            PagedTextReader.readPage(ByteArrayInputStream(byteArrayOf(65, 0, 66)),
                TextFileEncoding.UTF8, 0)
        }.isFailure)
    }

    @Test fun pageIsBoundedEvenForVeryLargeText() {
        val bytes = "a".repeat(2_000_000).toByteArray()
        var readBytes = 0L
        val stream = object : FilterInputStream(ByteArrayInputStream(bytes)) {
            override fun read(): Int {
                val b = super.read()
                if (b >= 0) readBytes++
                return b
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = super.read(b, off, len)
                if (n > 0) readBytes += n
                return n
            }
        }
        val first = PagedTextReader.readPage(stream, TextFileEncoding.UTF8, 0, 4_096)
        assertEquals(4_096, first.content.length)
        assertTrue(first.hasMore)
        assertTrue("Page reader loaded too much data", readBytes < 100_000)
    }

    @Test fun offsetAllowsNextAndPreviousPages() {
        val bytes = "123456789ABCDE".toByteArray()
        val first = PagedTextReader.readPage(ByteArrayInputStream(bytes), TextFileEncoding.UTF8, 0, 5)
        val second = PagedTextReader.readPage(ByteArrayInputStream(bytes),
            TextFileEncoding.UTF8, first.nextChar, 5)
        assertEquals("12345", first.content)
        assertEquals("6789A", second.content)
        assertEquals(first, PagedTextReader.readPage(
            ByteArrayInputStream(bytes), TextFileEncoding.UTF8, 0, 5
        ))
    }

    @Test fun emptyInputAndOffsetBeyondEndAreSafe() {
        val empty = PagedTextReader.readPage(ByteArrayInputStream(byteArrayOf()),
            TextFileEncoding.UTF8, 0)
        assertEquals("", empty.content)
        assertFalse(empty.hasMore)
        assertFalse(PagedTextReader.readPage(ByteArrayInputStream("abc".toByteArray()),
            TextFileEncoding.UTF8, 99).hasMore)
    }

    @Test fun surrogatePairDoesNotSplitAcrossPages() {
        val bytes = "Hello 🌏 end".toByteArray(Charsets.UTF_8)
        val first = PagedTextReader.readPage(ByteArrayInputStream(bytes),
            TextFileEncoding.UTF8, 0, 7)
        assertEquals("Hello 🌏", first.content)
        assertEquals(8L, first.nextChar)
        val second = PagedTextReader.readPage(ByteArrayInputStream(bytes),
            TextFileEncoding.UTF8, first.nextChar, 10)
        assertEquals(" end", second.content)
    }
}
