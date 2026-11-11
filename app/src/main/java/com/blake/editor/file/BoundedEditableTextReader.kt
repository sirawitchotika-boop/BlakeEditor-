package com.blake.editor.file

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Last-resort protection for older saved sessions and any callers that
 * bypass the Activity's size probe. Editing never reads more than 2 MiB
 * of source bytes. Large files must be opened with the paged reader.
 */
/** An oversized file must not enter an editable Android text buffer. */
class OversizedEditableFileException :
    IllegalArgumentException("File exceeds 2 MB; use Large Text Reader")

object BoundedEditableTextReader {
    fun readUtf8(input: InputStream): String {
        val result = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0L
        while (true) {
            val next = input.read(chunk)
            if (next < 0) break
            if (next == 0) {
                val single = input.read()
                if (single < 0) break
                if (total >= LargeTextOpenGate.MAX_EDIT_BYTES) {
                    throw OversizedEditableFileException()
                }
                result.write(single)
                total++
            } else {
                if (total + next > LargeTextOpenGate.MAX_EDIT_BYTES) {
                    throw OversizedEditableFileException()
                }
                result.write(chunk, 0, next)
                total += next
            }
        }
        return String(result.toByteArray(), Charsets.UTF_8)
    }
}
