package com.blake.editor.core.model

import android.net.Uri

data class EditorSession(
    val uri: Uri?,
    val displayName: String,
    val syntaxMode: SyntaxMode,
    val isDirty: Boolean = false,
    val cursorLine: Int = 1,
    val cursorColumn: Int = 1,
    val scrollPosition: Int = 0
)
