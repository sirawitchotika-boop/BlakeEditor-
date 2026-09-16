package com.blake.editor.ui.main

import android.net.Uri
import com.blake.editor.core.model.SyntaxMode
import com.blake.editor.core.model.ThemeMode

data class MainUiState(
    val currentUri: Uri? = null,
    val fileName: String = "untitled.txt",
    val content: String = "",
    val syntaxMode: SyntaxMode = SyntaxMode.PLAIN_TEXT,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val isDirty: Boolean = false,
    val isLoading: Boolean = false,
    val isPreviewing: Boolean = false,
    val cursorLine: Int = 1,
    val cursorColumn: Int = 1,
    val scrollPosition: Int = 0,
    val statusMessage: String = "Blake Editor   Phase 8 Ready"
)
