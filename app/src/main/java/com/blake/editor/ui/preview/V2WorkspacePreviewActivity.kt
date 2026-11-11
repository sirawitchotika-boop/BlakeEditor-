package com.blake.editor.ui.preview

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
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
import androidx.webkit.WebViewAssetLoader
import androidx.lifecycle.lifecycleScope
import com.blake.editor.editor.v2.CodeSyntaxScanner
import com.blake.editor.editor.v2.EditorFindReplace
import com.blake.editor.editor.v2.TextMatch
import com.blake.editor.file.DecodedTextFile
import com.blake.editor.file.EncodingDetection
import com.blake.editor.file.TextEncodingCodec
import com.blake.editor.file.TextFileEncoding
import com.blake.editor.session.v2.DocumentIdentity
import com.blake.editor.session.v2.DraftWorkspace
import com.blake.editor.session.v2.RecoveryDraft
import com.blake.editor.session.v2.RecoveryDraftStore
import com.blake.editor.session.v2.VerifiedDocumentSaver
import com.blake.editor.session.v2.V2HomeSections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import org.json.JSONObject

/**
 * V2 workspace engine shared with the app source set. The Activity is
 * registered as a launcher ONLY in the debug manifest; main/release keeps
 * Blake Editor — Original as its launcher and external document handler.
 * Original files change ONLY after explicit Save. V2 drafts remain separate
 * from V1 recovery storage.
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
    private val displaySettings by lazy { PreviewEditorSettings(this) }
    private val palette: PreviewPalette get() = displaySettings.palette()
    private val BG: Int get() = palette.background
    private val SURFACE: Int get() = palette.surface
    private val FG: Int get() = palette.foreground
    private val MUTED: Int get() = palette.secondary


    private lateinit var root: LinearLayout
    private var input: PreviewCodeEditText? = null
    private var readerWebView: WebView? = null
    private var markdownSourceMode = false
    private var status: TextView? = null
    private var active: RecoveryDraft? = null
    private var viewingTrash = false
    private var autosave: Job? = null
    private var highlightJob: Job? = null
    private var pendingTextChange = false

    private data class PersistedVersion(val documentId: String, val revision: Long)
    @Volatile private var lastPersisted: PersistedVersion? = null

    private fun isPersisted(draft: RecoveryDraft): Boolean =
        PreviewPowerPolicy.needsWrite(
            draft.id.value, draft.revision,
            lastPersisted?.documentId, lastPersisted?.revision ?: -1L
        ).not()

    private fun markPersisted(draft: RecoveryDraft) {
        val current = lastPersisted
        if (current?.documentId != draft.id.value ||
            current.revision < draft.revision) {
            lastPersisted = PersistedVersion(draft.id.value, draft.revision)
        }
    }
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
        PreviewSystemBars.install(this, root, BG)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (active == null) {
                    if (viewingTrash) showHome() else finish()
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
            // Folds/rotation can destroy the Activity before its asynchronous
            // onStop disk flush finishes. A small Bundle snapshot keeps recent
            // changes recoverable, without risking Android Binder size limits.
            val pending = savedInstanceState?.let { bundle ->
                val text = bundle.getString("pending_text")
                val id = bundle.getString("pending_id")
                if (text == null || id == null) null else ResumeSnapshot(
                    id, text,
                    bundle.getInt("pending_start"), bundle.getInt("pending_end"),
                    bundle.getInt("pending_scroll"), bundle.getLong("pending_revision")
                )
            }
            val extra = recovered?.let { PreviewResumePolicy.restore(it, pending) }
            var restored = extra ?: recovered
            var knownStored = extra == null
            if (extra != null) {
                val saved = withContext(Dispatchers.IO) { workspace.persist(extra) }
                knownStored = saved.isSuccess
                if (saved.isFailure) {
                    // Another Activity may already have saved an even newer
                    // revision; never show a stale document over that record.
                    val latest = withContext(Dispatchers.IO) {
                        workspace.load(extra.id).getOrNull()
                    }
                    if (latest != null && latest.revision > extra.revision) {
                        restored = latest
                        knownStored = true
                    }
                }
            }
            if (restored == null) showHome()
            else showEditor(restored, knownStored = knownStored)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        capture()?.let { draft ->
            outState.putString("active_id", draft.id.value)
            if (!isPersisted(draft)) {
                PreviewResumePolicy.capture(draft)?.let { pending ->
                    outState.putString("pending_id", pending.id)
                    outState.putString("pending_text", pending.content)
                    outState.putInt("pending_start", pending.selectionStart)
                    outState.putInt("pending_end", pending.selectionEnd)
                    outState.putInt("pending_scroll", pending.scrollY)
                    outState.putLong("pending_revision", pending.revision)
                }
            }
        }
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        // No background animation or live syntax scanning when not focused.
        highlightJob?.cancel()
        readerWebView?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        readerWebView?.onResume()
        val field = input
        val draft = active
        if (field != null && draft != null) {
            scheduleSyntaxHighlight(field, draft.language, immediate = true)
        }
    }

    override fun onStop() {
        super.onStop()
        autosave?.cancel()
        highlightJob?.cancel()
        val snapshot = capture() ?: return
        if (isPersisted(snapshot)) return
        // Finish a single short atomic write on IO after the UI goes away.
        // Do not hold a wake lock or keep a permanent background service.
        lastChanceSaver.launch {
            if (workspace.persist(snapshot).isSuccess) markPersisted(snapshot)
        }
    }

    override fun onDestroy() {
        highlightJob?.cancel()
        autosave?.cancel()
        disposeReaderView()
        super.onDestroy()
    }

    private fun showHome() {
        viewingTrash = false
        autosave?.cancel()
        highlightJob?.cancel()
        pendingTextChange = false
        active = null
        input = null
        markdownSourceMode = false
        disposeReaderView()
        root.removeAllViews()
        PreviewSystemBars.update(this, root, BG)
        label("Blake Editor 2.0", 22f, FG)
        label("Home · Your documents", 16f, FG)
        label("Recovery stays on your phone. Save writes to your chosen file.", 12f, MUTED)
        controls(listOf(
            "New File" to { newFile() },
            "Open File" to { filePicker.launch(arrayOf("*/*")) },
            "Settings" to { showEditorSettings() }
        ))
        // This selector is OUTSIDE the document ScrollView. Scrolling can never
        // press Move, check a draft, or trigger any storage action.
        root.addView(button("Select drafts for Trash") { chooseDraftsForTrash() },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        status = label("Loading your documents...", 12f, MUTED)
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        lifecycleScope.launch {
            val listing = withContext(Dispatchers.IO) { workspace.list() }
            val sections = V2HomeSections.from(listing)
            val unfinished = sections.continueWriting
            val saved = sections.recentlySaved
            status?.text = "Documents: " + (unfinished.size + saved.size) +
                " · Trash: " + sections.trash.size + " · Stored locally"
            if (listing.unreadableFiles.isNotEmpty()) {
                status?.append(" · " + listing.unreadableFiles.size + " unreadable, kept safely")
            }
            if (unfinished.isEmpty() && saved.isEmpty()) {
                list.addView(text("No active drafts yet. Tap New File.", 15f, MUTED))
            }
            if (unfinished.isNotEmpty()) {
                list.addView(text("Continue Writing", 17f, FG))
            }
            unfinished.forEach { draft ->
                val row = LinearLayout(this@V2WorkspacePreviewActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(button(draft.displayName + " · " + draft.language +
                    "\nRecovery draft · " + draft.content.length + " characters") {
                    loadDraft(draft.id)
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                })
                row.addView(button("Rename") { renameDraft(draft) }.apply {
                    layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                        marginStart = dp(6)
                    }
                })
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(8)
                })
            }
            if (saved.isNotEmpty()) list.addView(text("Recent Saved Files", 17f, FG))
            saved.forEach { draft ->
                list.addView(button(draft.displayName + " · " + draft.language +
                    "\nSaved file · " + draft.content.length + " characters") {
                    loadDraft(draft.id)
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = dp(8)
                    }
                })
            }
            list.addView(button("Trash · " + sections.trash.size + " stored drafts") {
                showTrash()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(8)
                    bottomMargin = dp(8)
                }
            })
        }
    }

    /** Trash is a read-only list until conflict-safe Restore is implemented. */
    private fun showTrash() {
        if (busy || active != null) return
        viewingTrash = true
        root.removeAllViews()
        PreviewSystemBars.update(this, root, BG)
        label("Trash", 22f, FG)
        label("Restore is available. Permanent deletion needs confirmation.", 12f, MUTED)
        controls(listOf(
            "Back to Home" to { showHome() },
            "Select to Delete" to { chooseTrashedForDeletion() },
            "Empty Trash" to { confirmEmptyTrash() }
        ))
        status = label("Loading Trash...", 12f, MUTED)
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        lifecycleScope.launch {
            val listing = withContext(Dispatchers.IO) { workspace.list() }
            if (!viewingTrash) return@launch
            val trashed = V2HomeSections.from(listing).trash
            status?.text = "Trash: " + trashed.size + " · Stored locally"
            if (listing.unreadableFiles.isNotEmpty()) {
                status?.append(" · Unreadable recovery records kept safely")
            }
            if (trashed.isEmpty()) {
                list.addView(text("Trash is empty.", 15f, MUTED))
            }
            trashed.forEach { draft ->
                val row = LinearLayout(this@V2WorkspacePreviewActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(text(draft.displayName + " · " + draft.language +
                    "\nRecovery kept · " + draft.content.length + " characters", 15f, FG),
                    LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(button("Restore") { restoreTrashedDraft(draft) },
                    LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(8)
                })
            }
        }
    }

    /** Short, stable identifier distinguishes duplicate custom names before bulk actions. */
    private fun draftChoiceLabel(draft: RecoveryDraft): String =
        draft.displayName + " · " + draft.content.length + " chars · #" +
            draft.id.value.takeLast(6)

    /**
     * Selection happens inside Android's multi-choice dialog. Nothing can move
     * from a scroll gesture; only the separately confirmed button changes data.
     */
    private fun chooseDraftsForTrash() {
        if (busy || active != null || viewingTrash) return
        busy = true
        lifecycleScope.launch {
            val listing = withContext(Dispatchers.IO) { workspace.list() }
            busy = false
            if (viewingTrash || active != null) return@launch
            if (listing.unreadableFiles.isNotEmpty()) {
                dialog("Unreadable recovery records",
                    "Unreadable drafts were kept. They cannot be selected.")
            }
            val drafts = V2HomeSections.from(listing).continueWriting
            if (drafts.isEmpty()) {
                dialog("No drafts to move", "Continue Writing has no unfinished drafts.")
                return@launch
            }
            val checked = BooleanArray(drafts.size)
            AlertDialog.Builder(this@V2WorkspacePreviewActivity)
                .setTitle("Select drafts to move")
                .setMultiChoiceItems(drafts.map { draftChoiceLabel(it) }.toTypedArray(), checked) { _, index, isChecked ->
                    checked[index] = isChecked
                }
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Review selected") { _, _ ->
                    val selected = drafts.filterIndexed { index, _ -> checked[index] }
                    if (selected.isEmpty()) {
                        dialog("Nothing selected", "Check one or more drafts first.")
                    } else {
                        confirmSelectedToTrash(selected)
                    }
                }
                .show()
        }
    }

    private fun confirmSelectedToTrash(drafts: List<RecoveryDraft>) {
        if (busy || active != null || viewingTrash || drafts.isEmpty()) return
        val preview = drafts.take(5).joinToString("\n") { "• " + draftChoiceLabel(it) }
        val more = if (drafts.size > 5) "\nAnd " + (drafts.size - 5) + " more" else ""
        AlertDialog.Builder(this)
            .setTitle("Move " + drafts.size + " drafts to Trash?")
            .setMessage(preview + more + "\n\nRecovery contents will stay on the phone. " +
                "No real file will change.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Move selected to Trash") { _, _ ->
                if (busy || active != null || viewingTrash) return@setPositiveButton
                busy = true
                lifecycleScope.launch {
                    // Each record is synced atomically. A failed item stays on Home,
                    // while successful items are still recoverable in Trash.
                    val result = withContext(Dispatchers.IO) {
                        var moved = 0
                        val failed = mutableListOf<String>()
                        drafts.forEach { draft ->
                            val saved = workspace.moveToTrash(draft.id, draft.revision)
                            if (saved.isSuccess) moved++ else failed.add(draft.displayName)
                        }
                        Pair(moved, failed)
                    }
                    busy = false
                    showHome()
                    if (result.second.isNotEmpty()) {
                        dialog("Some drafts were not moved",
                            result.first.toString() + " moved, " +
                                result.second.size + " kept on Home.\n" +
                                result.second.take(4).joinToString(", "))
                    }
                }
            }
            .show()
    }

    private fun restoreTrashedDraft(draft: RecoveryDraft) {
        if (busy || active != null || !viewingTrash) return
        busy = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                // A missing/unreadable/changed linked source always becomes a
                // separate recovered draft, never a blind overwrite.
                val matches = runCatching {
                    val uri = draft.sourceUri ?: return@runCatching false
                    val fingerprint = draft.sourceSha256 ?: return@runCatching false
                    VerifiedDocumentSaver.fingerprint(
                        readSmallFile(Uri.parse(uri))) == fingerprint
                }.getOrDefault(false)
                workspace.restoreFromTrash(draft.id, draft.revision, matches)
            }
            busy = false
            result.onSuccess { restored ->
                showTrash()
                if (restored.recoveredAsCopy) {
                    dialog("Recovered a separate copy",
                        "A linked source could not be verified. A new unlinked draft " +
                            "was created under Continue Writing. The original Trash " +
                            "entry was retained. No real file was changed.")
                } else {
                    status?.text = "Restored " + restored.draft.displayName
                }
            }.onFailure {
                dialog("Restore was not completed",
                    it.message.orEmpty() + "\nYour Trash recovery record was kept.")
            }
        }
    }

    private fun chooseTrashedForDeletion() {
        if (busy || active != null || !viewingTrash) return
        busy = true
        lifecycleScope.launch {
            val listing = withContext(Dispatchers.IO) { workspace.list() }
            busy = false
            if (!viewingTrash || active != null) return@launch
            val drafts = V2HomeSections.from(listing).trash
            if (drafts.isEmpty()) {
                dialog("Trash is empty", "There are no Trash entries to delete.")
                return@launch
            }
            val checked = BooleanArray(drafts.size)
            AlertDialog.Builder(this@V2WorkspacePreviewActivity)
                .setTitle("Select Trash entries to delete")
                .setMultiChoiceItems(drafts.map { draftChoiceLabel(it) }.toTypedArray(), checked) { _, index, isChecked ->
                    checked[index] = isChecked
                }
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Review deletion") { _, _ ->
                    val selected = drafts.filterIndexed { index, _ -> checked[index] }
                    if (selected.isEmpty()) {
                        dialog("Nothing selected", "Check entries to delete first.")
                    } else {
                        confirmPermanentDeletion(selected)
                    }
                }
                .show()
        }
    }

    private fun confirmEmptyTrash() {
        if (busy || active != null || !viewingTrash) return
        busy = true
        lifecycleScope.launch {
            val listing = withContext(Dispatchers.IO) { workspace.list() }
            busy = false
            if (!viewingTrash || active != null) return@launch
            val drafts = V2HomeSections.from(listing).trash
            if (drafts.isEmpty()) {
                dialog("Trash is empty", "There is nothing to delete.")
                return@launch
            }
            AlertDialog.Builder(this@V2WorkspacePreviewActivity)
                .setTitle("Empty all Trash?")
                .setMessage("Review permanent deletion of " + drafts.size +
                    " recovery records? Nothing has been deleted yet.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Review deletion") { _, _ ->
                    confirmPermanentDeletion(drafts)
                }
                .show()
        }
    }

    private fun confirmPermanentDeletion(drafts: List<RecoveryDraft>) {
        if (busy || active != null || !viewingTrash || drafts.isEmpty()) return
        val names = drafts.take(5).joinToString("\n") { "• " + draftChoiceLabel(it) }
        val more = if (drafts.size > 5) "\nAnd " + (drafts.size - 5) + " more" else ""
        AlertDialog.Builder(this)
            .setTitle("Delete " + drafts.size + " permanently?")
            .setMessage(names + more + "\n\nThis permanently removes only the " +
                "selected private recovery records. This cannot be undone. " +
                "Real files and other drafts are never deleted.")
            .setNegativeButton("Keep in Trash", null)
            .setPositiveButton("Delete Permanently") { _, _ ->
                if (busy || active != null || !viewingTrash) return@setPositiveButton
                busy = true
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        var removed = 0
                        val failed = mutableListOf<String>()
                        drafts.forEach { draft ->
                            val action = workspace.deleteTrashedPermanently(
                                draft.id, draft.revision)
                            if (action.isSuccess) removed++ else failed.add(draft.displayName)
                        }
                        Pair(removed, failed)
                    }
                    busy = false
                    showTrash()
                    if (result.second.isNotEmpty()) {
                        dialog("Some entries were kept",
                            result.first.toString() + " deleted, " +
                                result.second.size + " kept in Trash.\n" +
                                result.second.take(4).joinToString(", "))
                    }
                }
            }
            .show()
    }

    private fun renameDraft(draft: RecoveryDraft) {
        if (busy || active != null) return
        val nameField = EditText(this).apply {
            setSingleLine(true)
            setText(draft.displayName)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("Rename Draft")
            .setMessage("Changes the draft name only. It does not rename or save a real file.")
            .setView(nameField)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Rename") { _, _ ->
                if (busy || active != null) return@setPositiveButton
                busy = true
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        workspace.renameDraft(draft.id, draft.revision,
                            nameField.text?.toString().orEmpty())
                    }
                    busy = false
                    result.onSuccess { showHome() }
                        .onFailure {
                            dialog("Could not rename draft", it.message.orEmpty() +
                                "\nYour existing draft was kept unchanged.")
                        }
                }
            }
            .show()
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

    private fun showEditor(draft: RecoveryDraft, focusKeyboard: Boolean = false,
        editMarkdown: Boolean = false, knownStored: Boolean = true) {
        active = draft
        if (knownStored) markPersisted(draft)
        pendingTextChange = !knownStored
        autosave?.cancel()
        highlightJob?.cancel()
        disposeReaderView()
        markdownSourceMode = editMarkdown
        root.removeAllViews()
        PreviewSystemBars.update(this, root, BG)
        label("Blake 2.0 · Document", 19f, FG)
        val documentActions = mutableListOf<Pair<String, () -> Unit>>(
            "Home" to { goHome() },
            "Switch" to { switcher() },
            "Save" to { saveCurrentFile() },
            "Save As" to { beginSaveAs() },
            "Store Draft" to { storeCurrent() },
            "Settings" to { showEditorSettings() }
        )
        if (draft.language != "MARKDOWN" || editMarkdown) {
            documentActions.add("Find / Replace" to { showFindReplace() })
        }
        controls(documentActions)
        label(draft.displayName, 16f, FG)
        label("Save updates the file · Store Draft keeps recovery only", 12f, MUTED)
        if (draft.language == "MARKDOWN") {
            controls(listOf((if (editMarkdown) "Reader" else "Edit Source") to {
                toggleMarkdownMode()
            }))
            if (!editMarkdown) {
                input = null
                status = label("Offline Markdown Reader · Read mode", 12f, MUTED)
                showMarkdownReader(draft)
                return
            }
        }
        binding = true
        // Keep recoverable documents accessible if a future custom view fails
        // while being constructed. Do not let the preview crash or delete drafts.
        val field = try {
            PreviewCodeEditText(this).apply {
            setTextColor(FG)
            setHintTextColor(MUTED)
            setBackgroundColor(SURFACE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.TOP or Gravity.START
            setTextSize(TypedValue.COMPLEX_UNIT_SP, displaySettings.fontSp.toFloat())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setSingleLine(false)
            setHorizontallyScrolling(CodeSyntaxScanner.isCode(draft.language) &&
                !displaySettings.wrapCode)
            typeface = if (draft.language in listOf("PLAIN_TEXT", "MARKDOWN"))
                Typeface.DEFAULT else Typeface.MONOSPACE
            hint = "Start writing..."
            setText(draft.content)
            setSelection(draft.selectionStart.coerceIn(0, length()),
                draft.selectionEnd.coerceIn(0, length()))
            configureCodeGutter(CodeSyntaxScanner.isCode(draft.language) &&
                displaySettings.showLineNumbers, palette)
            }
        } catch (failure: RuntimeException) {
            showHome()
            dialog("Could not open editor",
                "The file and recovery draft were kept. " +
                    failure.javaClass.simpleName + ": " + failure.message.orEmpty())
            return
        }
        root.addView(field, LinearLayout.LayoutParams(-1, 0, 1f))
        input = field
        if (CodeSyntaxScanner.isCode(draft.language)) {
            showQuickSymbols(field)
        }
        status = label(if (draft.savedToDisk) "Saved file · Revision " + draft.revision
            else "Recovery draft stored · Revision " + draft.revision, 12f, MUTED)
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                changed(textChanged = true)
                scheduleSyntaxHighlight(field, draft.language)
            }
        })
        field.onSelection = { changed(textChanged = false) }
        field.setOnScrollChangeListener { _, _, _, _, _ ->
            changed(textChanged = false)
        }
        binding = false
        scheduleSyntaxHighlight(field, draft.language, immediate = true)
        field.post { if (active?.id == draft.id) field.scrollTo(0, draft.scrollY) }
        if (focusKeyboard) {
            field.requestFocus()
            field.post {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            }
        } else field.clearFocus()
    }

    private fun toggleMarkdownMode() {
        if (active?.language != "MARKDOWN") return
        val nextSourceMode = !markdownSourceMode
        afterStored { snapshot ->
            showEditor(snapshot, focusKeyboard = nextSourceMode,
                editMarkdown = nextSourceMode)
        }
    }

    private fun disposeReaderView() {
        readerWebView?.let { view ->
            root.removeView(view)
            view.destroy()
        }
        readerWebView = null
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun showMarkdownReader(draft: RecoveryDraft) {
        // Keep very large Markdown in source/edit mode until incremental
        // formatting is available, rather than blocking WebView on rendering.
        if (draft.content.length > MAX_MARKDOWN_RENDER_CHARS) {
            status?.text = "This Markdown is too large to format safely; tap Edit Source."
            root.addView(text("Markdown preview limited to 300,000 characters.", 15f, MUTED))
            return
        }
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        val view = WebView(this).apply {
            setBackgroundColor(BG)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                blockNetworkLoads = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView,
                    request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(view: WebView,
                    request: WebResourceRequest): Boolean = true

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (readerWebView !== view ||
                        active?.id != draft.id || markdownSourceMode) return
                    val quoted = JSONObject.quote(draft.content)
                    val theme = JSONObject.quote(palette.readerTheme)
                    view?.evaluateJavascript("window.BlakeReader.setTheme(" + theme +
                        ");window.BlakeReader.show(" + quoted + ");", null)
                }
            }
        }
        readerWebView = view
        root.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        view.loadUrl("https://appassets.androidplatform.net/assets/reader/index.html")
    }

    private fun scheduleSyntaxHighlight(field: PreviewCodeEditText,
        language: String, immediate: Boolean = false
    ) {
        if (!CodeSyntaxScanner.isCode(language)) return
        highlightJob?.cancel()
        val snapshot = field.text?.toString().orEmpty()
        highlightJob = lifecycleScope.launch {
            if (!immediate) delay(220L)
            val result = withContext(Dispatchers.Default) {
                CodeSyntaxScanner.scan(snapshot, language)
            }
            if (field === input && field.text?.toString() == snapshot) {
                field.applyCodeTokens(result, palette)
            }
        }
    }

    private fun showEditorSettings() {
        if (busy) return
        val options = arrayOf(
            "Theme: " + displaySettings.theme.title,
            "Font size: " + displaySettings.fontSp + " sp",
            "Code wrapping: " + if (displaySettings.wrapCode) "On" else "Off",
            "Code line numbers: " + if (displaySettings.showLineNumbers) "On" else "Off"
        )
        AlertDialog.Builder(this)
            .setTitle("Editor Settings")
            .setItems(options) { _, selected ->
                when (selected) {
                    0 -> chooseTheme()
                    1 -> chooseFontSize()
                    2 -> {
                        displaySettings.wrapCode = !displaySettings.wrapCode
                        refreshAfterSetting()
                    }
                    3 -> {
                        displaySettings.showLineNumbers = !displaySettings.showLineNumbers
                        refreshAfterSetting()
                    }
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun chooseTheme() {
        val choices = PreviewTheme.values()
        AlertDialog.Builder(this)
            .setTitle("Theme")
            .setSingleChoiceItems(choices.map { it.title }.toTypedArray(),
                choices.indexOf(displaySettings.theme)) { dialog, index ->
                displaySettings.theme = choices[index]
                dialog.dismiss()
                refreshAfterSetting()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun chooseFontSize() {
        val choices = intArrayOf(12, 14, 16, 18, 20, 22, 24, 26)
        val index = choices.indexOf(displaySettings.fontSp).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Editor font size")
            .setSingleChoiceItems(choices.map { "$it sp" }.toTypedArray(), index
            ) { dialog, selected ->
                displaySettings.fontSp = choices[selected]
                dialog.dismiss()
                refreshAfterSetting()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun refreshAfterSetting() {
        if (active == null) {
            showHome()
        } else {
            val sourceMode = markdownSourceMode
            afterStored { snapshot ->
                showEditor(snapshot, editMarkdown = sourceMode)
            }
        }
    }

    /**
     * Keeps symbol entry inside the native editor: no replacement TextView,
     * clipboard insertion or extra input method that could disturb Thai IMEs.
     */
    private fun showQuickSymbols(field: PreviewCodeEditText) {
        val keys = listOf(
            "Tab" to "    ", "{" to "{", "}" to "}",
            "(" to "(", ")" to ")", "[" to "[", "]" to "]",
            ";" to ";", ":" to ":", "=" to "=",
            "\"" to "\"", "'" to "'", "/" to "/", "\\" to "\\",
            "<" to "<", ">" to ">", "#" to "#"
        )
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        keys.forEach { (name, value) ->
            strip.addView(button(name) {
                if (field !== input || active == null) return@button
                val current = field.text ?: return@button
                val a = field.selectionStart.coerceIn(0, current.length)
                val b = field.selectionEnd.coerceIn(0, current.length)
                val start = minOf(a, b)
                current.replace(start, maxOf(a, b), value)
                field.setSelection((start + value.length).coerceAtMost(current.length))
                field.requestFocus()
            }.apply {
                isFocusable = false
                isFocusableInTouchMode = false
                minWidth = dp(46)
            })
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip)
        })
    }

    private fun showFindReplace() {
        if (busy) return
        val field = input ?: return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(6))
        }
        val query = EditText(this).apply {
            hint = "Find text"
            setSingleLine(true)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        val replacement = EditText(this).apply {
            hint = "Replace with (can be empty)"
            setSingleLine(true)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        val matchCase = CheckBox(this).apply {
            text = "Match case"
            isChecked = false
        }
        val result = TextView(this).apply {
            text = "Searches this document only. Plain text, not regex."
            setPadding(dp(6), dp(8), dp(6), dp(8))
        }
        panel.addView(query)
        panel.addView(replacement)
        panel.addView(matchCase)
        panel.addView(result)
        val buttonScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        buttonScroller.addView(row)
        panel.addView(buttonScroller)

        fun select(match: TextMatch?) {
            if (field !== input) return
            if (match == null) {
                result.text = "No match found"
                return
            }
            field.setSelection(match.start, match.end)
            field.bringPointIntoView(match.start)
            result.text = "Selected match at character " + (match.start + 1)
        }

        row.addView(button("Previous") {
            if (field !== input) return@button
            val text = field.text?.toString().orEmpty()
            val search = query.text.toString()
            if (search.isEmpty()) {
                result.text = "Type something to find"
            } else {
                select(EditorFindReplace.previous(text, search,
                    minOf(field.selectionStart, field.selectionEnd),
                    ignoreCase = !matchCase.isChecked))
            }
        })
        row.addView(button("Next") {
            if (field !== input) return@button
            val text = field.text?.toString().orEmpty()
            val search = query.text.toString()
            if (search.isEmpty()) {
                result.text = "Type something to find"
            } else {
                select(EditorFindReplace.next(text, search,
                    maxOf(field.selectionStart, field.selectionEnd),
                    ignoreCase = !matchCase.isChecked))
            }
        })
        row.addView(button("Replace") {
            if (field !== input) return@button
            val editable = field.text ?: return@button
            val text = editable.toString()
            val search = query.text.toString()
            if (search.isEmpty()) {
                result.text = "Type something to find"
                return@button
            }
            val start = minOf(field.selectionStart, field.selectionEnd)
            val end = maxOf(field.selectionStart, field.selectionEnd)
            if (!EditorFindReplace.selectionMatches(text, start, end,
                    search, ignoreCase = !matchCase.isChecked)
            ) {
                select(EditorFindReplace.next(text, search, end,
                    ignoreCase = !matchCase.isChecked))
                result.text = "Select a match, then tap Replace again"
                return@button
            }
            val insert = replacement.text.toString()
            editable.replace(start, end, insert)
            field.setSelection((start + insert.length).coerceAtMost(editable.length))
            result.text = "Replaced one match. Tap Next to continue."
        })
        row.addView(button("Replace All") {
            if (field !== input) return@button
            val search = query.text.toString()
            if (search.isEmpty()) {
                result.text = "Type something to find"
                return@button
            }
            val oldText = field.text?.toString() ?: return@button
            val insert = replacement.text.toString()
            val matchExactly = matchCase.isChecked
            // Bound expensive searching off the UI thread; verify the source
            // has not changed before applying this destructive edit.
            lifecycleScope.launch {
                result.text = "Checking matches..."
                val plan = withContext(Dispatchers.Default) {
                    runCatching {
                        EditorFindReplace.replaceAll(
                            oldText, search, insert, ignoreCase = !matchExactly
                        )
                    }
                }
                plan.onFailure {
                    result.text = it.message ?: "Replace All failed"
                    return@launch
                }
                val prepared = plan.getOrThrow()
                if (prepared.count == 0) {
                    result.text = "No matches found"
                    return@launch
                }
                if (field !== input || field.text?.toString() != oldText) {
                    result.text = "Document changed. Try Replace All again."
                    return@launch
                }
                AlertDialog.Builder(this@V2WorkspacePreviewActivity)
                    .setTitle("Replace all " + prepared.count + " matches?")
                    .setMessage("This updates the editor text. Use Save to write the file.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Replace") { _, _ ->
                        if (field !== input || field.text?.toString() != oldText) {
                            result.text = "Document changed. No replacements made."
                        } else {
                            val oldCursor = field.selectionEnd.coerceAtLeast(0)
                            val editable = field.text ?: return@setPositiveButton
                            editable.replace(0, editable.length, prepared.text)
                            field.setSelection(oldCursor.coerceAtMost(editable.length))
                            result.text = "Replaced " + prepared.count + " matches"
                        }
                    }
                    .show()
            }
        })
        AlertDialog.Builder(this)
            .setTitle("Find and Replace")
            .setView(panel)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun changed(textChanged: Boolean) {
        if (binding || busy) return
        val before = active ?: return
        val snapshot = capture(includeText = textChanged) ?: return
        if (snapshot.revision == before.revision || isPersisted(snapshot)) return
        if (textChanged) pendingTextChange = true
        status?.text = "Recovery pending · Revision " + snapshot.revision
        autosave?.cancel()
        // Content gets a quick debounce. Cursor/scroll-only changes are cheap
        // to keep in memory and can wait until the user stops interacting.
        val waitMs = PreviewPowerPolicy.autosaveDelay(pendingTextChange)
        autosave = lifecycleScope.launch {
            delay(waitMs)
            if (isPersisted(snapshot)) return@launch
            val saved = withContext(Dispatchers.IO) { workspace.persist(snapshot) }
            if (saved.isSuccess) markPersisted(snapshot)
            if (active?.id == snapshot.id && active?.revision == snapshot.revision) {
                if (saved.isSuccess) pendingTextChange = false
                status?.text = if (saved.isSuccess) {
                    "Recovery draft stored · Revision " + snapshot.revision
                } else {
                    "Recovery error: " + saved.exceptionOrNull()?.message
                }
            }
        }
    }

    private fun capture(includeText: Boolean = true): RecoveryDraft? {
        val old = active ?: return null
        val view = input ?: return old
        return workspace.edit(
            old,
            if (includeText) view.text?.toString().orEmpty() else old.content,
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
                markPersisted(snapshot)
                pendingTextChange = false
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
        val visible = entries.drafts.filter { it.trashedAtMs == null }
        AlertDialog.Builder(this@V2WorkspacePreviewActivity)
            .setTitle("Quick Document Switcher")
            .setItems(visible.map { it.displayName + " · " + it.content.length + " chars" }
                .toTypedArray()) { _, index ->
                loadDraft(visible[index].id)
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
                    val length = documentSize(uri)
                    val bytes = if (length != null && length > MAX_EDIT_BYTES) null
                        else readSmallFile(uri)
                    Pair(name, bytes)
                }
            }
            busy = false
            result.onSuccess { (name, bytes) ->
                if (bytes == null) {
                    showLargeReader(uri)
                    return@onSuccess
                }
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
                if (it is FileTooLargeForEditor) {
                    showLargeReader(uri)
                } else {
                    dialog("Could not read file", it.message.orEmpty())
                }
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
                if (output.size() + n > MAX_EDIT_BYTES) throw FileTooLargeForEditor()
                output.write(buffer, 0, n)
            }
            return output.toByteArray()
        }
    }

    private fun showLargeReader(uri: Uri) {
        status?.text = "Opening read-only large file viewer"
        startActivity(Intent(this, LargeTextReaderActivity::class.java).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    private fun documentSize(uri: Uri): Long? {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                    return cursor.getLong(index).takeIf { it >= 0 }
                }
            }
        return null
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
            "js", "jsx", "mjs" -> "JAVASCRIPT"
            "ts", "tsx" -> "TYPESCRIPT"
            "yaml", "yml" -> "YAML"
            "json" -> "JSON"
            "xml" -> "XML"
            "html" -> "HTML"
            "css" -> "CSS"
            else -> "PLAIN_TEXT"
        }

    private fun controls(items: List<Pair<String, () -> Unit>>) {
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(5), 0, dp(7))
        }
        items.forEach { (caption, action) ->
            buttons.addView(button(caption, action).apply {
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                    marginEnd = dp(6)
                }
            })
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(buttons)
        })
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(FG)
            minHeight = dp(48)
            minWidth = dp(48)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(SURFACE)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), MUTED)
            }
            setOnClickListener { if (!busy) action() }
        }

    private fun dialog(title: String, detail: String) {
        status?.text = title
        AlertDialog.Builder(this).setTitle(title).setMessage(detail)
            .setPositiveButton("OK", null).show()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()

    private class FileTooLargeForEditor :
        IllegalArgumentException("File exceeds the 2 MB editable preview limit")

    private companion object {
        const val MAX_EDIT_BYTES = 2 * 1024 * 1024
        const val MAX_MARKDOWN_RENDER_CHARS = 300_000
    }
}
