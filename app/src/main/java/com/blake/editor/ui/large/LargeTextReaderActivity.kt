package com.blake.editor.ui.large

import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blake.editor.core.model.ThemeMode
import com.blake.editor.file.PagedTextReader
import com.blake.editor.file.ReaderPageHistory
import com.blake.editor.file.TextFileEncoding
import com.blake.editor.file.TextPage
import com.blake.editor.ui.system.EdgeToEdgeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Production-available, read-only file viewer for all 2 MiB+ open paths.
 * This is intentionally separate from the editable document state, so
 * Android ACTION_VIEW never loads an oversized file into an EditText.
 */
class LargeTextReaderActivity : AppCompatActivity() {
    private val bg = 0xFF151820.toInt()
    private val surface = 0xFF232833.toInt()
    private val fg = 0xFFF1F5F9.toInt()
    private val muted = 0xFFBBC6D4.toInt()

    private lateinit var fileUri: Uri
    private lateinit var textView: TextView
    private lateinit var detail: TextView
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private lateinit var contentScroll: ScrollView
    private var currentPage: TextPage? = null
    private var encoding = TextFileEncoding.UTF8
    private val history = ReaderPageHistory()
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileUri = intent.data ?: run { finish(); return }
        EdgeToEdgeController.applyEdgeToEdge(this, ThemeMode.DARK)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        setContentView(root)
        EdgeToEdgeController.setupWindowInsets(root)

        root.addView(label("Large Text Reader", 22f, fg))
        root.addView(label("READ ONLY · Original file never changes", 13f, muted))
        val fileLabel = label("Opening document...", 16f, fg)
        root.addView(fileLabel)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(5), dp(4), dp(5))
        }
        previousButton = button("Previous") { previous() }
        nextButton = button("Next") { next() }
        controls.addView(previousButton)
        controls.addView(nextButton)
        controls.addView(button("Encoding") { chooseEncoding() })
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(controls)
        })
        detail = label("Checking file encoding...", 12f, muted)
        root.addView(detail)

        contentScroll = ScrollView(this)
        textView = label("", 16f, fg).apply {
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(32))
            typeface = android.graphics.Typeface.MONOSPACE
        }
        contentScroll.addView(textView)
        root.addView(contentScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        previousButton.isEnabled = false
        nextButton.isEnabled = false

        lifecycleScope.launch {
            val details = withContext(Dispatchers.IO) {
                runCatching {
                    val name = displayName(fileUri)
                    val input = contentResolver.openInputStream(fileUri)
                        ?: error("Storage provider could not open this document")
                    name to PagedTextReader.initialEncoding(input)
                }
            }
            details.onSuccess { (name, detected) ->
                fileLabel.text = name
                encoding = detected
                loadPage(0L)
            }.onFailure {
                report("Cannot open this file: " + it.message)
            }
        }
    }

    private fun next() {
        val page = currentPage ?: return
        if (!page.hasMore) return
        loadPage(page.nextChar, onSuccess = {
            history.recordNext(page.startChar)
        })
    }

    private fun previous() {
        val start = history.previousStart() ?: return
        loadPage(start, onSuccess = {
            history.recordPrevious()
        })
    }

    private fun chooseEncoding() {
        val options = TextFileEncoding.values()
        AlertDialog.Builder(this)
            .setTitle("Read text as")
            .setItems(options.map { it.charsetName }.toTypedArray()) { _, index ->
                val old = encoding
                encoding = options[index]
                loadPage(0L,
                    onSuccess = { history.reset() },
                    onFailure = { encoding = old })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadPage(
        start: Long,
        onSuccess: (TextPage) -> Unit = {},
        onFailure: () -> Unit = {}
    ) {
        if (busy) return
        busy = true
        previousButton.isEnabled = false
        nextButton.isEnabled = false
        detail.text = "Reading a small page..."
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val input = contentResolver.openInputStream(fileUri)
                        ?: error("Cannot reopen this file for reading")
                    PagedTextReader.readPage(input, encoding, start)
                }
            }
            busy = false
            result.onSuccess { page ->
                currentPage = page
                onSuccess(page)
                textView.text = if (page.content.isEmpty()) "(End of file)" else page.content
                contentScroll.post { contentScroll.scrollTo(0, 0) }
                detail.text = "Page " + history.pageNumber +
                    " · " + encoding.charsetName + " · characters " +
                    page.startChar + "–" + page.nextChar +
                    if (page.hasMore) " · more pages" else " · end of file"
                previousButton.isEnabled = history.canGoBack
                nextButton.isEnabled = page.hasMore
            }.onFailure { error ->
                onFailure()
                report("Could not read page: " + error.message +
                    ". Try Encoding if this is a different text encoding.")
                previousButton.isEnabled = history.canGoBack
                nextButton.isEnabled = currentPage?.hasMore == true
            }
        }
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) {
                    cursor.getString(column)?.takeIf { it.isNotBlank() }?.let {
                        return it
                    }
                }
            }
        }
        return uri.lastPathSegment ?: "Text document"
    }

    private fun report(message: String) {
        detail.text = message
        if (currentPage == null) textView.text = "The file has not been changed."
    }

    private fun label(message: String, size: Float, color: Int) =
        TextView(this).apply {
            text = message
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            gravity = Gravity.START
            setPadding(dp(9), dp(9), dp(9), dp(9))
        }

    private fun button(label: String, action: () -> Unit) =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            minWidth = dp(48)
            minHeight = dp(48)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), muted)
            }
            setOnClickListener { if (!busy) action() }
        }

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density + .5f).toInt()
}
