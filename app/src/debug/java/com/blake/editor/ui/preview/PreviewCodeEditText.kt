package com.blake.editor.ui.preview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.widget.EditText
import com.blake.editor.editor.v2.CodeLineIndex
import com.blake.editor.editor.v2.CodeToken
import com.blake.editor.editor.v2.CodeTokenKind

/**
 * Native Android EditText with an optional drawn gutter and color spans.
 * Does not replace Android input/caret/selection behavior.
 */
class PreviewCodeEditText(context: Context) : EditText(context) {
    var onSelection: (() -> Unit)? = null

    private val lines = CodeLineIndex()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gutterPaint = Paint()
    private val tokenSpans = mutableListOf<ForegroundColorSpan>()
    private val gap = dp(10)
    private var gutterWidth = dp(50)
    private var lineNumbersEnabled = false
    private var gutterColor: Int = 0xFF1A202A.toInt()
    private var numberColor: Int = 0xFF8298AC.toInt()

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelection?.invoke()
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int,
        lengthAfter: Int
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        lines.rebuild(text ?: "")
        invalidate()
    }

    fun configureCodeGutter(show: Boolean, palette: PreviewPalette) {
        val leftBefore = if (lineNumbersEnabled) gutterWidth + dp(12) else dp(12)
        val userRight = paddingRight.takeIf { it > 0 } ?: dp(12)
        val top = paddingTop.takeIf { it > 0 } ?: dp(12)
        val bottom = paddingBottom.takeIf { it > 0 } ?: dp(12)
        lineNumbersEnabled = show
        gutterWidth = dp(50)
        gutterColor = palette.gutter
        numberColor = palette.secondary
        linePaint.color = numberColor
        linePaint.typeface = android.graphics.Typeface.MONOSPACE
        linePaint.textSize = textSize * 0.75f
        setPadding(if (show) gutterWidth + dp(12) else dp(12),
            top, userRight, bottom)
        if (leftBefore != paddingLeft) requestLayout()
        lines.rebuild(text ?: "")
        invalidate()
    }

    fun applyCodeTokens(tokens: List<CodeToken>, palette: PreviewPalette) {
        val editable = text ?: return
        for (span in tokenSpans) editable.removeSpan(span)
        tokenSpans.clear()
        for (token in tokens) {
            if (token.start >= token.end || token.end > editable.length) continue
            val color = when (token.kind) {
                CodeTokenKind.KEYWORD -> palette.keyword
                CodeTokenKind.STRING -> palette.string
                CodeTokenKind.NUMBER -> palette.number
                CodeTokenKind.COMMENT -> palette.comment
                CodeTokenKind.TAG -> palette.tag
            }
            val span = ForegroundColorSpan(color)
            editable.setSpan(span, token.start, token.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            tokenSpans.add(span)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (lineNumbersEnabled) {
            val textLayout = layout
            val displayHeight = height
            if (textLayout != null && displayHeight > 0) {
                val left = scrollX.toFloat()
                val top = scrollY.toFloat()
                gutterPaint.color = gutterColor
                canvas.drawRect(left, top, left + gutterWidth,
                    top + displayHeight, gutterPaint)
                linePaint.color = numberColor
                linePaint.textSize = textSize * 0.75f
                val first = textLayout.getLineForVertical(scrollY.coerceAtLeast(0))
                val last = textLayout.getLineForVertical(
                    (scrollY + displayHeight).coerceAtMost(textLayout.height)
                )
                for (visual in first..last) {
                    val start = textLayout.getLineStart(visual)
                    if (!lines.isLogicalLineStart(start)) continue
                    val number = lines.lineAt(start).toString()
                    val x = scrollX + gutterWidth - gap -
                        linePaint.measureText(number)
                    val baseline = textLayout.getLineBaseline(visual) +
                        totalPaddingTop
                    canvas.drawText(number, x, baseline.toFloat(), linePaint)
                }
            }
        }
        super.onDraw(canvas)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
