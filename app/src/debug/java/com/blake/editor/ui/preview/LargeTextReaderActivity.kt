package com.blake.editor.ui.preview

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
import com.blake.editor.file.PagedTextReader
import com.blake.editor.file.ReaderPageHistory
import com.blake.editor.file.TextFileEncoding
import com.blake.editor.file.TextPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debug-only bounded large text viewer. Read-only by design: no editing
 * controls and no file output streams.
 */
class LargeTextReaderActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var content: TextView
    private lateinit var detail: TextView
    private lateinit var backButton: Button
    private lateinit var nextButton: Button
    private lateinit var scroll: ScrollView
    private lateinit var uri: Uri
    private var encoding = TextFileEncoding.UTF8
    private var currentPage: TextPage? = null
    private val history = ReaderPageHistory()
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uri = intent.data ?: run {
            finish()
            return
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(12), dp(10), dp(10))
            setBackgroundColor(0xFF151820.toInt())
        }
        setContentView(root)
        root.addView(label("Large Text Reader", 22f, 0xFFF1F5F9.toInt()))
        root.addView(label("READ ONLY · Never changes your file", 13f, 0xFFB7C5D5.toInt()))
        root.addView(label(fileName(), 16f, 0xFFF1F5F9.toInt()))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        backButton = button("Previous") { previous() }
        nextButton = button("Next") { next() }
        actions.addView(backButton)
        actions.addView(nextButton)
        actions.addView(button("Encoding") { chooseEncoding() })
        root.addView(HorizontalScrollView(this).apply { addView(actions) })
        detail = label("Checking encoding...", 12f, 0xFFB7C5D5.toInt())
        root.addView(detail)
        scroll = ScrollView(this)
        content = label("", 16f, 0xFFF1F5F9.toInt()).apply {
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(30))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        lifecycleScope.launch {
            val firstEncoding = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = contentResolver.openInputStream(uri)
                        ?: error("Storage provider could not open the document")
                    PagedTextReader.initialEncoding(stream)
                }
            }
            firstEncoding.onSuccess { detected ->
                encoding = detected
                loadPage(0L)
            }.onFailure { report("Cannot open document: " + it.message) }
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
        val offset = history.previousStart() ?: return
        loadPage(offset, onSuccess = {
            history.recordPrevious()
        })
    }

    private fun chooseEncoding() {
        val choices = TextFileEncoding.values()
        AlertDialog.Builder(this)
            .setTitle("Read text as")
            .setItems(choices.map { it.charsetName }.toTypedArray()) { _, index ->
                val selected = choices[index]
                val former = encoding
                encoding = selected
                loadPage(0L, onSuccess = {
                    history.reset()
                }, onFailure = { encoding = former })
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun loadPage(
        start: Long,
        onSuccess: (TextPage) -> Unit = {},
        onFailure: () -> Unit = {}
    ) {
        if (busy) return
        busy = true
        backButton.isEnabled = false
        nextButton.isEnabled = false
        detail.text = "Reading one small page..."
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val input = contentResolver.openInputStream(uri)
                        ?: error("Storage provider cannot open the file")
                    PagedTextReader.readPage(input, encoding, start)
                }
            }
            busy = false
            result.onSuccess { page ->
                currentPage = page
                onSuccess(page)
                content.text = if (page.content.isEmpty()) "(End of file)" else page.content
                scroll.post { scroll.scrollTo(0, 0) }
                detail.text = "Page " + history.pageNumber +
                    " · " + encoding.charsetName +
                    " · characters " + page.startChar + "–" + page.nextChar +
                    if (page.hasMore) " · more pages" else " · end"
                backButton.isEnabled = history.canGoBack
                nextButton.isEnabled = page.hasMore
            }.onFailure {
                onFailure()
                report("Could not read page: " + it.message +
                    ". Try Encoding if the text uses a different character set.")
                backButton.isEnabled = history.canGoBack
                nextButton.isEnabled = currentPage?.hasMore == true
            }
        }
    }

    private fun report(message: String) {
        detail.text = message
        if (currentPage == null) content.text = "The file was not changed."
    }

    private fun fileName(): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && cursor.moveToFirst()) {
                    cursor.getString(i)?.takeIf(String::isNotBlank)?.let { return it }
                }
            }
        return uri.lastPathSegment ?: "Document"
    }

    private fun label(s: String, size: Float, color: Int): TextView =
        TextView(this).apply {
            text = s
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            gravity = Gravity.START
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

    private fun button(s: String, callback: () -> Unit): Button =
        Button(this).apply {
            text = s
            isAllCaps = false
            setOnClickListener { if (!busy) callback() }
        }

    private fun dp(n: Int): Int =
        (n * resources.displayMetrics.density + 0.5f).toInt()
}
