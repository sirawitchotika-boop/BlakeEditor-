package com.blake.editor.ui.preview

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.blake.editor.file.DecodedTextFile
import com.blake.editor.file.EncodingDetection
import com.blake.editor.file.TextEncodingCodec
import com.blake.editor.file.TextFileEncoding
import com.blake.editor.session.v2.DocumentIdentity
import com.blake.editor.session.v2.DraftWorkspace
import com.blake.editor.session.v2.RecoveryDraft
import com.blake.editor.session.v2.RecoveryDraftStore
import com.blake.editor.session.v2.VerifiedDocumentSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Isolated DEBUG ONLY preview. Original files change ONLY after an explicit
 * Save; Save As uses the Android document picker. The original Ace editor is
 * unaffected and V2 drafts remain separate from V1 recovery storage.
 */
class V2WorkspacePreviewActivity : AppCompatActivity() {
    private val store by lazy { RecoveryDraftStore(File(filesDir, "v2_recovery")) }
    private val workspace by lazy { DraftWorkspace(store) }
    private val saver by lazy {
        VerifiedDocumentSaver(
            File(filesDir, "v2_save_backups"),
            readBytes = { uri -> readSmallFile(Uri.parse(uri)) },
            writeBytes = { uri, bytes ->
                contentResolver.openOutputStream(Uri.parse(uri), "wt").use { stream ->
                    requireNotNull(stream) { "Storage provider denied file writing" }
                    stream.write(bytes)
                    stream.flush()
                }
            }
        )
    }
    private val lastChanceSaver = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var root: LinearLayout
    private var input: TrackedEditText? = null
    private var status: TextView? = null
    private var active: RecoveryDraft? = null
    private var autosave: Job? = null
    private var binding = false
    private var busy = false

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openLocalFile(uri)
    }

    private val saveAsPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) saveAsTo(uri)
        else status?.text = "Save As canceled · Recovery draft kept"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(12), dp(10), dp(10))
            setBackgroundColor(BG)
        }
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (active == null) {
                    finish()
                } else if (ViewCompat.getRootWindowInsets(root)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                ) {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(root.windowToken, 0)
                    input?.clearFocus()
                } else {
                    goHome()
                }
            }
        })
        lifecycleScope.launch {
            val migration = withContext(Dispatchers.IO) {
                store.importLegacy(File(filesDir, "drafts"))
            }
            if (!migration.complete) {
                dialog("Older drafts need attention",
                    "Some older drafts could not be imported. Their original recovery files were kept.")
            }
            val rememberedId = savedInstanceState?.getString("active_id")
            val recovered = if (rememberedId == null) null else withContext(Dispatchers.IO) {
                runCatching {
                    workspace.load(DocumentIdentity.fromStored(rememberedId)).getOrThrow()
                }.getOrNull()
            }
            if (recovered == null) showHome() else showEditor(recovered)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        active?.let { outState.putString("active_id", it.id.value) }
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        val snapshot = capture() ?: return
        autosave?.cancel()
        lastChanceSaver.launch { workspace.persist(snapshot) }
    }

    private fun showHome() {
        autosave?.cancel()
        active = null
        input = null
        root.removeAllViews()
        label("Blake Editor 2.0", 23f, FG)
        label("Home · Recovery Preview", 16f, FG)
        label("Save writes to a real file. Store Draft only keeps recovery data.", 12f, MUTED)
        controls(listOf(
            "New File" to { newFile() },
            "Open File" to { filePicker.launch(arrayOf("*/*")) }
        ))
        status = label("Loading drafts...", 12f, MUTED)
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        lifecycleScope.launch {
            val listing = withContext(Dispatchers.IO) { workspace.list() }
            status?.text = "Documents: " + listing.drafts.size
            if (listing.unreadableFiles.isNotEmpty()) {
                status?.append(" · " + listing.unreadableFiles.size + " unreadable, kept safely")
            }
            if (listing.drafts.isEmpty()) {
                list.addView(text("No drafts yet. Tap New File.", 15f, MUTED))
            } else {
                val unfinished = listing.drafts.filter { !it.savedToDisk }
                val saved = listing.drafts.filter { it.savedToDisk }
                if (unfinished.isNotEmpty()) list.addView(text("Continue Writing", 17f, FG))
                unfinished.forEach { draft ->
                    list.addView(button(draft.displayName + " · " + draft.language +
                        "\nRecovery draft · " + draft.content.length + " characters") {
                        loadDraft(draft.id)
                    })
                }
                if (saved.isNotEmpty()) list.addView(text("Recent Saved Files", 17f, FG))
                saved.forEach { draft ->
                    list.addView(button(draft.displayName + " · " + draft.language +
                        "\nSaved file · " + draft.content.length + " characters") {
                        loadDraft(draft.id)
                    })
                }
            }
        }
    }

    private fun newFile() {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { workspace.createUntitled() }
            busy = false
            result.onSuccess { showEditor(it, focusKeyboard = true) }
                .onFailure { dialog("Could not create draft", it.message.orEmpty()) }
        }
    }

    private fun showEditor(draft: RecoveryDraft, focusKeyboard: Boolean = false) {
        active = draft
        autosave?.cancel()
        root.removeAllViews()
        label("Blake 2.0 · Document", 21f, FG)
        controls(listOf(
            "Home" to { goHome() },
            "Switch" to { switcher() },
            "Save" to { saveCurrentFile() },
            "Save As" to { beginSaveAs() },
            "Store Draft" to { storeCurrent() }
        ))
        label(draft.displayName, 18f, FG)
        label("Save updates the file · Store Draft keeps recovery only", 12f, MUTED)
        binding = true
        val field = TrackedEditText(this).apply {
            setTextColor(FG)
            setHintTextColor(MUTED)
            setBackgroundColor(SURFACE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.TOP or Gravity.START
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setSingleLine(false)
            setHorizontallyScrolling(false)
            typeface = if (draft.language in listOf("PLAIN_TEXT", "MARKDOWN"))
                Typeface.DEFAULT else Typeface.MONOSPACE
            hint = "Start writing..."
            setText(draft.content)
            setSelection(draft.selectionStart.coerceIn(0, length()),
                draft.selectionEnd.coerceIn(0, length()))
        }
        root.addView(field, LinearLayout.LayoutParams(-1, 0, 1f))
        input = field
        status = label(if (draft.savedToDisk) "Saved file · Revision " + draft.revision
            else "Recovery draft stored · Revision " + draft.revision, 12f, MUTED)
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { changed() }
        })
        field.onSelection = { changed() }
        field.setOnScrollChangeListener { _, _, _, _, _ -> changed() }
        binding = false
        field.post { if (active?.id == draft.id) field.scrollTo(0, draft.scrollY) }
        if (focusKeyboard) {
            field.requestFocus()
            field.post {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            }
        } else field.clearFocus()
    }

    private fun changed() {
        if (binding || busy) return
        val before = active ?: return
        val snapshot = capture() ?: return
        if (snapshot.revision == before.revision) return
        status?.text = "Unsaved to recovery · Revision " + snapshot.revision
        autosave?.cancel()
        autosave = lifecycleScope.launch {
            delay(350L)
            val saved = withContext(Dispatchers.IO) { workspace.persist(snapshot) }
            if (active?.id == snapshot.id && active?.revision == snapshot.revision) {
                status?.text = if (saved.isSuccess) {
                    "Recovery draft stored · Revision " + snapshot.revision
                } else {
                    "Recovery error: " + saved.exceptionOrNull()?.message
                }
            }
        }
    }

    private fun capture(): RecoveryDraft? {
        val old = active ?: return null
        val view = input ?: return old
        return workspace.edit(
            old, view.text?.toString().orEmpty(),
            view.selectionStart.coerceAtLeast(0), view.selectionEnd.coerceAtLeast(0),
            view.scrollY
        ).also { active = it }
    }

    /** Never navigate away until the current revision is safely stored. */
    private fun afterStored(next: suspend (RecoveryDraft) -> Unit) {
        if (busy) return
        val snapshot = capture() ?: return
        busy = true
        input?.isEnabled = false
        autosave?.cancel()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { workspace.persist(snapshot) }
            if (result.isSuccess) {
                next(snapshot)
            } else {
                dialog("Recovery failed — staying in the editor",
                    result.exceptionOrNull()?.message.orEmpty())
            }
            input?.isEnabled = true
            busy = false
        }
    }

    private fun goHome() = afterStored { showHome() }

    private fun storeCurrent() = afterStored { draft ->
        status?.text = "Recovery draft stored · Revision " + draft.revision
    }

    private fun switcher() = afterStored {
        val entries = withContext(Dispatchers.IO) { workspace.list() }
        if (entries.unreadableFiles.isNotEmpty()) {
            dialog("Unreadable drafts",
                "Some recovery records could not be opened. They were not deleted.")
        }
        AlertDialog.Builder(this@V2WorkspacePreviewActivity)
            .setTitle("Quick Document Switcher")
            .setItems(entries.drafts.map { it.displayName + " · " + it.content.length + " chars" }
                .toTypedArray()) { _, index ->
                loadDraft(entries.drafts[index].id)
            }
            .setNeutralButton("New File") { _, _ -> afterStored { createAfterSwitch() } }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private suspend fun createAfterSwitch() {
        val result = withContext(Dispatchers.IO) { workspace.createUntitled() }
        result.onSuccess { showEditor(it, focusKeyboard = true) }
            .onFailure { dialog("Could not create draft", it.message.orEmpty()) }
    }

    private fun loadDraft(id: DocumentIdentity) {
        if (busy) return
        val current = active
        if (current == null) {
            busy = true
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { workspace.load(id) }
                busy = false
                result.onSuccess { draft ->
                    if (draft == null) dialog("Draft missing", "No recovery data found.")
                    else showEditor(draft)
                }.onFailure { dialog("Could not open draft", it.message.orEmpty()) }
            }
        } else {
            afterStored { snapshot ->
                val result = withContext(Dispatchers.IO) { workspace.switchTo(snapshot, id) }
                result.onSuccess { showEditor(it) }
                    .onFailure { dialog("Could not switch", it.message.orEmpty()) }
            }
        }
    }

    private fun openLocalFile(uri: Uri) {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {
                        try {
                            contentResolver.takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: SecurityException) { /* Drafts work without persisted grant. */ }
                    }
                    val name = displayName(uri)
                    val bytes = readSmallFile(uri)
                    Pair(name, bytes)
                }
            }
            busy = false
            result.onSuccess { (name, bytes) ->
                when (val found = TextEncodingCodec.detect(bytes)) {
                    is EncodingDetection.Ready -> addOpenedFile(uri, name, found.document, bytes)
                    is EncodingDetection.ChooseEncoding -> {
                        val candidates = found.previews
                        AlertDialog.Builder(this@V2WorkspacePreviewActivity)
                            .setTitle("Choose text encoding")
                            .setItems(candidates.map {
                                it.encoding.charsetName + " · " + it.text.take(40)
                            }.toTypedArray()) { _, which ->
                                TextEncodingCodec.decodeWith(bytes, candidates[which].encoding)
                                    .onSuccess { addOpenedFile(uri, name, it, bytes) }
                                    .onFailure { dialog("Could not decode file", it.message.orEmpty()) }
                            }
                            .setNegativeButton("Cancel", null).show()
                    }
                    is EncodingDetection.Unsupported ->
                        dialog("Cannot open safely", found.reason)
                }
            }.onFailure {
                dialog("Could not read file", it.message.orEmpty() +
                    "\nLarge-file reading is a later milestone.")
            }
        }
    }

    private fun addOpenedFile(uri: Uri, name: String, decoded: DecodedTextFile,
        sourceBytes: ByteArray) {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                workspace.openFromFile(uri.toString(), name, languageFor(name), decoded.text,
                    encoding = decoded.encoding.name,
                    hadBom = decoded.hadBom,
                    sourceSha256 = VerifiedDocumentSaver.fingerprint(sourceBytes))
            }
            busy = false
            result.onSuccess { showEditor(it) }
                .onFailure { dialog("Could not store recovery draft", it.message.orEmpty()) }
        }
    }

    private fun saveCurrentFile() {
        if (active?.sourceUri == null) {
            beginSaveAs()
            return
        }
        afterStored { snapshot ->
            val result = withContext(Dispatchers.IO) { saver.saveExisting(snapshot) }
            result.onSuccess { file ->
                val updated = VerifiedDocumentSaver.afterSave(
                    snapshot, file, snapshot.displayName, snapshot.language
                )
                val stored = withContext(Dispatchers.IO) { workspace.persist(updated) }
                if (stored.isSuccess) {
                    showEditor(updated)
                    status?.text = "Saved and verified · " + file.verifiedBytes + " bytes"
                } else {
                    dialog("File saved, but recovery tracking failed",
                        "The file was written, but its recovery record could not be updated. " +
                            stored.exceptionOrNull()?.message.orEmpty())
                }
            }.onFailure {
                dialog("Could not safely save", it.message.orEmpty() +
                    "\nYour recovery draft is still available. Use Save As if the file changed.")
            }
        }
    }

    private fun beginSaveAs() = afterStored { snapshot ->
        val name = snapshot.displayName.let {
            if (it == "Untitled") "Untitled.txt" else it
        }
        saveAsPicker.launch(name)
    }

    private fun saveAsTo(uri: Uri) {
        if (active?.sourceUri == uri.toString()) {
            dialog("Choose another file", "Use Save to update the current file.")
            return
        }
        afterStored { snapshot ->
            val encoding = runCatching {
                TextFileEncoding.valueOf(snapshot.sourceEncoding ?: "UTF8")
            }.getOrDefault(TextFileEncoding.UTF8)
            val result = withContext(Dispatchers.IO) {
                saver.saveAs(snapshot, uri.toString(), encoding, snapshot.sourceHadBom)
            }
            result.onSuccess { file ->
                val name = withContext(Dispatchers.IO) { displayName(uri) }
                val updated = VerifiedDocumentSaver.afterSave(
                    snapshot, file, name, languageFor(name)
                )
                val stored = withContext(Dispatchers.IO) { workspace.persist(updated) }
                if (stored.isSuccess) {
                    val cleared = withContext(Dispatchers.IO) {
                        store.clearSavedRevision(snapshot.id, snapshot.revision)
                    }
                    showEditor(updated)
                    status?.text = if (cleared.getOrDefault(false)) {
                        "Saved As and verified · " + file.verifiedBytes + " bytes"
                    } else {
                        "Saved As verified · Previous recovery copy retained"
                    }
                } else {
                    dialog("File saved, but recovery tracking failed",
                        "The new file was written. Old recovery draft was kept: " +
                            stored.exceptionOrNull()?.message.orEmpty())
                }
            }.onFailure {
                dialog("Save As failed", it.message.orEmpty() +
                    "\nThe original document and recovery draft were kept.")
            }
        }
    }

    private fun readSmallFile(uri: Uri): ByteArray {
        contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "No file input" }
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                if (n == 0) continue
                require(output.size() + n <= 2 * 1024 * 1024) {
                    "File exceeds the preview's 2 MB limit"
                }
                output.write(buffer, 0, n)
            }
            return output.toByteArray()
        }
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) {
                    val candidate = c.getString(idx)
                    if (!candidate.isNullOrBlank()) return candidate
                }
            }
        return uri.lastPathSegment ?: "Document"
    }

    private fun languageFor(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "md", "markdown" -> "MARKDOWN"
            "kt", "kts" -> "KOTLIN"
            "py" -> "PYTHON"
            "java" -> "JAVA"
            "js" -> "JAVASCRIPT"
            "json" -> "JSON"
            "xml" -> "XML"
            "html" -> "HTML"
            "css" -> "CSS"
            else -> "PLAIN_TEXT"
        }

    private fun controls(items: List<Pair<String, () -> Unit>>) {
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEach { (caption, action) -> buttons.addView(button(caption, action)) }
        root.addView(HorizontalScrollView(this).apply { addView(buttons) })
    }

    private fun label(value: String, sp: Float, color: Int): TextView =
        text(value, sp, color).also { root.addView(it) }

    private fun text(value: String, sp: Float, color: Int): TextView =
        TextView(this).apply {
            this.text = value
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setPadding(dp(8), dp(7), dp(8), dp(7))
        }

    private fun button(value: String, action: () -> Unit): Button =
        Button(this).apply {
            text = value
            isAllCaps = false
            setOnClickListener { if (!busy) action() }
        }

    private fun dialog(title: String, detail: String) {
        status?.text = title
        AlertDialog.Builder(this).setTitle(title).setMessage(detail)
            .setPositiveButton("OK", null).show()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()

    private companion object {
        val BG = 0xFF151820.toInt()
        val SURFACE = 0xFF232833.toInt()
        val FG = 0xFFF1F5F9.toInt()
        val MUTED = 0xFFBBC6D4.toInt()
    }
}

private class TrackedEditText(context: Context) : EditText(context) {
    var onSelection: (() -> Unit)? = null
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelection?.invoke()
    }
}
