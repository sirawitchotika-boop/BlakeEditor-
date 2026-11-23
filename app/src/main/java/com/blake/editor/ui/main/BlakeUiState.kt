package com.blake.editor.ui.main

import com.blake.editor.editor.TextMatch
import com.blake.editor.model.ActiveDocument
import com.blake.editor.model.AppThemeMode
import com.blake.editor.model.EditorFontSize
import com.blake.editor.model.RecentFile
import com.blake.editor.model.ScratchDraft

data class FindReplaceState(
    val isOpen: Boolean = false,
    val showReplace: Boolean = false,
    val query: String = "",
    val replacement: String = "",
    val matchCase: Boolean = false,
    val matches: List<TextMatch> = emptyList(),
    val activeIndex: Int = -1,
    val jumpNonce: Long = 0L
)

data class BlakeUiState(
    val document: ActiveDocument = ActiveDocument(),
    val documentVersion: Long = 1L,
    val pendingSelection: Pair<Int, Int>? = null,
    val isReadMode: Boolean = false,
    val isBusy: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val find: FindReplaceState = FindReplaceState(),
    val drafts: List<ScratchDraft> = emptyList(),
    val recentFiles: List<RecentFile> = emptyList(),
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val fontSize: EditorFontSize = EditorFontSize.MEDIUM,
    val showMarkdownBar: Boolean = true,
    val isDocumentsSheetOpen: Boolean = false,
    val isSettingsSheetOpen: Boolean = false,
    val isRenameDialogOpen: Boolean = false,
    val snackbarMessage: String? = null
)

sealed interface BlakeSideEffect {
    data class LaunchSaveAs(val suggestedName: String, val mimeType: String) : BlakeSideEffect
    data class OpenLargeReader(val uriString: String) : BlakeSideEffect
    data class ShareContent(val title: String, val text: String) : BlakeSideEffect
}
