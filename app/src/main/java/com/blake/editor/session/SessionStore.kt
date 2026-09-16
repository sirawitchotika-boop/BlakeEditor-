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
import java.util.Properties

class SessionStore(
    private val sessionFile: File,
    private val uriParser: (String) -> Uri? = { Uri.parse(it) }
) {

    constructor(context: Context) : this(
        File(context.filesDir, "session_state.properties")
    )

    suspend fun saveSession(session: EditorSession): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            sessionFile.parentFile?.mkdirs()
            val props = Properties()
            props.setProperty("uri", session.uri?.toString() ?: "")
            props.setProperty("displayName", session.displayName)
            props.setProperty("syntaxMode", session.syntaxMode.name)
            props.setProperty("isDirty", session.isDirty.toString())
            props.setProperty("cursorLine", session.cursorLine.toString())
            props.setProperty("cursorColumn", session.cursorColumn.toString())
            props.setProperty("scrollPosition", session.scrollPosition.toString())

            FileOutputStream(sessionFile).use { out ->
                props.store(out, "Blake Editor Session State")
            }
            Unit
        }
    }

    suspend fun loadSession(): Result<EditorSession?> = withContext(Dispatchers.IO) {
        runCatching {
            if (!sessionFile.exists() || sessionFile.length() == 0L) {
                return@runCatching null
            }
            val props = Properties()
            FileInputStream(sessionFile).use { input ->
                props.load(input)
            }

            val uriStr = props.getProperty("uri", "").trim()
            val uri = if (uriStr.isNotEmpty()) uriParser(uriStr) else null
            val displayName = props.getProperty("displayName", "untitled.txt")
            val syntaxModeStr = props.getProperty("syntaxMode", SyntaxMode.PLAIN_TEXT.name)
            val syntaxMode = try {
                SyntaxMode.valueOf(syntaxModeStr)
            } catch (e: Exception) {
                SyntaxMode.PLAIN_TEXT
            }
            val isDirty = props.getProperty("isDirty", "false").toBoolean()
            val cursorLine = props.getProperty("cursorLine", "1").toIntOrNull() ?: 1
            val cursorColumn = props.getProperty("cursorColumn", "1").toIntOrNull() ?: 1
            val scrollPosition = props.getProperty("scrollPosition", "0").toIntOrNull() ?: 0

            EditorSession(
                uri = uri,
                displayName = displayName,
                syntaxMode = syntaxMode,
                isDirty = isDirty,
                cursorLine = cursorLine,
                cursorColumn = cursorColumn,
                scrollPosition = scrollPosition
            )
        }
    }

    suspend fun clearSession(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (sessionFile.exists()) {
                sessionFile.delete()
            }
            Unit
        }
    }

    suspend fun hasSession(): Boolean = withContext(Dispatchers.IO) {
        sessionFile.exists() && sessionFile.length() > 0L
    }
}
