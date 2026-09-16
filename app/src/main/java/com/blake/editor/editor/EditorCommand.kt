package com.blake.editor.editor

import com.blake.editor.core.model.SyntaxMode
import com.blake.editor.core.model.ThemeMode

sealed class EditorCommand {
    data class SetContent(val text: String, val syntaxMode: SyntaxMode) : EditorCommand()
    data class SetSyntaxMode(val syntaxMode: SyntaxMode) : EditorCommand()
    data class SetTheme(val themeMode: ThemeMode) : EditorCommand()
    data class SetReadOnly(val isReadOnly: Boolean) : EditorCommand()
    data class SetFontSize(val sizeSp: Float) : EditorCommand()
    data class GoToLine(val line: Int) : EditorCommand()
    data class Find(val query: String, val caseSensitive: Boolean = false, val wholeWord: Boolean = false) : EditorCommand()
    data class Replace(val query: String, val replacement: String, val replaceAll: Boolean = false) : EditorCommand()
    object Undo : EditorCommand()
    object Redo : EditorCommand()
    object RequestContent : EditorCommand()
    data class ShowPreview(val markdownText: String) : EditorCommand()
    object HidePreview : EditorCommand()
}
