package com.blake.editor.session

import android.content.Context
import android.net.Uri
import com.blake.editor.core.model.EditorSession
import com.blake.editor.core.model.SyntaxMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Properties

/**
 * Original editor draft storage.
 *
 * Legacy pairs (<key>.draft and <key>.meta) remain readable and untouched
 * on upgrade. New saves use two alternating pairs with the SAME draft text
 * and Java Properties metadata formats. The atomic .active marker is the
 * commit point: an interrupted write never exposes half a new pair.
 *
 * A deleted marker is a tombstone, so incomplete cleanup cannot resurrect
 * an old draft. Only a single app process is supported by the shared lock.
 */
class FileDraftRepository(
    private val baseDir: File,
    private val uriParser: (String) -> Uri? = { Uri.parse(it) }
) : DraftRepository {

    constructor(context: Context) : this(File(context.filesDir, "drafts"))

    private fun getDraftKey(uri: Uri?): String {
        if (uri == null) return "untitled"
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(uri.toString().toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun legacyContent(key: String) = File(baseDir, "$key.draft")
    private fun legacyMetadata(key: String) = File(baseDir, "$key.meta")
    private fun slotContent(key: String, slot: String) = File(baseDir, "$key.draft.$slot")
    private fun slotMetadata(key: String, slot: String) = File(baseDir, "$key.meta.$slot")
    private fun markerFile(key: String) = File(baseDir, "$key.active")

    /** null means legacy; "deleted" is an intentional, durable clear. */
    private fun readMarker(key: String): String? {
        val marker = markerFile(key)
        if (!marker.exists()) return null
        val value = marker.readText(Charsets.US_ASCII)
        require(value == "0" || value == "1" || value == "deleted") {
            "Draft commit marker is invalid; existing draft files were retained"
        }
        return value
    }

    private fun writeMarker(key: String, value: String) {
        AtomicPrivateFileWriter.write(markerFile(key)) { output ->
            output.write(value.toByteArray(Charsets.US_ASCII))
        }
    }

    private fun metadata(session: EditorSession) = Properties().apply {
        setProperty("uri", session.uri?.toString() ?: "")
        setProperty("displayName", session.displayName)
        setProperty("syntaxMode", session.syntaxMode.name)
        setProperty("isDirty", session.isDirty.toString())
        setProperty("cursorLine", session.cursorLine.toString())
        setProperty("cursorColumn", session.cursorColumn.toString())
        setProperty("scrollPosition", session.scrollPosition.toString())
    }

    override suspend fun saveDraft(session: EditorSession, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                synchronized(draftMutationLock) {
                    val key = getDraftKey(session.uri)
                    val nextSlot = if (readMarker(key) == "0") "1" else "0"

                    // Both staged replacements must succeed before the marker
                    // becomes visible. The currently committed pair is untouched.
                    AtomicPrivateFileWriter.write(slotContent(key, nextSlot)) { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                    AtomicPrivateFileWriter.write(slotMetadata(key, nextSlot)) { out ->
                        metadata(session).store(out, "Blake Editor Draft Metadata")
                    }
                    writeMarker(key, nextSlot)
                }
            }
        }

    override suspend fun loadDraft(uri: Uri?): Result<Pair<EditorSession, String>?> =
        withContext(Dispatchers.IO) {
            runCatching {
                synchronized(draftMutationLock) {
                    val key = getDraftKey(uri)
                    when (val slot = readMarker(key)) {
                        "deleted" -> null
                        null -> readPair(legacyContent(key), legacyMetadata(key))
                        else -> readPair(slotContent(key, slot), slotMetadata(key, slot))
                            ?: error("Committed draft pair is missing; older files were retained")
                    }
                }
            }
        }

    private fun readPair(
        contentFile: File,
        metaFile: File
    ): Pair<EditorSession, String>? {
        if (!contentFile.exists() && !metaFile.exists()) return null
        check(contentFile.isFile && metaFile.isFile) {
            "Draft text/metadata pair is incomplete; original files were retained"
        }
        val content = contentFile.readText(Charsets.UTF_8)
        val props = Properties()
        FileInputStream(metaFile).use { props.load(it) }

        val uriStr = props.getProperty("uri", "").trim()
        val restoredUri = if (uriStr.isNotEmpty()) uriParser(uriStr) else null
        val displayName = props.getProperty("displayName", "untitled.txt")
        val syntaxMode = runCatching {
            SyntaxMode.valueOf(props.getProperty("syntaxMode", SyntaxMode.PLAIN_TEXT.name))
        }.getOrDefault(SyntaxMode.PLAIN_TEXT)

        val session = EditorSession(
            uri = restoredUri,
            displayName = displayName,
            syntaxMode = syntaxMode,
            isDirty = props.getProperty("isDirty", "true").toBoolean(),
            cursorLine = props.getProperty("cursorLine", "1").toIntOrNull() ?: 1,
            cursorColumn = props.getProperty("cursorColumn", "1").toIntOrNull() ?: 1,
            scrollPosition = props.getProperty("scrollPosition", "0").toIntOrNull() ?: 0
        )
        return Pair(session, content)
    }

    override suspend fun clearDraft(uri: Uri?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            synchronized(draftMutationLock) {
                val key = getDraftKey(uri)
                // Commit deletion before cleanup. A crash must not restore
                // legacy pairs or partially deleted slot files.
                writeMarker(key, "deleted")
                for (file in listOf(
                    legacyContent(key), legacyMetadata(key),
                    slotContent(key, "0"), slotMetadata(key, "0"),
                    slotContent(key, "1"), slotMetadata(key, "1")
                )) {
                    if (file.exists() && !file.delete()) {
                        error("Draft was cleared, but old file cleanup was incomplete")
                    }
                }
            }
        }
    }

    override suspend fun hasDraft(uri: Uri?): Boolean = withContext(Dispatchers.IO) {
        synchronized(draftMutationLock) {
            val key = getDraftKey(uri)
            // A corrupt marker is treated as existing so the load path can
            // report corruption rather than silently hiding recovery records.
            val marker = markerFile(key)
            if (marker.exists()) {
                marker.readText(Charsets.US_ASCII) != "deleted"
            } else {
                legacyContent(key).exists() || legacyMetadata(key).exists()
            }
        }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS: Long = 800L

        // Protects all repository instances within this app process.
        private val draftMutationLock = Any()
    }
}
