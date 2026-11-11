package com.blake.editor.ui.lab

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.blake.editor.ui.preview.PreviewSystemBars

/**
 * DEBUG ONLY. Standalone native EditText baseline for Thai and mixed-script
 * keyboard/caret tests. No documents are opened or written by this screen.
 *
 * This does NOT establish that the final editor engine is correct; it is a
 * physical-device test harness used before choosing the replacement for Ace.
 */
class InputLabActivity : AppCompatActivity() {
    private lateinit var editor: SelectionProbeEditText
    private lateinit var status: TextView
    private lateinit var modeButton: Button
    private lateinit var wrapButton: Button
    private var codeFont = false
    private var wrap = true
    private var fontSp = 18

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFF141820.toInt())
        }
        setContentView(root)
        PreviewSystemBars.install(this, root, 0xFF141820.toInt())

        root.addView(TextView(this).apply {
            text = "Blake 2.0 · Native Input Lab"
            setTextColor(0xFFF1F5F9.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        })

        root.addView(TextView(this).apply {
            text = "Choose a sample, tap the text, then type with a Thai keyboard. Check the visible caret, selection, deletion and wrapping."
            setTextColor(0xFFB9C2D0.toInt())
            setPadding(0, dp(7), 0, dp(7))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        })

        root.addView(scrollableButtons(InputProbeSamples.all.map { sample ->
            sample.name to { loadSample(sample.content) }
        }))

        modeButton = actionButton("Font: System") {
            codeFont = !codeFont
            editor.typeface = if (codeFont) Typeface.MONOSPACE else Typeface.DEFAULT
            modeButton.text = if (codeFont) "Font: Mono" else "Font: System"
            updateStatus()
        }
        wrapButton = actionButton("Wrap: On") {
            wrap = !wrap
            editor.setHorizontallyScrolling(!wrap)
            wrapButton.text = if (wrap) "Wrap: On" else "Wrap: Off"
            updateStatus()
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        controls.addView(modeButton)
        controls.addView(wrapButton)
        controls.addView(actionButton("A−") { changeFontSize(-1) })
        controls.addView(actionButton("A+") { changeFontSize(1) })
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(controls)
        })

        editor = SelectionProbeEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP or Gravity.START
            setSingleLine(false)
            setTextColor(0xFFF8FAFC.toInt())
            setHintTextColor(0xFF94A3B8.toInt())
            setBackgroundColor(0xFF242B35.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp.toFloat())
            minHeight = dp(190)
            hint = "Type here using your regular Android keyboard"
            setHorizontallyScrolling(false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { topMargin = dp(8) }
            onSelection = { updateStatus() }
        }
        root.addView(editor)

        status = TextView(this).apply {
            setTextColor(0xFFD5DFED.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(8), 0, dp(2))
        }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "Debug-only test. Nothing is uploaded or saved to a file."
            setTextColor(0xFF94A3B8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        })
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { updateStatus() }
        })

        loadSample(InputProbeSamples.all.first().content)
    }

    private fun loadSample(content: String) {
        editor.setText(content)
        editor.setSelection(editor.text?.length ?: 0)
        updateStatus()
    }

    private fun changeFontSize(change: Int) {
        fontSp = (fontSp + change).coerceIn(12, 28)
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp.toFloat())
        updateStatus()
    }

    private fun updateStatus() {
        if (!::editor.isInitialized || !::status.isInitialized) return
        val value = editor.text?.toString().orEmpty()
        val count = Character.codePointCount(value, 0, value.length)
        status.text = "Selection: " + editor.selectionStart + "–" + editor.selectionEnd +
            "  |  UTF-16: " + value.length + "  |  Code points: " + count +
            "\nFont: " + fontSp + "sp  |  " + (if (codeFont) "Monospace" else "System") +
            "  |  Wrap: " + (if (wrap) "On" else "Off")
    }

    private fun scrollableButtons(actions: List<Pair<String, () -> Unit>>): HorizontalScrollView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.forEach { (label, action) ->
            row.addView(actionButton(label, action).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(5) }
            })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}

private class SelectionProbeEditText(context: Context) : EditText(context) {
    var onSelection: (() -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelection?.invoke()
    }
}
