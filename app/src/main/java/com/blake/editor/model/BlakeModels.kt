package com.blake.editor.model

import java.util.UUID

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    PAPER_LIGHT("Warm Paper"),
    CHARCOAL_DARK("Charcoal Ink")
}

enum class EditorFontSize(val label: String, val editorSp: Float, val readerSp: Float) {
    SMALL("Small", 15f, 15.5f),
    MEDIUM("Medium", 17f, 17f),
    LARGE("Large", 19f, 19f),
    XLARGE("Extra Large", 21f, 21f)
}

data class ActiveDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled.md",
    val uriString: String? = null,
    val mimeType: String = "text/markdown",
    val content: String = "",
    val isDirty: Boolean = false,
    val hasVerifiedSnapshot: Boolean = false,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    val isExternal: Boolean get() = !uriString.isNullOrBlank()
    val canOverwriteDirectly: Boolean get() = isExternal && hasVerifiedSnapshot
}

data class ScratchDraft(
    val id: String,
    val title: String,
    val uriString: String?,
    val mimeType: String,
    val content: String,
    val isDirty: Boolean,
    val updatedAtMs: Long
)

data class RecentFile(
    val uriString: String,
    val title: String,
    val mimeType: String,
    val openedAtMs: Long
)
