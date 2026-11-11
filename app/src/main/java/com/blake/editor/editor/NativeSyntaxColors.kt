package com.blake.editor.editor

import android.text.Editable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.blake.editor.core.model.SyntaxMode

/** Color spans on the existing Editable; never replace text or selection. */
object NativeSyntaxColors {
    private const val MAX_CHARS = 64_000
    private const val MAX_MATCHES = 1_500
    private val comments = Regex("//[^\\r\\n]*|#[^\\r\\n]*|/\\*[\\s\\S]*?\\*/")
    private val strings = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
    private val numbers = Regex("\\b\\d+(?:\\.\\d+)?\\b")
    private val keywords = Regex("\\b(?:fun|val|var|class|object|interface|import|package|return|if|else|when|for|while|try|catch|const|let|function|def|async|await|public|private|static|void|new|true|false|null|None|True|False|SELECT|FROM|WHERE|INSERT|UPDATE|DELETE)\\b")
    private val markdown = Regex("(?m)^#{1,6} .+$|\\*\\*[^*\\r\\n]+\\*\\*")

    fun apply(editable: Editable, mode: SyntaxMode, dark: Boolean) {
        editable.getSpans(0, editable.length, SyntaxColorSpan::class.java)
            .forEach { editable.removeSpan(it) }
        if (mode == SyntaxMode.PLAIN_TEXT || editable.length > MAX_CHARS) return
        val text = editable.toString()
        var count = 0
        fun color(pattern: Regex, value: Int) {
            for (match in pattern.findAll(text)) {
                if (++count > MAX_MATCHES) break
                editable.setSpan(SyntaxColorSpan(value), match.range.first,
                    match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val blue = if (dark) 0xFF8FBFFF.toInt() else 0xFF2255B8.toInt()
        val green = if (dark) 0xFF9AD9A0.toInt() else 0xFF227744.toInt()
        val orange = if (dark) 0xFFFFC18B.toInt() else 0xFF9C4A16.toInt()
        val grey = if (dark) 0xFF8899A9.toInt() else 0xFF77808A.toInt()
        if (mode == SyntaxMode.MARKDOWN) color(markdown, blue)
        else {
            color(keywords, blue)
            color(numbers, orange)
            color(strings, green)
            if (mode != SyntaxMode.JSON) color(comments, grey)
        }
    }

    private class SyntaxColorSpan(color: Int) : ForegroundColorSpan(color)
}
