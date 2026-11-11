package com.blake.editor.editor

import com.blake.editor.core.model.ThemeMode
import com.blake.editor.editor.v2.CodeSyntaxScanner
import com.blake.editor.editor.v2.CodeTokenKind

/**
 * Native code decorations never change the document or the input connection.
 * Use a small bounded file for expensive scanning and line-number rendering.
 */
object NativeCodeVisualPolicy {
    const val HIGHLIGHT_IDLE_MILLIS = 420L

    // Other recognized source types get a line-number gutter even before
    // a dedicated tokenizer is available for their syntax.
    private val extraCodeModes = setOf("SQL", "SHELL", "C_CPP", "RUST", "GO")

    fun shouldDecorate(language: String, length: Int): Boolean =
        (CodeSyntaxScanner.isCode(language) || language in extraCodeModes) &&
            length <= CodeSyntaxScanner.MAX_HIGHLIGHT_CHARS

    fun shouldHighlight(language: String, length: Int): Boolean =
        CodeSyntaxScanner.isCode(language) &&
            length <= CodeSyntaxScanner.MAX_HIGHLIGHT_CHARS

    fun tokenPalette(theme: ThemeMode): NativeTokenPalette = when (theme) {
        ThemeMode.DARK -> NativeTokenPalette(
            keyword = 0xFF93C5FD.toInt(),
            string = 0xFF86EFAC.toInt(),
            number = 0xFFFBBF24.toInt(),
            comment = 0xFF94A3B8.toInt(),
            tag = 0xFF67E8F9.toInt(),
            gutterBackground = 0xFF1D2330.toInt(),
            gutterText = 0xFFA2B2C4.toInt()
        )
        ThemeMode.AMOLED -> NativeTokenPalette(
            keyword = 0xFF93C5FD.toInt(),
            string = 0xFF86EFAC.toInt(),
            number = 0xFFFBBF24.toInt(),
            comment = 0xFF94A3B8.toInt(),
            tag = 0xFF67E8F9.toInt(),
            gutterBackground = 0xFF0E0E0E.toInt(),
            gutterText = 0xFFACB6C5.toInt()
        )
        ThemeMode.EYE_CARE -> NativeTokenPalette(
            keyword = 0xFF174EA6.toInt(),
            string = 0xFF276749.toInt(),
            number = 0xFF995517.toInt(),
            comment = 0xFF746D63.toInt(),
            tag = 0xFF7C3FA1.toInt(),
            gutterBackground = 0xFFEDE4D3.toInt(),
            gutterText = 0xFF685D4F.toInt()
        )
    }
}

data class NativeTokenPalette(
    val keyword: Int,
    val string: Int,
    val number: Int,
    val comment: Int,
    val tag: Int,
    val gutterBackground: Int,
    val gutterText: Int
) {
    fun forKind(kind: CodeTokenKind): Int = when (kind) {
        CodeTokenKind.KEYWORD -> keyword
        CodeTokenKind.STRING -> string
        CodeTokenKind.NUMBER -> number
        CodeTokenKind.COMMENT -> comment
        CodeTokenKind.TAG -> tag
    }
}
