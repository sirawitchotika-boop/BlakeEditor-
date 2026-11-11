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
    val updatedAtMs: Long = System.currentTimeMillis(),
    val sourceEncoding: String? = null,
    val sourceHadBom: Boolean = false,
    val sourceSha256: String? = null,
    val savedToDisk: Boolean = false,
    val trashedAtMs: Long? = null
) {
    init {
        require(displayName.isNotBlank()) { "Display name must not be blank" }
        require(selectionStart >= 0 && selectionEnd >= 0) { "Invalid selection" }
        require(scrollY >= 0) { "Invalid scroll" }
        require(revision > 0) { "Revision must be positive" }
        require(sourceSha256 == null || Regex("[0-9a-f]{64}").matches(sourceSha256)) {
            "Invalid source fingerprint"
        }
        require(sourceEncoding == null || sourceEncoding in setOf(
            "UTF8", "UTF16_LE", "UTF16_BE", "WINDOWS_874", "TIS_620"
        )) { "Unsupported recorded encoding" }
        require(trashedAtMs == null || trashedAtMs >= 0L) { "Invalid Trash timestamp" }
        if (savedToDisk) require(sourceUri != null && sourceSha256 != null && sourceEncoding != null) {
            "Saved document must have verified file metadata"
        }
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
        const val FORMAT_VERSION = 3
        const val MAX_METADATA_BYTES = 8192
        const val MAX_CONTENT_BYTES = 32 * 1024 * 1024
        const val MAX_FILE_BYTES = MAX_CONTENT_BYTES + MAX_METADATA_BYTES * 4
        const val EXTENSION = ".recovery"
        const val LEGACY_MARKER = "legacy-imported.marker"
        const val UNTITLED_COUNTER = "next-untitled-number.counter"
        val numberedUntitled = Regex("Untitled ([1-9][0-9]*)")
        // Serializes reservations across store instances within the app process.
        val numberReservationLock = Any()
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
     * Reserve a number before writing a new draft. A failed draft write may skip
     * a number, but must never make it available to a later draft.
     *
     * Existing installs may have numbered records but no counter yet. Initialize
     * above their highest recorded name, and fail closed if any recovery file
     * cannot be checked. Never reconstruct an existing corrupt counter.
     */
    fun reserveUntitledNumber(): Result<Long> = runCatching {
        synchronized(numberReservationLock) {
            val counter = File(directory, UNTITLED_COUNTER)
            val next = if (counter.exists()) {
                require(counter.isFile && counter.length() in 1L..20L) {
                    "Untitled counter is invalid; existing draft data was not changed"
                }
                val value = counter.readText(Charsets.US_ASCII)
                require(Regex("[1-9][0-9]*").matches(value)) {
                    "Untitled counter is invalid; existing draft data was not changed"
                }
                value.toLongOrNull() ?: error("Untitled counter is out of range")
            } else {
                val listing = list()
                check(listing.unreadableFiles.isEmpty()) {
                    "Cannot initialize Untitled numbering while recovery records are unreadable"
                }
                val highest = listing.drafts.mapNotNull { draft ->
                    numberedUntitled.matchEntire(draft.displayName)
                        ?.groupValues?.get(1)?.let { number ->
                            number.toLongOrNull() ?: error("Existing Untitled number is out of range")
                        }
                }.maxOrNull() ?: 0L
                check(highest < Long.MAX_VALUE) { "No more Untitled numbers are available" }
                highest + 1L
            }
            check(next >= 1L && next < Long.MAX_VALUE) {
                "No more Untitled numbers are available"
            }
            // The reservation is committed before creating the draft itself.
            atomicWrite(counter, (next + 1L).toString().toByteArray(Charsets.US_ASCII))
            next
        }
    }

    /**
     * Rename a draft's display label only. Never rename or write the backing
     * Android file. Compare the Home row's revision with the latest stored
     * snapshot so a stale rename cannot replace newer unsaved edits.
     *
     * This uses the same durable, atomic recovery write as text edits.
     * Duplicate custom names are intentionally allowed; IDs are the identity.
     */
    @Synchronized
    fun renameDisplayName(
        id: DocumentIdentity,
        expectedRevision: Long,
        enteredName: String
    ): Result<RecoveryDraft> = runCatching {
        val name = enteredName.trim()
        require(name.isNotEmpty()) { "Draft name cannot be empty" }
        require(name.toByteArray(Charsets.UTF_8).size <= MAX_METADATA_BYTES) {
            "Draft name is too long"
        }
        val current = readDraftFile(id) ?: error("Draft is missing; nothing was changed")
        check(current.trashedAtMs == null) { "Draft is in Trash and cannot be renamed here" }
        check(current.revision == expectedRevision) {
            "Draft changed since Home opened. Reload Home and try again."
        }
        if (current.displayName == name) return@runCatching current
        check(current.revision < Long.MAX_VALUE) { "Draft revision is out of range" }
        val updated = current.copy(
            displayName = name,
            revision = current.revision + 1L,
            updatedAtMs = maxOf(current.updatedAtMs, System.currentTimeMillis())
        )
        save(updated).getOrThrow()
        updated
    }

    /**
     * Atomically mark an unsaved draft as Trash without removing its recovery
     * file. The full content, cursor, scroll, source URI and encoding metadata
     * stay together in one complete snapshot.
     *
     * Home actions pass the revision that was displayed. A newer edit or
     * another operation must win rather than being silently hidden.
     */
    @Synchronized
    fun moveToTrash(id: DocumentIdentity, expectedRevision: Long): Result<RecoveryDraft> =
        runCatching {
            val current = readDraftFile(id) ?: error("Draft is missing; nothing was moved")
            check(current.revision == expectedRevision) {
                "Draft changed since Home opened. Reload Home before moving it."
            }
            check(current.trashedAtMs == null) { "Draft is already in Trash" }
            check(!current.savedToDisk) {
                "Saved files belong in Recent Files, not draft Trash"
            }
            check(current.revision < Long.MAX_VALUE) { "Draft revision is out of range" }
            val now = System.currentTimeMillis().coerceAtLeast(0L)
            val moved = current.copy(
                trashedAtMs = now,
                revision = current.revision + 1L,
                updatedAtMs = maxOf(current.updatedAtMs, now)
            )
            save(moved).getOrThrow()
            moved
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
            out.writeShortString(draft.sourceEncoding.orEmpty())
            out.writeBoolean(draft.sourceHadBom)
            out.writeShortString(draft.sourceSha256.orEmpty())
            out.writeBoolean(draft.savedToDisk)
            out.writeBoolean(draft.trashedAtMs != null)
            if (draft.trashedAtMs != null) out.writeLong(draft.trashedAtMs)
        }
        return bytes.toByteArray()
    }

    private fun deserialize(bytes: ByteArray): RecoveryDraft =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Unrecognized draft format" }
            val version = input.readInt()
            require(version in 1..FORMAT_VERSION) { "Unsupported draft version" }
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
            val sourceEncoding = if (version >= 2) input.readShortString().ifBlank { null } else null
            val sourceHadBom = if (version >= 2) input.readBoolean() else false
            val sourceHash = if (version >= 2) input.readShortString().ifBlank { null } else null
            val savedToDisk = if (version >= 2) input.readBoolean() else false
            val trashedAtMs = if (version >= 3 && input.readBoolean()) input.readLong() else null
            require(input.read() == -1) { "Unexpected trailing data" }
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            val content = decoder.decode(java.nio.ByteBuffer.wrap(contentBytes)).toString()
            RecoveryDraft(id, uri, name, language, content, selectionStart, selectionEnd,
                scrollY, revision, updatedAt, sourceEncoding, sourceHadBom,
                sourceHash, savedToDisk, trashedAtMs)
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
