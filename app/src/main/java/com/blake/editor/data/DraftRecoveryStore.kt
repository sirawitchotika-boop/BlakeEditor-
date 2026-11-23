package com.blake.editor.data

import com.blake.editor.model.ScratchDraft
import com.blake.editor.session.AtomicPrivateFileWriter
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

data class RecoveryWorkspaceState(
    val activeDraftId: String?,
    val drafts: List<ScratchDraft>
)

/**
 * Atomic, crash-safe storage for active document recovery and local scratch drafts.
 * Stored under filesDir/blake_v1/ so Blake 1.0 starts with clean internal storage.
 * Uses explicit byte-length UTF-8 framing (no 64 KiB DataOutput.writeUTF limit).
 */
class DraftRecoveryStore(private val storageFile: File) {

    companion object {
        private const val MAGIC = 0x424C4B31 // "BLK1"
        private const val FORMAT_VERSION = 1
        private const val MAX_DRAFTS = 40
        private const val MAX_STRING_BYTES = 8 * 1024 * 1024
    }

    @Synchronized
    fun load(): RecoveryWorkspaceState {
        if (!storageFile.exists() || !storageFile.isFile) {
            return RecoveryWorkspaceState(activeDraftId = null, drafts = emptyList())
        }
        return runCatching {
            DataInputStream(BufferedInputStream(storageFile.inputStream())).use { input ->
                val magic = input.readInt()
                val version = input.readInt()
                if (magic != MAGIC || version != FORMAT_VERSION) {
                    throw IOException("Unsupported draft recovery format")
                }
                val activeId = readNullableString(input)
                val count = input.readInt().coerceIn(0, MAX_DRAFTS)
                val drafts = ArrayList<ScratchDraft>(count)
                repeat(count) {
                    val id = readString(input)
                    val title = readString(input)
                    val uri = readNullableString(input)
                    val mime = readString(input)
                    val content = readString(input)
                    val isDirty = input.readBoolean()
                    val updatedAt = input.readLong()
                    drafts.add(
                        ScratchDraft(
                            id = id,
                            title = title,
                            uriString = uri,
                            mimeType = mime,
                            content = content,
                            isDirty = isDirty,
                            updatedAtMs = updatedAt
                        )
                    )
                }
                RecoveryWorkspaceState(activeDraftId = activeId, drafts = drafts)
            }
        }.getOrElse {
            RecoveryWorkspaceState(activeDraftId = null, drafts = emptyList())
        }
    }

    @Synchronized
    fun save(state: RecoveryWorkspaceState) {
        val trimmed = state.drafts
            .sortedByDescending { it.updatedAtMs }
            .take(MAX_DRAFTS)
        AtomicPrivateFileWriter.write(storageFile) { fileOut ->
            val out = DataOutputStream(BufferedOutputStream(fileOut))
            out.writeInt(MAGIC)
            out.writeInt(FORMAT_VERSION)
            writeNullableString(out, state.activeDraftId)
            out.writeInt(trimmed.size)
            for (draft in trimmed) {
                writeString(out, draft.id)
                writeString(out, draft.title)
                writeNullableString(out, draft.uriString)
                writeString(out, draft.mimeType)
                writeString(out, draft.content)
                out.writeBoolean(draft.isDirty)
                out.writeLong(draft.updatedAtMs)
            }
            out.flush()
        }
    }

    @Synchronized
    fun upsert(draft: ScratchDraft, setActive: Boolean = true): RecoveryWorkspaceState {
        val current = load()
        val updatedList = current.drafts.filterNot { it.id == draft.id } + draft
        val next = RecoveryWorkspaceState(
            activeDraftId = if (setActive) draft.id else current.activeDraftId,
            drafts = updatedList.sortedByDescending { it.updatedAtMs }.take(MAX_DRAFTS)
        )
        save(next)
        return next
    }

    @Synchronized
    fun remove(draftId: String): RecoveryWorkspaceState {
        val current = load()
        val remaining = current.drafts.filterNot { it.id == draftId }
        val nextActive = if (current.activeDraftId == draftId) {
            remaining.firstOrNull()?.id
        } else {
            current.activeDraftId
        }
        val next = RecoveryWorkspaceState(activeDraftId = nextActive, drafts = remaining)
        save(next)
        return next
    }

    private fun writeNullableString(out: DataOutputStream, value: String?) {
        if (value == null) {
            out.writeInt(-1)
        } else {
            writeString(out, value)
        }
    }

    private fun writeString(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readNullableString(input: DataInputStream): String? {
        val len = input.readInt()
        if (len < 0) return null
        return readBytesAsString(input, len)
    }

    private fun readString(input: DataInputStream): String {
        val len = input.readInt()
        if (len < 0) throw IOException("Unexpected null string")
        return readBytesAsString(input, len)
    }

    private fun readBytesAsString(input: DataInputStream, length: Int): String {
        if (length > MAX_STRING_BYTES) throw IOException("String exceeds safety limit")
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}
