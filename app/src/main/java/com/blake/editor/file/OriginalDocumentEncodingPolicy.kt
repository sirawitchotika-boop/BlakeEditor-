package com.blake.editor.file

/**
 * Strict Original editor file decoding and exact-encoding writes. No silent
 * replacement characters and no guessing between legacy Thai code pages.
 */
internal data class OriginalDocumentFormat(
    val encoding: TextFileEncoding,
    val hadBom: Boolean
)

internal data class VerifiedOriginalOpenSnapshot(
    val fingerprint: String,
    val format: OriginalDocumentFormat
)

internal object OriginalDocumentEncodingPolicy {
    val utf8 = OriginalDocumentFormat(TextFileEncoding.UTF8, hadBom = false)

    fun decodeForOriginal(bytes: ByteArray): Result<Pair<String, OriginalDocumentFormat>> =
        runCatching {
            when (val detection = TextEncodingCodec.detect(bytes)) {
                is EncodingDetection.Ready -> {
                    val document = detection.document
                    document.text to OriginalDocumentFormat(document.encoding, document.hadBom)
                }
                is EncodingDetection.ChooseEncoding -> throw IllegalArgumentException(
                    "This file needs an explicit text encoding choice (such as Windows-874 " +
                        "or TIS-620). Original left it unchanged; do not Save over it."
                )
                is EncodingDetection.Unsupported -> throw IllegalArgumentException(
                    "Original cannot safely decode this file: ${detection.reason}. " +
                        "The source was left unchanged."
                )
            }
        }

    fun encodeForSaving(text: String, format: OriginalDocumentFormat): Result<ByteArray> =
        runCatching {
            val reference = DecodedTextFile("", format.encoding, format.hadBom)
            val encoded = TextEncodingCodec.encode(reference, text).getOrThrow()
            require(encoded.size <= LargeTextOpenGate.MAX_EDIT_BYTES) {
                "Encoded file exceeds 2 MiB. Original refuses to overwrite it."
            }
            encoded
        }
}
