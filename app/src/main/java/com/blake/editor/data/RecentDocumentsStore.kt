package com.blake.editor.data

import com.blake.editor.model.RecentFile
import com.blake.editor.session.AtomicPrivateFileWriter
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/**
 * Persists recently opened external files. Removing an entry only hides
 * the shortcut in Blake and never modifies or deletes the underlying file.
 */
class RecentDocumentsStore(private val storageFile: File) {

    companion object {
        private const val MAGIC = 0x424C5231 // "BLR1"
        private const val FORMAT_VERSION = 1
        private const val MAX_RECENTS = 25
        private const val MAX_FIELD_BYTES = 64 * 1024
    }

    @Synchronized
    fun load(): List<RecentFile> {
        if (!storageFile.exists() || !storageFile.isFile) return emptyList()
        return runCatching {
            DataInputStream(BufferedInputStream(storageFile.inputStream())).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) {
                    throw IOException("Unsupported recents format")
                }
                val count = input.readInt().coerceIn(0, MAX_RECENTS)
                val items = ArrayList<RecentFile>(count)
                repeat(count) {
                    val uri = readString(input)
                    val title = readString(input)
                    val mime = readString(input)
                    val openedAt = input.readLong()
                    items.add(RecentFile(uri, title, mime, openedAt))
                }
                items
            }
        }.getOrElse { emptyList() }
    }

    @Synchronized
    fun record(entry: RecentFile): List<RecentFile> {
        val current = load().filterNot { it.uriString == entry.uriString }
        val updated = (listOf(entry) + current)
            .sortedByDescending { it.openedAtMs }
            .take(MAX_RECENTS)
        persist(updated)
        return updated
    }

    @Synchronized
    fun remove(uriString: String): List<RecentFile> {
        val updated = load().filterNot { it.uriString == uriString }
        persist(updated)
        return updated
    }

    private fun persist(items: List<RecentFile>) {
        AtomicPrivateFileWriter.write(storageFile) { fileOut ->
            val out = DataOutputStream(BufferedOutputStream(fileOut))
            out.writeInt(MAGIC)
            out.writeInt(FORMAT_VERSION)
            out.writeInt(items.size)
            for (item in items) {
                writeString(out, item.uriString)
                writeString(out, item.title)
                writeString(out, item.mimeType)
                out.writeLong(item.openedAtMs)
            }
            out.flush()
        }
    }

    private fun writeString(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val len = input.readInt()
        if (len < 0 || len > MAX_FIELD_BYTES) throw IOException("Invalid field length")
        val bytes = ByteArray(len)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}
