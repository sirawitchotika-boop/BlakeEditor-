package com.blake.editor.editor

import com.blake.editor.core.model.SyntaxMode
import com.blake.editor.core.model.ThemeMode
import com.blake.editor.editor.v2.CodeSyntaxScanner

/** Render-only choices for Original's native input; no document mutations. */
data class NativeTokenColors(
    val keyword: Int,
    val string: Int,
    val number: Int,
    val comment: Int,
    val tag: Int
)

object NativeCodePresentation {
    const val MAX_RENDER_TOKENS = 4_000

    fun showLineNumbers(mode: SyntaxMode): Boolean =
        mode != SyntaxMode.PLAIN_TEXT && mode != SyntaxMode.MARKDOWN

    fun shouldHighlight(mode: SyntaxMode, length: Int): Boolean =
        length <= CodeSyntaxScanner.MAX_HIGHLIGHT_CHARS &&
            CodeSyntaxScanner.isCode(mode.name)

    /** Reuses the accessible code-color families of Blake 2.0 Preview. */
    fun colorsFor(theme: ThemeMode): NativeTokenColors = when (theme) {
        ThemeMode.DARK -> NativeTokenColors(
            0xFFC4A7FF.toInt(), 0xFF8FE5BC.toInt(), 0xFFF8C776.toInt(),
            0xFF8298AC.toInt(), 0xFF89BEFF.toInt()
        )
        ThemeMode.AMOLED -> NativeTokenColors(
            0xFFBAA7FF.toInt(), 0xFF6EE7B7.toInt(), 0xFFFCCB76.toInt(),
            0xFF94A3B8.toInt(), 0xFF7DD3FC.toInt()
        )
        ThemeMode.EYE_CARE -> NativeTokenColors(
            0xFF84533D.toInt(), 0xFF386A52.toInt(), 0xFF97631D.toInt(),
            0xFF86745C.toInt(), 0xFF365F85.toInt()
        )
    }
}
