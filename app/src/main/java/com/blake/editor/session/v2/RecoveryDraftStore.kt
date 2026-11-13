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
import com.blake.editor.session.FileDraftRepository

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
    val unreadableFiles: List<String>,
    // Separate private shortcut metadata. Never used to decide whether a
    // recovery record should be deleted, overwritten, or opened.
    val hiddenRecentIds: Set<DocumentIdentity> = emptySet()
)

/** A changed or unverifiable linked source is restored as a separate, unlinked copy. */
data class DraftRestoreResult(
    val draft: RecoveryDraft,
    val recoveredAsCopy: Boolean
)

data class LegacyMigrationReport(
    val imported: Int,
    val alreadyPresent: Int,
    val failedFiles: List<String>,
    val complete: Boolean
)

data class HistoricalDraftDifference(
    val id: DocumentIdentity,
    val originalSourceUri: String?,
    val originalDisplayName: String,
    val originalContent: String,
    val existingV2: RecoveryDraft?
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
        const val HIDDEN_RECENT_INDEX = "recent-shortcuts-hidden.index"
        const val HIDDEN_RECENT_HEADER = "BLK-RECENT-HIDDEN-V1\n"
        const val MAX_HIDDEN_RECENT_BYTES = 1024 * 1024
        val numberedUntitled = Regex("Untitled ([1-9][0-9]*)")
        // Serializes reservations across store instances within the app process.
        val recoveryMutationLock = Any()
    }

    /** Serializes recovery mutations across all store instances in this app process. */
    private inline fun <T> mutate(block: () -> T): Result<T> =
        synchronized(recoveryMutationLock) { runCatching(block) }

    fun save(draft: RecoveryDraft): Result<Unit> = mutate {
        val old = readDraftFile(draft.id)
        if (old != null) {
            require(draft.revision >= old.revision) { "Stale draft revision" }
            require(draft.sourceUri == old.sourceUri) { "Document source changed" }
            if (draft.revision == old.revision) {
                require(draft == old) { "Conflicting draft revision" }
                return@mutate
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
        val hidden = runCatching { readHiddenRecentIds() }
            .onFailure { unreadable.add(HIDDEN_RECENT_INDEX) }
            .getOrDefault(emptySet())
        return RecoveryListing(
            drafts.sortedByDescending { it.updatedAtMs },
            unreadable.sorted(),
            hidden
        )
    }

    /**
     * Remove only a Home shortcut. This operation never writes a recovery
     * record or calls any Android file writer. The record stays openable and
     * is still available in recovery even after an app restart.
     */
    fun hideFromRecent(
        id: DocumentIdentity,
        expectedRevision: Long
    ): Result<Unit> = mutate {
        val record = readDraftFile(id) ?: error("Saved-file shortcut is missing")
        check(record.trashedAtMs == null && record.savedToDisk) {
            "Only clean saved-file shortcuts can be removed from Recent"
        }
        check(record.revision == expectedRevision) {
            "The file record changed. Refresh Home and try again."
        }
        val hidden = readHiddenRecentIds()
        if (id !in hidden) writeHiddenRecentIds(hidden + id)
    }

    /**
     * Explicitly reopening the same file may show it in Recent again.
     * Failure to update this shortcut index cannot damage any recovery text.
     */
    fun showInRecent(id: DocumentIdentity): Result<Unit> = mutate {
        val hidden = readHiddenRecentIds()
        if (id in hidden) writeHiddenRecentIds(hidden - id)
    }

    private fun readHiddenRecentIds(): Set<DocumentIdentity> {
        val index = File(directory, HIDDEN_RECENT_INDEX)
        if (!index.exists()) return emptySet()
        require(index.isFile && index.length() <= MAX_HIDDEN_RECENT_BYTES) {
            "Recent shortcut index is invalid; original file records were retained"
        }
        val content = index.readText(Charsets.UTF_8)
        require(content.startsWith(HIDDEN_RECENT_HEADER)) {
            "Recent shortcut index has an invalid header"
        }
        val payload = content.removePrefix(HIDDEN_RECENT_HEADER)
        if (payload.isEmpty()) return emptySet()
        require(payload.endsWith("\n")) { "Recent shortcut index is incomplete" }
        val rawIds = payload.dropLast(1).split("\n")
        require(rawIds.none { it.isEmpty() }) { "Recent shortcut index has an empty ID" }
        val ids = rawIds.map { DocumentIdentity.fromStored(it) }
        require(ids.size == ids.toSet().size) { "Recent shortcut index has duplicate IDs" }
        return ids.toSet()
    }

    private fun writeHiddenRecentIds(hidden: Set<DocumentIdentity>) {
        val text = HIDDEN_RECENT_HEADER +
            hidden.map { it.value }.sorted().joinToString(separator = "\n", postfix =
                if (hidden.isEmpty()) "" else "\n")
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_HIDDEN_RECENT_BYTES) {
            "Recent shortcut index is full; no records were changed"
        }
        atomicWrite(File(directory, HIDDEN_RECENT_INDEX), bytes)
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
        synchronized(recoveryMutationLock) {
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
                // The optional Recent shortcut index is not a recovery record.
                // A damaged index must not prevent safe Untitled numbering.
                check(listing.unreadableFiles.none { it != HIDDEN_RECENT_INDEX }) {
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
    fun renameDisplayName(
        id: DocumentIdentity,
        expectedRevision: Long,
        enteredName: String
    ): Result<RecoveryDraft> = mutate {
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
        if (current.displayName == name) return@mutate current
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
    fun moveToTrash(id: DocumentIdentity, expectedRevision: Long): Result<RecoveryDraft> =
        mutate {
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
     * Restore an entire recovery snapshot, never the external Android file.
     * The caller verifies source bytes against their original fingerprint.
     *
     * If a linked source changed, cannot be read, or lacks a known fingerprint,
     * write a separately identified untitled recovery copy BEFORE returning.
     * The original Trash record stays intact for manual review; never risk
     * overwriting the real source file.
     */
    fun restoreFromTrash(
        id: DocumentIdentity,
        expectedRevision: Long,
        sourceMatches: Boolean
    ): Result<DraftRestoreResult> = mutate {
        val current = readDraftFile(id) ?: error("Recovery draft is missing")
        check(current.trashedAtMs != null) { "Draft is not in Trash" }
        check(current.revision == expectedRevision) {
            "Trash entry changed. Reload Trash and try again."
        }
        check(current.revision < Long.MAX_VALUE) { "Draft revision is out of range" }
        val now = System.currentTimeMillis().coerceAtLeast(1L)
        if (current.sourceUri == null ||
            (sourceMatches && current.sourceSha256 != null && current.sourceEncoding != null)
        ) {
            val restored = current.copy(
                trashedAtMs = null,
                revision = current.revision + 1L,
                updatedAtMs = maxOf(current.updatedAtMs, now)
            )
            save(restored).getOrThrow()
            DraftRestoreResult(restored, recoveredAsCopy = false)
        } else {
            // This file can never silently regain a Save-to-original connection.
            // Keep the old Trash record until the user explicitly removes it.
            val recovered = current.copy(
                id = DocumentIdentity.newUntitled(),
                sourceUri = null,
                displayName = ("Recovered — " + current.displayName).take(120),
                sourceSha256 = null,
                savedToDisk = false,
                trashedAtMs = null,
                revision = 1L,
                updatedAtMs = now
            )
            save(recovered).getOrThrow()
            DraftRestoreResult(recovered, recoveredAsCopy = true)
        }
    }

    /**
     * Only explicitly confirmed Trash actions may remove private recovery bytes.
     * Checks identity, stored revision, and Trash state. Never touches Android
     * documents, SAF URIs, saved-file metadata, or untrashed drafts.
     */
    fun deleteTrashedPermanently(
        id: DocumentIdentity,
        expectedRevision: Long
    ): Result<Unit> = mutate {
        val current = readDraftFile(id) ?: error("Trash entry is missing")
        check(current.trashedAtMs != null) { "Draft is not in Trash" }
        check(current.revision == expectedRevision) {
            "Trash entry changed. Reload Trash and try again."
        }
        if (!fileFor(id).delete()) {
            throw IOException("Could not remove Trash recovery record")
        }
    }

    /**
     * Only delete when the on-disk revision equals the revision that was
     * successfully written to the real file. Never clear newer unsaved edits.
     */
    fun clearSavedRevision(id: DocumentIdentity, savedRevision: Long): Result<Boolean> =
        mutate {
            val old = readDraftFile(id) ?: return@mutate false
            if (old.trashedAtMs != null) return@mutate false
            if (old.revision != savedRevision) return@mutate false
            if (!fileFor(id).delete()) throw IOException("Could not delete saved recovery draft")
            true
        }

    /**
     * Read-only comparison for a future user-invoked historical import review.
     *
     * An existing migration marker can be older than Original's committed-slot
     * format. This method deliberately does not re-import, write a recovery
     * record, reset the marker, or touch any Original draft. It is not called
     * during normal startup and does not approve any automatic migration.
     *
     * A damaged source or recovery record fails the entire inspection instead
     * of offering a potentially stale or incomplete version to the user.
     */
    fun inspectHistoricalImports(legacyDirectory: File): Result<List<HistoricalDraftDifference>> =
        synchronized(recoveryMutationLock) {
            synchronized(FileDraftRepository.draftMutationLock) {
                runCatching {
                    check(File(directory, LEGACY_MARKER).isFile) {
                        "Original draft import has not been marked complete"
                    }
                    val entries = if (!legacyDirectory.exists()) {
                        emptyArray<File>()
                    } else {
                        check(legacyDirectory.isDirectory) { "Original draft path is invalid" }
                        legacyDirectory.listFiles()
                            ?: error("Cannot inspect Original draft directory")
                    }
                    val slotName = Regex("""^(.+)\.(draft|meta)\.[01]$""")
                    val keys = entries.mapNotNull { file ->
                        when {
                            file.name.endsWith(".active") -> file.name.removeSuffix(".active")
                            file.name.endsWith(".draft") -> file.name.removeSuffix(".draft")
                            file.name.endsWith(".meta") -> file.name.removeSuffix(".meta")
                            else -> slotName.matchEntire(file.name)?.groupValues?.get(1)
                        }
                    }.distinct().sorted()

                    keys.mapNotNull { key ->
                        require(key.isNotEmpty()) { "Invalid Original draft key" }
                        val activeMarker = File(legacyDirectory, "${key}.active")
                        val slot = if (activeMarker.exists()) {
                            check(activeMarker.isFile && activeMarker.length() <= 16L) {
                                "Original draft marker is invalid"
                            }
                            when (val value = activeMarker.readText(Charsets.US_ASCII)) {
                                "0", "1" -> value
                                "deleted" -> return@mapNotNull null
                                else -> error("Invalid Original draft marker: ${value.take(16)}")
                            }
                        } else {
                            // An orphan slot may be a failed publish; never show stale
                            // legacy bytes as though they were the committed version.
                            check((0..1).none { slot ->
                                File(legacyDirectory, "${key}.draft.$slot").exists() ||
                                    File(legacyDirectory, "${key}.meta.$slot").exists()
                            }) { "Uncommitted Original slot exists" }
                            null
                        }
                        val suffix = if (slot == null) "" else ".$slot"
                        val textFile = File(legacyDirectory, "${key}.draft$suffix")
                        val metaFile = File(legacyDirectory, "${key}.meta$suffix")
                        check(textFile.isFile && metaFile.isFile) {
                            "Original committed draft pair is incomplete"
                        }
                        check(metaFile.length() <= MAX_METADATA_BYTES.toLong()) {
                            "Original draft metadata is too large"
                        }
                        check(textFile.length() <= MAX_CONTENT_BYTES.toLong()) {
                            "Original draft text is too large"
                        }
                        val meta = Properties()
                        FileInputStream(metaFile).use(meta::load)
                        val uri = meta.getProperty("uri", "").trim().ifBlank { null }
                        val id = if (uri == null) {
                            check(key == "untitled") { "Unknown Original draft key" }
                            DocumentIdentity.legacyUntitled()
                        } else {
                            val linked = DocumentIdentity.forUri(uri)
                            check(key == linked.value.removePrefix("uri-")) {
                                "Original URI does not match draft key"
                            }
                            linked
                        }
                        val originalContent = textFile.readText(Charsets.UTF_8)
                        val existingV2 = readDraftFile(id)
                        if (existingV2?.content == originalContent) {
                            null
                        } else {
                            HistoricalDraftDifference(
                                id = id,
                                originalSourceUri = uri,
                                originalDisplayName = meta.getProperty("displayName", "Untitled")
                                    .ifBlank { "Untitled" },
                                originalContent = originalContent,
                                existingV2 = existingV2
                            )
                        }
                    }
                }
            }
        }

    /**
     * Create an unlinked recovery copy only after a deliberate, user-confirmed
     * historical comparison. No UI calls this method yet.
     *
     * Reinspect both stores under the Original and V2 process locks before
     * writing, so stale review screens cannot recover out-of-date content.
     * A stable content-derived ID makes repeated confirmations idempotent
     * across app restarts without a separate mutable migration marker.
     *
     * Never writes Original's draft, external SAF documents, the import marker,
     * any existing V2 record, or the untitled naming counter.
     */
    fun copyHistoricalOriginalAfterConfirmation(
        legacyDirectory: File,
        reviewed: HistoricalDraftDifference,
        explicitlyConfirmed: Boolean
    ): Result<RecoveryDraft> =
        synchronized(recoveryMutationLock) {
            synchronized(FileDraftRepository.draftMutationLock) {
                runCatching {
                    check(explicitlyConfirmed) { "Review both versions and confirm a separate copy" }
                    val current = inspectHistoricalImports(legacyDirectory).getOrThrow()
                        .singleOrNull { it.id == reviewed.id }
                        ?: error("Original draft is no longer available for recovery")
                    check(current == reviewed) {
                        "Original or V2 draft changed. Review the latest versions first."
                    }
                    val copyId = historicalCopyId(reviewed)
                    val previous = readDraftFile(copyId)
                    if (previous != null) {
                        check(previous.sourceUri == null &&
                            previous.sourceSha256 == null &&
                            !previous.savedToDisk &&
                            previous.trashedAtMs == null &&
                            previous.content == reviewed.originalContent
                        ) {
                            "A prior recovery copy changed; it was preserved."
                        }
                        return@runCatching previous
                    }
                    val name = ("Recovered Original — " + reviewed.originalDisplayName).take(120)
                    val copy = RecoveryDraft(
                        id = copyId,
                        sourceUri = null,
                        displayName = name,
                        language = "PLAIN_TEXT",
                        content = reviewed.originalContent
                    )
                    save(copy).getOrThrow()
                    copy
                }
            }
        }

    private fun historicalCopyId(reviewed: HistoricalDraftDifference): DocumentIdentity {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(
            "blake-v2-historical-copy-v1\\n${reviewed.id.value}\\n".toByteArray(Charsets.UTF_8)
        )
        val hash = digest.digest(reviewed.originalContent.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return DocumentIdentity.fromStored(
            "new-${hash.substring(0, 8)}-${hash.substring(8, 12)}-" +
                "${hash.substring(12, 16)}-${hash.substring(16, 20)}-${hash.substring(20, 32)}"
        )
    }

    /**
     * Non-destructive, one-time import of Original's currently committed draft.
     *
     * Original used <key>.draft/.meta before two-slot draft commits. Newer
     * builds publish <key>.active only after both slot files are complete.
     * Never read an inactive slot, a deleted draft, or stale legacy bytes
     * when a committed marker exists. Preserve Original files on all paths.
     *
     * Both locks are process-local. This cannot promise cross-process
     * consistency or atomic Android cloud-backup snapshots.
     */
    fun importLegacy(legacyDirectory: File): LegacyMigrationReport =
        synchronized(recoveryMutationLock) {
            synchronized(FileDraftRepository.draftMutationLock) {
                importCommittedOriginal(legacyDirectory)
            }
        }

    private fun importCommittedOriginal(legacyDirectory: File): LegacyMigrationReport {
        if (File(directory, LEGACY_MARKER).exists()) {
            return LegacyMigrationReport(0, 0, emptyList(), true)
        }
        var imported = 0
        var alreadyPresent = 0
        val failures = mutableListOf<String>()

        // Discover both old-format pairs and slot-only drafts. Uncommitted
        // orphan slots without a marker must not be silently marked imported.
        val slotName = Regex("""^(.+)\.(draft|meta)\.[01]$""")
        val originalEntries = try {
            if (legacyDirectory.exists()) legacyDirectory.listFiles() else emptyArray<File>()
        } catch (_: SecurityException) {
            null
        } ?: return LegacyMigrationReport(0, 0, listOf(legacyDirectory.name), false)
        val keys = originalEntries
            .mapNotNull { file ->
                when {
                    file.name.endsWith(".active") -> file.name.removeSuffix(".active")
                    file.name.endsWith(".draft") -> file.name.removeSuffix(".draft")
                    file.name.endsWith(".meta") -> file.name.removeSuffix(".meta")
                    else -> slotName.matchEntire(file.name)?.groupValues?.get(1)
                }
            }
            .distinct()
            .sorted()

        for (legacyKey in keys) {
            try {
                require(legacyKey.isNotEmpty()) { "Missing Original draft key" }
                val marker = File(legacyDirectory, "${legacyKey}.active")
                val slot = if (marker.exists()) {
                    require(marker.isFile) { "Original draft marker is not a file" }
                    when (val value = marker.readText(Charsets.US_ASCII)) {
                        "0", "1" -> value
                        "deleted" -> continue // Do not resurrect cleared drafts.
                        else -> error("Invalid Original draft commit marker")
                    }
                } else null

                val contentFile = File(
                    legacyDirectory,
                    if (slot == null) "${legacyKey}.draft" else "${legacyKey}.draft.${slot}"
                )
                val metaFile = File(
                    legacyDirectory,
                    if (slot == null) "${legacyKey}.meta" else "${legacyKey}.meta.${slot}"
                )
                require(metaFile.isFile && contentFile.isFile) {
                    "Current Original draft text/metadata pair is incomplete"
                }

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
                    continue // Never overwrite an existing V2 recovery revision.
                }
                require(contentFile.length() <= MAX_CONTENT_BYTES.toLong()) {
                    "Original draft content too large"
                }
                val draftContent = contentFile.readText(Charsets.UTF_8)
                val line = properties.getProperty("cursorLine", "1").toIntOrNull() ?: 1
                val column = properties.getProperty("cursorColumn", "1").toIntOrNull() ?: 1
                val position = legacyPosition(draftContent, line, column)
                val scroll = (properties.getProperty("scrollPosition", "0").toIntOrNull() ?: 0)
                    .coerceAtLeast(0)
                val recovered = RecoveryDraft(
                    id = id,
                    sourceUri = uri,
                    displayName = properties.getProperty("displayName", "Untitled")
                        .ifBlank { "Untitled" },
                    language = properties.getProperty("syntaxMode", "PLAIN_TEXT"),
                    content = draftContent,
                    selectionStart = position,
                    selectionEnd = position,
                    scrollY = scroll,
                    revision = 1
                )
                save(recovered).getOrThrow()
                imported++
            } catch (_: Exception) {
                // No file was deleted. Leave the migration marker absent so a
                // repaired pair can be retried without hiding any draft.
                failures.add(legacyKey)
            }
        }

        if (failures.isEmpty()) {
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
