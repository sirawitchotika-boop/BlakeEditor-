package com.blake.editor.file

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Strict, lossless encoding operations for the future Blake Editor document layer.
 *
 * This is deliberately not wired into the existing file repository yet. The current
 * editor still reads UTF-8. Wiring needs a document model that stores encoding/BOM.
 */
enum class TextFileEncoding(val charsetName: String) {
    UTF8("UTF-8"),
    UTF16_LE("UTF-16LE"),
    UTF16_BE("UTF-16BE"),
    WINDOWS_874("windows-874"),
    TIS_620("TIS-620")
}

data class DecodedTextFile(
    val text: String,
    val encoding: TextFileEncoding,
    val hadBom: Boolean
)

data class EncodingPreview(
    val encoding: TextFileEncoding,
    val text: String
)

sealed class EncodingDetection {
    data class Ready(val document: DecodedTextFile) : EncodingDetection()
    data class ChooseEncoding(val previews: List<EncodingPreview>) : EncodingDetection()
    data class Unsupported(val reason: String) : EncodingDetection()
}

object TextEncodingCodec {
    private val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val utf16LeBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val utf16BeBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    /**
     * Automatically accept only unambiguous BOM encodings or strictly valid UTF-8.
     * Invalid UTF-8 is NOT automatically classified as a legacy Thai encoding;
     * Windows-874 and TIS-620 can overlap, so the user must choose.
     */
    fun detect(bytes: ByteArray): EncodingDetection {
        val bomEncoding = when {
            bytes.startsWithBytes(utf8Bom) -> TextFileEncoding.UTF8
            bytes.startsWithBytes(utf16LeBom) -> TextFileEncoding.UTF16_LE
            bytes.startsWithBytes(utf16BeBom) -> TextFileEncoding.UTF16_BE
            else -> null
        }
        if (bomEncoding != null) {
            return decodeWith(bytes, bomEncoding).fold(
                onSuccess = { EncodingDetection.Ready(it) },
                onFailure = { EncodingDetection.Unsupported("Invalid data for " + bomEncoding.charsetName) }
            )
        }

        if (bytes.any { it == 0.toByte() }) {
            return EncodingDetection.Unsupported("Possible binary data or UTF-16 without a BOM")
        }

        val utf8 = decodeWith(bytes, TextFileEncoding.UTF8)
        if (utf8.isSuccess) {
            return EncodingDetection.Ready(utf8.getOrThrow())
        }

        val previews = listOf(TextFileEncoding.WINDOWS_874, TextFileEncoding.TIS_620)
            .mapNotNull { encoding ->
                decodeWith(bytes, encoding).getOrNull()?.let {
                    EncodingPreview(encoding, it.text.take(400))
                }
            }

        return if (previews.isEmpty()) {
            EncodingDetection.Unsupported("No supported lossless text decoder")
        } else {
            EncodingDetection.ChooseEncoding(previews)
        }
    }

    /** Strict manual decode, also used after the user chooses a legacy encoding. */
    fun decodeWith(bytes: ByteArray, encoding: TextFileEncoding): Result<DecodedTextFile> =
        runCatching {
            val bom = bomFor(encoding)
            val hasBom = bytes.startsWithBytes(bom)
            val offset = if (hasBom) bom.size else 0
            val decoded = decodeStrict(bytes, offset, encoding)
            require(!decoded.contains('\u0000')) { "NUL character: possible binary data" }
            DecodedTextFile(decoded, encoding, hasBom)
        }

    /** Never replace characters that cannot be represented in the source encoding. */
    fun encode(document: DecodedTextFile, updatedText: String): Result<ByteArray> =
        runCatching {
            val encoder = Charset.forName(document.encoding.charsetName).newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val buffer = encoder.encode(CharBuffer.wrap(updatedText))
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            if (document.hadBom) bomFor(document.encoding) + bytes else bytes
        }

    private fun decodeStrict(
        bytes: ByteArray,
        offset: Int,
        encoding: TextFileEncoding
    ): String {
        val decoder = Charset.forName(encoding.charsetName).newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
    }

    private fun bomFor(encoding: TextFileEncoding): ByteArray = when (encoding) {
        TextFileEncoding.UTF8 -> utf8Bom
        TextFileEncoding.UTF16_LE -> utf16LeBom
        TextFileEncoding.UTF16_BE -> utf16BeBom
        TextFileEncoding.WINDOWS_874, TextFileEncoding.TIS_620 -> byteArrayOf()
    }

    private fun ByteArray.startsWithBytes(prefix: ByteArray): Boolean =
        prefix.isNotEmpty() && size >= prefix.size &&
            prefix.indices.all { this[it] == prefix[it] }
}
