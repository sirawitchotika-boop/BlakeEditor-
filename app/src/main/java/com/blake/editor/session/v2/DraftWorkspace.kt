package com.blake.editor.session.v2

/**
 * Document actions shared by the V2 Home screen and editor.
 *
 * A successful switch or Home navigation means the active document's latest
 * recovery snapshot has already been written. File saves are separate and
 * are intentionally not part of this preview milestone.
 */
class DraftWorkspace(private val store: RecoveryDraftStore) {

    fun createUntitled(): Result<RecoveryDraft> = runCatching {
        val nextNumber = store.reserveUntitledNumber().getOrThrow()
        val draft = RecoveryDraft(
            id = DocumentIdentity.newUntitled(),
            sourceUri = null,
            displayName = "Untitled $nextNumber",
            language = "PLAIN_TEXT",
            content = ""
        )
        store.save(draft).getOrThrow()
        draft
    }

    fun openFromFile(
        sourceUri: String,
        displayName: String,
        language: String,
        fileContent: String,
        encoding: String? = null,
        hadBom: Boolean = false,
        sourceSha256: String? = null
    ): Result<RecoveryDraft> = runCatching {
        val id = DocumentIdentity.forUri(sourceUri)
        val existing = store.load(id).getOrThrow()
        if (existing != null) {
            check(existing.trashedAtMs == null) {
                "This file has a recovery draft in Trash. It was not changed; review Trash first."
            }
            if (existing.savedToDisk && existing.sourceSha256 != null &&
                sourceSha256 != null && existing.sourceSha256 != sourceSha256
            ) {
                error("This file changed outside Blake. Recovery record retained; inspect the file and use Save As.")
            }
            return@runCatching existing
        }

        val draft = RecoveryDraft(
            id = id,
            sourceUri = sourceUri,
            displayName = displayName,
            language = language,
            content = fileContent,
            sourceEncoding = encoding,
            sourceHadBom = hadBom,
            sourceSha256 = sourceSha256,
            savedToDisk = encoding != null && sourceSha256 != null
        )
        store.save(draft).getOrThrow()
        draft
    }

    /** Return the same snapshot when nothing changed, preserving its revision. */
    fun edit(
        current: RecoveryDraft,
        content: String,
        selectionStart: Int,
        selectionEnd: Int,
        scrollY: Int
    ): RecoveryDraft {
        val from = selectionStart.coerceIn(0, content.length)
        val to = selectionEnd.coerceIn(0, content.length)
        val scroll = scrollY.coerceAtLeast(0)
        if (current.content == content &&
            current.selectionStart == from &&
            current.selectionEnd == to &&
            current.scrollY == scroll
        ) {
            return current
        }
        return current.copy(
            content = content,
            savedToDisk = if (current.content != content) false else current.savedToDisk,
            selectionStart = from,
            selectionEnd = to,
            scrollY = scroll,
            revision = current.revision + 1,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    /** Metadata-only rename; the real file remains unchanged. */
    fun renameDraft(
        id: DocumentIdentity,
        expectedRevision: Long,
        enteredName: String
    ): Result<RecoveryDraft> = store.renameDisplayName(id, expectedRevision, enteredName)

    /** Confirmed Home action: preserve unsaved text and recovery metadata first. */
    fun moveToTrash(id: DocumentIdentity, expectedRevision: Long): Result<RecoveryDraft> =
        store.moveToTrash(id, expectedRevision)

    fun persist(current: RecoveryDraft): Result<Unit> = store.save(current)

    /** The caller must not leave the editor when this returns a failure. */
    fun returnHome(current: RecoveryDraft): Result<Unit> = store.save(current)

    /** Switching is forbidden if the outgoing draft could not be stored. */
    fun switchTo(current: RecoveryDraft, targetId: DocumentIdentity): Result<RecoveryDraft> =
        runCatching {
            store.save(current).getOrThrow()
            val target = store.load(targetId).getOrThrow()
                ?: error("The selected recovery draft is missing")
            check(target.trashedAtMs == null) { "The selected draft is in Trash" }
            target
        }

    fun load(id: DocumentIdentity): Result<RecoveryDraft?> = store.load(id)

    fun list(): RecoveryListing = store.list()
}
