package com.blake.editor.session.v2

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * New document-scoped recovery API. Separate from the existing FileDraftRepository,
 * which is still used by the V1 UI until the V2 document manager is implemented.
 *
 * One file per draft; a synced temp file is atomically renamed into place.
 * Never write directly over a complete previous recovery record.
 */
data class RecoveryDraft(
    val id: DocumentIdentity,
    val sourceUri: String?,
    val displayName: String,
    val language: String,
    val content: String,
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val scrollY: Int = 0,
    val revision: Long = 1,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    init {
        require(displayName.isNotBlank()) { "Display name must not be blank" }
        require(selectionStart >= 0 && selectionEnd >= 0) { "Invalid selection" }
        require(scrollY >= 0) { "Invalid scroll" }
        require(revision > 0) { "Revision must be positive" }
        if (sourceUri == null) {
            require(id.value.startsWith("new-")) { "Untitled document needs an untitled ID" }
        } else {
            require(id == DocumentIdentity.forUri(sourceUri)) { "URI and ID mismatch" }
        }
    }
}

data class RecoveryListing(
    val drafts: List<RecoveryDraft>,
    val unreadableFiles: List<String>
)

data class LegacyMigrationReport(
    val imported: Int,
    val alreadyPresent: Int,
    val failedFiles: List<String>,
    val complete: Boolean
)

class RecoveryDraftStore(private val directory: File) {
    private companion object {
        const val MAGIC = 0x424C4B32 // BLK2
        const val FORMAT_VERSION = 1
        const val MAX_METADATA_BYTES = 8192
        const val MAX_CONTENT_BYTES = 32 * 1024 * 1024
        const val MAX_FILE_BYTES = MAX_CONTENT_BYTES + MAX_METADATA_BYTES * 4
        const val EXTENSION = ".recovery"
        const val LEGACY_MARKER = "legacy-imported.marker"
    }

    @Synchronized
    fun save(draft: RecoveryDraft): Result<Unit> = runCatching {
        val old = readDraftFile(draft.id)
        if (old != null) {
            require(draft.revision >= old.revision) { "Stale draft revision" }
            require(draft.sourceUri == old.sourceUri) { "Document source changed" }
            if (draft.revision == old.revision) {
                require(draft == old) { "Conflicting draft revision" }
                return@runCatching
            }
        }
        atomicWrite(fileFor(draft.id), serialize(draft))
    }

    @Synchronized
    fun load(id: DocumentIdentity): Result<RecoveryDraft?> =
        runCatching { readDraftFile(id) }

