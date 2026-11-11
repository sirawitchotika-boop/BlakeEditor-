package com.blake.editor.file

import java.io.InputStream

/**
 * One limit for every entry point into the editable document UI.
 *
 * Some SAF and "Open with" providers return size 0, null, or even an
 * incorrect smaller size. Probe at most MAX_EDIT_BYTES + 1 bytes to keep
 * large inputs out of memory even when metadata is wrong.
 *
 * No reader owns an output stream. Classifying a file cannot modify it.
 */
object LargeTextOpenGate {
    const val MAX_EDIT_BYTES = 2 * 1024 * 1024
    private const val BLOCK_BYTES = 8192

    enum class Decision { EDIT, READ_ONLY_PAGES }

    /**
     * knownBytes is only an early fast-path for clearly large files.
     * Otherwise always test the bounded input, including reported small
     * files; metadata may be missing or incorrect.
     */
    fun classify(knownBytes: Long?, openInput: () -> InputStream): Decision {
        if (knownBytes != null && knownBytes > MAX_EDIT_BYTES) {
            return Decision.READ_ONLY_PAGES
        }
        return openInput().use { stream ->
            var bytes = 0L
            val buf = ByteArray(BLOCK_BYTES)
            while (bytes <= MAX_EDIT_BYTES) {
                val count = stream.read(buf, 0,
                    minOf(buf.size.toLong(), MAX_EDIT_BYTES + 1L - bytes).toInt()
                )
                if (count < 0) return@use Decision.EDIT
                if (count > 0) {
                    bytes += count
                } else {
                    // Rare non-conforming streams may return zero bytes.
                    // Ensure the check makes progress or ends at EOF.
                    if (stream.read() < 0) return@use Decision.EDIT
                    bytes++
                }
            }
            Decision.READ_ONLY_PAGES
        }
    }
}
