package com.blake.editor.ui.editor

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.widget.ScrollView
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.blake.editor.editor.TextMatch
import com.blake.editor.ui.theme.BlakePalette
import com.blake.editor.ui.theme.LocalBlakePalette

/**
 * Lightweight bridge allowing Compose toolbar actions to query the live
 * selection inside the native EditText without triggering recomposition on every caret move.
 */
class EditorCanvasController {
    internal var editText: BlakeNativeEditText? = null

    fun currentSelection(): Pair<Int, Int> {
        val view = editText ?: return 0 to 0
        val len = view.text?.length ?: 0
        val start = view.selectionStart.coerceIn(0, len)
        val end = view.selectionEnd.coerceIn(0, len)
        return start to end
    }

    fun requestEditorFocus() {
        editText?.requestFocus()
    }
}

/**
 * Borderless native Android EditText styled for Editorial Paper & Ink writing.
 * Preserves bulletproof Thai/English IME behavior while supporting live search
 * highlights and instant smooth scroll-to-match above the soft keyboard.
 */
internal class BlakeNativeEditText(context: Context) : AppCompatEditText(context) {
    private val searchSpans = ArrayList<Any>()
    var suppressTextWatcher: Boolean = false

    init {
        background = null
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setSingleLine(false)
        setHorizontallyScrolling(false)
        gravity = Gravity.TOP or Gravity.START
        typeface = Typeface.SANS_SERIF
        setLineSpacing(0f, 1.45f)
        hint = "Start writing…"
        val horizontal = dp(20)
        val top = dp(18)
        val bottom = dp(48)
        setPadding(horizontal, top, horizontal, bottom)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isFallbackLineSpacing = true
        }
    }

    fun applyTheme(palette: BlakePalette, fontSizeSp: Float) {
        setTextColor(palette.ink.toArgb())
        setHintTextColor(palette.inkMuted.toArgb())
        highlightColor = palette.accentSoft.toArgb()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cursor = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setSize(dp(2), dp(22))
                setColor(palette.accent.toArgb())
            }
            textCursorDrawable = cursor
        }
    }

    fun applySearchHighlights(
        matches: List<TextMatch>,
        activeIndex: Int,
        palette: BlakePalette
    ) {
        val editable = text ?: return
        if (BaseInputConnection.getComposingSpanStart(editable) >= 0 ||
            BaseInputConnection.getComposingSpanEnd(editable) >= 0
        ) {
            return
        }
        clearSearchHighlights(editable)
        if (matches.isEmpty()) return

        val normalBg = palette.searchMatchBg.toArgb()
        val activeBg = palette.searchActiveBg.toArgb()
        val activeFg = palette.searchActiveText.toArgb()
        val maxLen = editable.length

        for (index in matches.indices) {
            val match = matches[index]
            if (match.start < 0 || match.end > maxLen || match.start >= match.end) continue
            if (index == activeIndex) {
                val bgSpan = BackgroundColorSpan(activeBg)
                val fgSpan = ForegroundColorSpan(activeFg)
                editable.setSpan(bgSpan, match.start, match.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                editable.setSpan(fgSpan, match.start, match.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                searchSpans.add(bgSpan)
                searchSpans.add(fgSpan)
            } else {
                val bgSpan = BackgroundColorSpan(normalBg)
                editable.setSpan(bgSpan, match.start, match.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                searchSpans.add(bgSpan)
            }
        }
    }

    private fun clearSearchHighlights(editable: Editable) {
        for (span in searchSpans) {
            editable.removeSpan(span)
        }
        searchSpans.clear()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}

private class BlakeEditorContainer(context: Context) : ScrollView(context) {
    val editText = BlakeNativeEditText(context)
    var lastAppliedDocVersion: Long = -1L
    var lastAppliedJumpNonce: Long = -1L
    var lastMatchesSignature: Int = 0

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = true
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        addView(
            editText,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    /**
     * Smoothly scrolls the active match into the upper-middle (~35% from top) of
     * the currently visible unobstructed ScrollView above the soft keyboard.
     */
    fun scrollToMatch(match: TextMatch) {
        post {
            val textLayout = editText.layout ?: return@post
            val textLen = editText.text?.length ?: 0
            if (textLen == 0) return@post
            val safeOffset = match.start.coerceIn(0, textLen)
            val line = textLayout.getLineForOffset(safeOffset)
            val matchTop = textLayout.getLineTop(line) + editText.paddingTop
            val visibleHeight = height
            if (visibleHeight <= 0) return@post
            val targetY = (matchTop - (visibleHeight * 0.35f).toInt()).coerceAtLeast(0)
            smoothScrollTo(0, targetY)
        }
    }
}

@Composable
fun BlakeEditorCanvas(
    content: String,
    documentVersion: Long,
    pendingSelection: Pair<Int, Int>?,
    fontSizeSp: Float,
    searchMatches: List<TextMatch>,
    activeMatchIndex: Int,
    searchJumpNonce: Long,
    controller: EditorCanvasController,
    onContentChanged: (String) -> Unit,
    onPendingSelectionConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalBlakePalette.current
    val latestOnChange = remember { arrayOf(onContentChanged) }.also { it[0] = onContentChanged }
    val latestOnSelectionConsumed = remember { arrayOf(onPendingSelectionConsumed) }
        .also { it[0] = onPendingSelectionConsumed }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            BlakeEditorContainer(context).apply {
                controller.editText = editText
                editText.applyTheme(palette, fontSizeSp)
                editText.suppressTextWatcher = true
                editText.setText(content)
                editText.suppressTextWatcher = false
                lastAppliedDocVersion = documentVersion

                editText.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        if (!editText.suppressTextWatcher) {
                            latestOnChange[0](s?.toString().orEmpty())
                        }
                    }
                })
            }
        },
        update = { container ->
            controller.editText = container.editText
            container.editText.applyTheme(palette, fontSizeSp)

            // Apply programmatic text changes (Open, New, Undo, Redo, Replace, Markdown Format)
            if (container.lastAppliedDocVersion != documentVersion) {
                container.lastAppliedDocVersion = documentVersion
                val currentText = container.editText.text?.toString().orEmpty()
                if (currentText != content) {
                    container.editText.suppressTextWatcher = true
                    container.editText.setText(content)
                    container.editText.suppressTextWatcher = false
                }
                if (pendingSelection != null) {
                    val len = container.editText.text?.length ?: 0
                    val s = pendingSelection.first.coerceIn(0, len)
                    val e = pendingSelection.second.coerceIn(0, len)
                    container.editText.setSelection(s, e)
                    latestOnSelectionConsumed[0]()
                }
                container.lastMatchesSignature = 0
            } else if (pendingSelection != null) {
                val len = container.editText.text?.length ?: 0
                val s = pendingSelection.first.coerceIn(0, len)
                val e = pendingSelection.second.coerceIn(0, len)
                container.editText.setSelection(s, e)
                latestOnSelectionConsumed[0]()
            }

            // Apply live search highlights when matches, active index, or palette changes
            val signature = 31 * (31 * (31 * searchMatches.hashCode() + activeMatchIndex) + palette.isDark.hashCode()) + content.length
            if (container.lastMatchesSignature != signature) {
                container.lastMatchesSignature = signature
                container.editText.applySearchHighlights(searchMatches, activeMatchIndex, palette)
            }

            // Immediately jump & scroll the active match cleanly into the visible viewport
            if (searchJumpNonce != container.lastAppliedJumpNonce) {
                container.lastAppliedJumpNonce = searchJumpNonce
                val activeMatch = searchMatches.getOrNull(activeMatchIndex)
                if (activeMatch != null) {
                    container.scrollToMatch(activeMatch)
                }
            }
        }
    )
}
