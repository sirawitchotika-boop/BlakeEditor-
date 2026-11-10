package com.blake.editor.file

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.math.min

/** An immutable page of original text, displayed without editing the source file. */
data class TextPage(
    val content: String,
    val startChar: Long,
    val nextChar: Long,
    val hasMore: Boolean
)

/**
 * Reads one bounded page from an Android document provider stream.
 * The source is reopened for each page, so no whole-file allocation is needed.
 * Non-seekable providers may require rescanning the prefix for distant pages.
 */
object PagedTextReader {
    const val DEFAULT_PAGE_CHARS = 16_000
    const val MAX_PAGE_CHARS = 32_000

    fun sniffBom(input: InputStream): TextFileEncoding? =
        input.use {
            val prefix = ByteArray(3)
            val n = readPrefix(it, prefix)
            detectBom(prefix, n)
        }

    fun readPage(
        input: InputStream,
        encoding: TextFileEncoding,
        startChar: Long,
        pageChars: Int = DEFAULT_PAGE_CHARS
    ): TextPage {
        require(startChar >= 0) { "Page offset must be non-negative" }
        require(pageChars in 1..MAX_PAGE_CHARS) { "Invalid page size" }

        val stream = PushbackInputStream(input, 3)
        val prefix = ByteArray(3)
        val read = readPrefix(stream, prefix)
        val bomKind = detectBom(prefix, read)
        if (bomKind != null && bomKind != encoding) {
            stream.close()
            error("File BOM requires " + bomKind.charsetName + "; choose the matching encoding")
        }
        val bomLength = when (bomKind) {
            TextFileEncoding.UTF8 -> 3
            TextFileEncoding.UTF16_LE, TextFileEncoding.UTF16_BE -> 2
            else -> 0
        }
        if (read > bomLength) stream.unread(prefix, bomLength, read - bomLength)

        val decoder = Charset.forName(encoding.charsetName).newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        return BufferedReader(InputStreamReader(stream, decoder), 8192).use { reader ->
            var remaining = startChar
            val scratch = CharArray(2048)
            while (remaining > 0) {
                val n = reader.read(scratch, 0, min(remaining, scratch.size.toLong()).toInt())
                if (n < 0) return@use TextPage("", startChar, startChar, false)
                if (n > 0) remaining -= n
            }
            val text = StringBuilder(pageChars + 2)
            val chars = CharArray(2048)
            while (text.length < pageChars) {
                val n = reader.read(chars, 0, min(chars.size, pageChars - text.length))
                if (n < 0) break
                if (n > 0) text.append(chars, 0, n)
            }
            if (text.isNotEmpty() && text.last().isHighSurrogate()) {
                val low = reader.read()
                require(low >= 0 && low.toChar().isLowSurrogate()) {
                    "Invalid or incomplete Unicode surrogate pair"
                }
                text.append(low.toChar())
            }
            val content = text.toString()
            require(!content.contains('\u0000')) { "Possible binary file: NUL byte detected" }
            val hasMore = content.isNotEmpty() && reader.read() >= 0
            TextPage(content, startChar, startChar + content.length, hasMore)
        }
    }

    private fun detectBom(bytes: ByteArray, size: Int): TextFileEncoding? = when {
        size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> TextFileEncoding.UTF8
        size >= 2 && bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xFE.toByte() -> TextFileEncoding.UTF16_LE
        size >= 2 && bytes[0] == 0xFE.toByte() &&
            bytes[1] == 0xFF.toByte() -> TextFileEncoding.UTF16_BE
        else -> null
    }

    private fun readPrefix(input: InputStream, target: ByteArray): Int {
        var count = 0
        while (count < target.size) {
            val n = input.read(target, count, target.size - count)
            if (n < 0) break
            if (n > 0) count += n
        }
        return count
    }
}