    @Synchronized
    fun list(): RecoveryListing {
        val drafts = mutableListOf<RecoveryDraft>()
        val unreadable = mutableListOf<String>()
        if (!directory.exists()) return RecoveryListing(emptyList(), emptyList())
        directory.listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) }
            ?.forEach { file ->
                runCatching {
                    val rawId = file.name.removeSuffix(EXTENSION)
                    val id = DocumentIdentity.fromStored(rawId)
                    readDraftFile(id) ?: error("Missing recovery file")
                }.onSuccess { drafts.add(it) }
                    .onFailure { unreadable.add(file.name) }
            }
        return RecoveryListing(
            drafts.sortedByDescending { it.updatedAtMs },
            unreadable.sorted()
        )
    }

    /**
     * Only delete when the on-disk revision equals the revision that was
     * successfully written to the real file. Never clear newer unsaved edits.
     */
    @Synchronized
    fun clearSavedRevision(id: DocumentIdentity, savedRevision: Long): Result<Boolean> =
        runCatching {
            val old = readDraftFile(id) ?: return@runCatching false
            if (old.revision != savedRevision) return@runCatching false
            if (!fileFor(id).delete()) throw IOException("Could not delete saved recovery draft")
            true
        }

    /**
     * One-time, non-destructive import from the V1 directory. V1 files are
     * never deleted and a partially failed import can be safely retried.
     *
     * Call this once when the V2 home/document manager becomes active.
     * It is intentionally not invoked by the existing app.
     */
    @Synchronized
    fun importLegacy(legacyDirectory: File): LegacyMigrationReport {
        if (File(directory, LEGACY_MARKER).exists()) {
            return LegacyMigrationReport(0, 0, emptyList(), true)
        }
        var imported = 0
        var alreadyPresent = 0
        val failures = mutableListOf<String>()
        val metadataFiles = legacyDirectory.listFiles { file ->
            file.isFile && file.name.endsWith(".meta")
        }.orEmpty()

        for (metaFile in metadataFiles) {
            try {
                val legacyKey = metaFile.name.removeSuffix(".meta")
                val contentFile = File(legacyDirectory, "$legacyKey.draft")
                require(contentFile.isFile) { "Legacy content missing" }

                val properties = Properties()
                FileInputStream(metaFile).use(properties::load)
                val uri = properties.getProperty("uri", "").trim().ifBlank { null }
                val id = if (uri == null) {
                    require(legacyKey == "untitled") { "Unknown untitled legacy key" }
                    DocumentIdentity.legacyUntitled()
                } else {
                    val derivedId = DocumentIdentity.forUri(uri)
                    require(legacyKey == derivedId.value.removePrefix("uri-")) {
                        "Legacy URI does not match draft filename"
                    }
                    derivedId
                }

                if (readDraftFile(id) != null) {
                    alreadyPresent++
                    continue
                }
                require(contentFile.length() <= MAX_CONTENT_BYTES.toLong()) {
                    "Legacy content too large"
                }
                val content = contentFile.readText(Charsets.UTF_8)
                val line = properties.getProperty("cursorLine", "1").toIntOrNull() ?: 1
                val column = properties.getProperty("cursorColumn", "1").toIntOrNull() ?: 1
                val position = legacyPosition(content, line, column)
                val scroll = (properties.getProperty("scrollPosition", "0").toIntOrNull() ?: 0)
                    .coerceAtLeast(0)
                val recovered = RecoveryDraft(
                    id = id,
                    sourceUri = uri,
                    displayName = properties.getProperty("displayName", "Untitled")
                        .ifBlank { "Untitled" },
                    language = properties.getProperty("syntaxMode", "PLAIN_TEXT"),
                    content = content,
                    selectionStart = position,
                    selectionEnd = position,
                    scrollY = scroll,
                    revision = 1
                )
                save(recovered).getOrThrow()
                imported++
            } catch (_: Exception) {
                // Never delete the V1 originals, even when one entry is invalid.
                failures.add(metaFile.name)
            }
        }

        val finished = failures.isEmpty()
        if (finished) {
            try {
                atomicWrite(File(directory, LEGACY_MARKER), byteArrayOf(1))
            } catch (_: Exception) {
                failures.add(LEGACY_MARKER)
            }
        }
        return LegacyMigrationReport(imported, alreadyPresent, failures, failures.isEmpty())
    }

    private fun legacyPosition(content: String, line: Int, column: Int): Int {
        val targetLine = line.coerceAtLeast(1)
        var at = 0
        var current = 1
        while (at < content.length && current < targetLine) {
            if (content[at] == '\n') current++
            at++
        }
        return (at.toLong() + column.coerceAtLeast(1) - 1).coerceAtMost(content.length.toLong()).toInt()
    }

    private fun fileFor(id: DocumentIdentity): File =
        File(directory, id.value + EXTENSION)

    private fun readDraftFile(id: DocumentIdentity): RecoveryDraft? {
        val file = fileFor(id)
        if (!file.exists()) return null
        require(file.isFile && file.length() <= MAX_FILE_BYTES.toLong()) {
            "Recovery file is too large or invalid"
        }
        val record = deserialize(file.readBytes())
        require(record.id == id) { "Recovery file identity mismatch" }
        return record
    }

    private fun serialize(draft: RecoveryDraft): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(FORMAT_VERSION)
            out.writeShortString(draft.id.value)
            out.writeBoolean(draft.sourceUri != null)
            if (draft.sourceUri != null) out.writeShortString(draft.sourceUri)
            out.writeShortString(draft.displayName)
            out.writeShortString(draft.language)
            out.writeInt(draft.selectionStart)
            out.writeInt(draft.selectionEnd)
            out.writeInt(draft.scrollY)
            out.writeLong(draft.revision)
            out.writeLong(draft.updatedAtMs)
            val content = draft.content.toByteArray(Charsets.UTF_8)
            require(content.size <= MAX_CONTENT_BYTES) { "Recovery draft exceeds safe editing size" }
            out.writeInt(content.size)
            out.write(content)
        }
        return bytes.toByteArray()
    }

    private fun deserialize(bytes: ByteArray): RecoveryDraft =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Unrecognized draft format" }
            require(input.readInt() == FORMAT_VERSION) { "Unsupported draft version" }
            val id = DocumentIdentity.fromStored(input.readShortString())
            val uri = if (input.readBoolean()) input.readShortString() else null
            val name = input.readShortString()
            val language = input.readShortString()
            val selectionStart = input.readInt()
            val selectionEnd = input.readInt()
            val scrollY = input.readInt()
            val revision = input.readLong()
            val updatedAt = input.readLong()
            val length = input.readInt()
            require(length in 0..MAX_CONTENT_BYTES) { "Invalid text size" }
            val contentBytes = ByteArray(length)
            input.readFully(contentBytes)
            require(input.read() == -1) { "Unexpected trailing data" }
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            val content = decoder.decode(java.nio.ByteBuffer.wrap(contentBytes)).toString()
            RecoveryDraft(id, uri, name, language, content, selectionStart, selectionEnd,
                scrollY, revision, updatedAt)
        }

    private fun DataOutputStream.writeShortString(s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_METADATA_BYTES) { "Metadata too large" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readShortString(): String {
        val length = readInt()
        require(length in 0..MAX_METADATA_BYTES) { "Metadata field is invalid" }
        val bytes = ByteArray(length)
        readFully(bytes)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }

    private fun atomicWrite(destination: File, bytes: ByteArray) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create recovery directory")
        }
        val temp = Files.createTempFile(directory.toPath(), "saving-", ".tmp")
        try {
            FileOutputStream(temp.toFile()).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Files.move(
                temp,
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
