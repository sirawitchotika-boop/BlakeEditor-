package com.blake.editor.session

import android.content.Context
import android.net.Uri
import com.blake.editor.core.model.EditorSession
import com.blake.editor.core.model.SyntaxMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Properties

class FileDraftRepository(
    private val baseDir: File,
    private val uriParser: (String) -> Uri? = { Uri.parse(it) }
) : DraftRepository {

    constructor(context: Context) : this(
        File(context.filesDir, "drafts")
    )

    private fun getDraftKey(uri: Uri?): String {
        if (uri == null) return "untitled"
        val uriStr = uri.toString()
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(uriStr.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getContentFile(key: String): File = File(baseDir, "$key.draft")
    private fun getMetaFile(key: String): File = File(baseDir, "$key.meta")

    override suspend fun saveDraft(session: EditorSession, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
            val key = getDraftKey(session.uri)
            val contentFile = getContentFile(key)
            val metaFile = getMetaFile(key)

            contentFile.writeText(content, Charsets.UTF_8)

            val props = Properties()
            props.setProperty("uri", session.uri?.toString() ?: "")
            props.setProperty("displayName", session.displayName)
            props.setProperty("syntaxMode", session.syntaxMode.name)
            props.setProperty("isDirty", session.isDirty.toString())
            props.setProperty("cursorLine", session.cursorLine.toString())
            props.setProperty("cursorColumn", session.cursorColumn.toString())
            props.setProperty("scrollPosition", session.scrollPosition.toString())

            FileOutputStream(metaFile).use { out ->
                props.store(out, "Blake Editor Draft Metadata")
            }
            Unit
        }
    }

    override suspend fun loadDraft(uri: Uri?): Result<Pair<EditorSession, String>?> = withContext(Dispatchers.IO) {
        runCatching {
            val key = getDraftKey(uri)
            val contentFile = getContentFile(key)
            val metaFile = getMetaFile(key)

            if (!contentFile.exists() || !metaFile.exists()) {
                return@runCatching null
            }

            val content = contentFile.readText(Charsets.UTF_8)
            val props = Properties()
            FileInputStream(metaFile).use { input ->
                props.load(input)
            }

            val uriStr = props.getProperty("uri", "").trim()
            val restoredUri = if (uriStr.isNotEmpty()) uriParser(uriStr) else null
            val displayName = props.getProperty("displayName", "untitled.txt")
            val syntaxModeStr = props.getProperty("syntaxMode", SyntaxMode.PLAIN_TEXT.name)
            val syntaxMode = try {
                SyntaxMode.valueOf(syntaxModeStr)
            } catch (e: Exception) {
                SyntaxMode.PLAIN_TEXT
            }
            val isDirty = props.getProperty("isDirty", "true").toBoolean()
            val cursorLine = props.getProperty("cursorLine", "1").toIntOrNull() ?: 1
            val cursorColumn = props.getProperty("cursorColumn", "1").toIntOrNull() ?: 1
            val scrollPosition = props.getProperty("scrollPosition", "0").toIntOrNull() ?: 0

            val session = EditorSession(
                uri = restoredUri,
                displayName = displayName,
                syntaxMode = syntaxMode,
                isDirty = isDirty,
                cursorLine = cursorLine,
                cursorColumn = cursorColumn,
                scrollPosition = scrollPosition
            )
            Pair(session, content)
        }
    }

    override suspend fun clearDraft(uri: Uri?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val key = getDraftKey(uri)
            val contentFile = getContentFile(key)
            val metaFile = getMetaFile(key)
            if (contentFile.exists()) {
                contentFile.delete()
            }
            if (metaFile.exists()) {
                metaFile.delete()
            }
            Unit
        }
    }

    override suspend fun hasDraft(uri: Uri?): Boolean = withContext(Dispatchers.IO) {
        val key = getDraftKey(uri)
        val contentFile = getContentFile(key)
        val metaFile = getMetaFile(key)
        contentFile.exists() && metaFile.exists()
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS: Long = 800L
    }
}
