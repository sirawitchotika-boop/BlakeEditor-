package com.blake.editor.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.InputType
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.BaseInputConnection
import androidx.appcompat.widget.AppCompatEditText
import com.blake.editor.editor.v2.CodeLineIndex
import com.blake.editor.editor.v2.CodeToken
import com.blake.editor.editor.v2.CodeTokenKind
import kotlin.math.ceil

/**
 * Native Android typing surface for Thai/English/emoji.
 *
 * Code colors are presentation-only spans and line numbers are canvas drawings.
 * Neither feature calls setText or moves the caret after IME edits.
 */
class NativeInputEditText(context: Context) : AppCompatEditText(context) {
    // TextView may call subclass methods during its own constructor.
    private var lineIndex: CodeLineIndex? = null
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gutterPaint = Paint()
    private val syntaxSpans = mutableListOf<ForegroundColorSpan>()
    private val inset = dp(12)
    private val numberGap = dp(10)
    private var gutterWidth = dp(36)
    private var showGutter = false
    private var gutterColor = 0xFF1E1E1E.toInt()
    private var numberColor = 0xFFAAAAAA.toInt()

    var onCursorMoved: ((line: Int, column: Int) -> Unit)? = null

    init {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        setSingleLine(false)
        setHorizontallyScrolling(false)
        gravity = Gravity.TOP or Gravity.START
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = false
        setPadding(inset, inset, inset, inset)
        linePaint.typeface = Typeface.MONOSPACE
        linePaint.textSize = textSize * 0.75f
        lineIndex = CodeLineIndex().also { it.rebuild(text ?: "") }
    }

    override fun onTextChanged(text: CharSequence?, start: Int,
        before: Int, count: Int
    ) {
        super.onTextChanged(text, start, before, count)
        lineIndex?.rebuild(text ?: "")
        if (showGutter && lineIndex != null) updateGutterWidth()
        invalidate()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        val index = lineIndex ?: return
        val pos = selEnd.coerceIn(0, text?.length ?: 0)
        onCursorMoved?.invoke(index.lineAt(pos),
            pos - index.lineStartAt(pos) + 1)
    }

    /** Keep the gutter attached to logical lines even when code soft-wraps. */
    fun configureGutter(enabled: Boolean, background: Int, foreground: Int) {
        showGutter = enabled
        gutterColor = background
        numberColor = foreground
        linePaint.color = foreground
        updateGutterWidth()
        invalidate()
    }

    private fun updateGutterWidth() {
        val digits = (lineIndex?.lineCount ?: 1).toString().length
        linePaint.textSize = textSize * 0.75f
        val width = (ceil(linePaint.measureText("8".repeat(digits)).toDouble())
            .toInt() + numberGap * 2).coerceAtLeast(dp(36))
        if (gutterWidth != width) gutterWidth = width
        val left = inset + if (showGutter) gutterWidth else 0
        if (paddingLeft != left) {
            setPadding(left, paddingTop, paddingRight, paddingBottom)
        }
    }

    /** Remove only spans created by this editor, never IME composing spans. */
    fun clearSyntaxTokens() {
        val editable = text ?: return
        for (span in syntaxSpans) editable.removeSpan(span)
        syntaxSpans.clear()
    }

    /** Update colors without replacing text or adjusting Android's selection. */
    fun applySyntaxTokens(tokens: List<CodeToken>, colors: NativeTokenColors) {
        val editable = text ?: return
        if (BaseInputConnection.getComposingSpanStart(editable) >= 0 ||
            BaseInputConnection.getComposingSpanEnd(editable) >= 0) return
        clearSyntaxTokens()
        if (tokens.size > NativeCodePresentation.MAX_RENDER_TOKENS) return
        for (token in tokens) {
            if (token.start < 0 || token.end > editable.length ||
                token.start >= token.end) continue
            val color = when (token.kind) {
                CodeTokenKind.KEYWORD -> colors.keyword
                CodeTokenKind.STRING -> colors.string
                CodeTokenKind.NUMBER -> colors.number
                CodeTokenKind.COMMENT -> colors.comment
                CodeTokenKind.TAG -> colors.tag
            }
            val span = ForegroundColorSpan(color)
            editable.setSpan(span, token.start, token.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            syntaxSpans.add(span)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val index = lineIndex ?: return
        if (!showGutter) return
        val textLayout = layout ?: return
        gutterPaint.color = gutterColor
        val left = scrollX.toFloat()
        val top = scrollY.toFloat()
        canvas.drawRect(left, top, left + gutterWidth, top + height, gutterPaint)
        linePaint.color = numberColor
        linePaint.textSize = textSize * 0.75f
        val first = textLayout.getLineForVertical(scrollY.coerceAtLeast(0))
        val last = textLayout.getLineForVertical(
            (scrollY + height).coerceAtMost(textLayout.height))
        for (visual in first..last) {
            val start = textLayout.getLineStart(visual)
            if (!index.isLogicalLineStart(start)) continue
            val number = index.lineAt(start).toString()
            val x = left + gutterWidth - numberGap - linePaint.measureText(number)
            val baseline = textLayout.getLineBaseline(visual) + totalPaddingTop
            canvas.drawText(number, x, baseline.toFloat(), linePaint)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
