package com.blake.editor.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.InputType
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.BaseInputConnection
import androidx.appcompat.widget.AppCompatEditText
import com.blake.editor.editor.v2.CodeLineIndex
import com.blake.editor.editor.v2.CodeToken

/**
 * Native Android input for stable Thai/English/emoji editing.
 *
 * Syntax colors and line numbers are decorations only. No highlighting
 * method calls setText(), changes the selection, or replaces the Android
 * input connection. The IME retains ownership of composing text.
 */
class NativeInputEditText(context: Context) : AppCompatEditText(context) {
    // Android may invoke these overrides while the TextView constructor runs.
    private var lineIndex: CodeLineIndex? = null
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gutterPaint = Paint()
    private val highlightSpans = mutableListOf<ForegroundColorSpan>()
    private val gutterWidth = dp(52)
    private val gutterGap = dp(9)
    private var showLineNumbers = false
    private var tokenPalette = NativeCodeVisualPolicy.tokenPalette(
        com.blake.editor.core.model.ThemeMode.DARK
    )

    var onCursorMoved: ((line: Int, column: Int) -> Unit)? = null

    init {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        setSingleLine(false)
        setHorizontallyScrolling(false)
        gravity = Gravity.TOP or Gravity.START
        typeface = android.graphics.Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = false
        val inset = dp(12)
        setPadding(inset, inset, inset, inset)
        lineIndex = CodeLineIndex().also { it.rebuild(text ?: "") }
        numberPaint.typeface = android.graphics.Typeface.MONOSPACE
    }

    override fun onTextChanged(text: CharSequence?, start: Int,
        before: Int, count: Int
    ) {
        super.onTextChanged(text, start, before, count)
        // Preserve the real UTF-16 offsets, including Thai combining
        // characters and surrogate pairs. Wrapped rows aren't new lines.
        lineIndex?.rebuild(text ?: "")
        if (showLineNumbers) invalidate()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        val index = lineIndex ?: return
        val pos = selEnd.coerceIn(0, text?.length ?: 0)
        val line = index.lineAt(pos)
        val col = pos - index.lineStartAt(pos) + 1
        onCursorMoved?.invoke(line, col)
    }

    /** Only adjusts decoration padding when the code mode actually changes. */
    fun configureCodeVisuals(show: Boolean, palette: NativeTokenPalette) {
        tokenPalette = palette
        if (showLineNumbers != show) {
            showLineNumbers = show
            val left = dp(12) + if (show) gutterWidth else 0
            // Do not touch native text, focus, selection, or composing spans.
            setPadding(left, paddingTop, paddingRight, paddingBottom)
        }
        numberPaint.color = palette.gutterText
        numberPaint.textSize = textSize * 0.73f
        invalidate()
    }

    /**
     * Update ONLY our own ForegroundColorSpan objects in the existing
     * Editable. Avoid changing active IME composition (Thai/Gboard).
     *
     * Returns false when the keyboard is composing; the caller can
     * reschedule after the next text or lifecycle event.
     */
    fun applyCodeTokens(tokens: List<CodeToken>): Boolean {
        val editable = text ?: return false
        if (BaseInputConnection.getComposingSpanStart(editable) >= 0 ||
            BaseInputConnection.getComposingSpanEnd(editable) >= 0
        ) return false

        for (span in highlightSpans) editable.removeSpan(span)
        highlightSpans.clear()
        for (token in tokens) {
            if (token.start < 0 || token.start >= token.end ||
                token.end > editable.length) continue
            val span = ForegroundColorSpan(tokenPalette.forKind(token.kind))
            editable.setSpan(span, token.start, token.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            highlightSpans.add(span)
        }
        invalidate()
        return true
    }

    /** Clear only the syntax decorations, never Android's IME spans. */
    fun clearCodeTokens(): Boolean = applyCodeTokens(emptyList())

    override fun onDraw(canvas: Canvas) {
        val index = lineIndex
        val textLayout = layout
        if (showLineNumbers && index != null && textLayout != null &&
            height > 0 && textLayout.lineCount > 0
        ) {
            val left = scrollX.toFloat()
            val top = scrollY.toFloat()
            gutterPaint.color = tokenPalette.gutterBackground
            canvas.drawRect(left, top, left + gutterWidth,
                top + height.toFloat(), gutterPaint)
            numberPaint.color = tokenPalette.gutterText
            numberPaint.textSize = textSize * 0.73f

            val first = textLayout.getLineForVertical(scrollY.coerceAtLeast(0))
            val last = textLayout.getLineForVertical(
                (scrollY + height).coerceAtMost(textLayout.height)
            )
            for (visual in first..last) {
                val start = textLayout.getLineStart(visual)
                // A visual soft-wrap must not increment the line number.
                if (!index.isLogicalLineStart(start)) continue
                val number = index.lineAt(start).toString()
                val x = scrollX + gutterWidth - gutterGap -
                    numberPaint.measureText(number)
                val baseline = textLayout.getLineBaseline(visual) + totalPaddingTop
                canvas.drawText(number, x, baseline.toFloat(), numberPaint)
            }
        }
        super.onDraw(canvas)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + .5f).toInt()
}
