package com.blake.editor.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatEditText
import com.blake.editor.editor.v2.CodeLineIndex

/**
 * Real Android input connection for stable Thai/English/emoji editing.
 *
 * Ace/WebView must not own the active input connection. This view does not
 * rebuild its text in response to its own edits and relies on Android IME
 * composition, cursor, selection and wrapped-line handling.
 */
class NativeInputEditText(context: Context) : AppCompatEditText(context) {
    // Android can call subclass methods inside TextView's constructor.
    private var lineIndex: CodeLineIndex? = null

    var showLineNumbers: Boolean = true
        set(value) { field = value; invalidate() }
    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8795A6.toInt()
        textSize = 12f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.RIGHT
    }
    private val gutterWidth = (54 * resources.displayMetrics.density + .5f).toInt()

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
        val inset = (12 * resources.displayMetrics.density + .5f).toInt()
        setPadding(gutterWidth, inset, inset, inset)
        lineIndex = CodeLineIndex().also { it.rebuild(text ?: "") }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showLineNumbers) return
        val layout = layout ?: return
        val text = text ?: return
        val first = layout.getLineForVertical(scrollY.coerceAtLeast(0))
        val last = layout.getLineForVertical((scrollY + height).coerceAtMost(layout.height))
        val x = scrollX + gutterWidth - 12f * resources.displayMetrics.density
        val index = lineIndex ?: return
        for (visual in first..last) {
            val start = layout.getLineStart(visual)
            if (visual > 0 && start == layout.getLineEnd(visual - 1) &&
                start > 0 && text[start - 1] != '\n' && text[start - 1] != '\r') continue
            val number = index.lineAt(start)
            canvas.drawText(number.toString(), x,
                (totalPaddingTop + layout.getLineBaseline(visual) - scrollY).toFloat(),
                gutterPaint)
        }
    }

    override fun onTextChanged(text: CharSequence?, start: Int,
        before: Int, count: Int
    ) {
        super.onTextChanged(text, start, before, count)
        lineIndex?.rebuild(text ?: "")
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        val index = lineIndex ?: return
        val pos = selEnd.coerceIn(0, text?.length ?: 0)
        val line = index.lineAt(pos)
        val col = pos - index.lineStartAt(pos) + 1
        onCursorMoved?.invoke(line, col)
    }
}
