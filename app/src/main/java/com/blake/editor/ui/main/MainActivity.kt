package com.blake.editor.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.LinearLayout
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.blake.editor.BlakeEditorApp
import com.blake.editor.R
import com.blake.editor.core.model.SyntaxMode
import com.blake.editor.core.model.ThemeMode
import com.blake.editor.editor.EditorCommand
import com.blake.editor.editor.EditorController
import com.blake.editor.editor.NativeCodePresentation
import com.blake.editor.editor.NativeDocumentSyncGuard
import com.blake.editor.editor.NativeInputEditText
import com.blake.editor.editor.NativeUndoHistory
import com.blake.editor.editor.EditorEvent
import com.blake.editor.editor.v2.CodeSyntaxScanner
import com.blake.editor.intent.IncomingIntentParser
import com.blake.editor.settings.ThemeController
import com.blake.editor.ui.system.EdgeToEdgeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels {
        val app = application as BlakeEditorApp
        MainViewModel.provideFactory(
            app.appContainer.documentRepository,
            app.appContainer.draftRepository,
            app.appContainer.sessionStore,
            app.appContainer.settingsRepository
        )
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.openDocument(it) }
    }

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let { viewModel.saveAsDocument(it) }
    }

    private lateinit var editorController: EditorController
    private lateinit var rootContainer: View
    private lateinit var topBar: View
    private lateinit var fileBar: View
    private lateinit var tvFileName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvEditorInfo: TextView
    private lateinit var webViewEditor: WebView
    private lateinit var nativeEditor: NativeInputEditText
    private val documentSync = NativeDocumentSyncGuard()
    private val nativeUndo = NativeUndoHistory()
    private var applyingExternalText = false

    private lateinit var btnNew: Button
    private lateinit var btnOpen: Button
    private lateinit var btnSave: Button
    private lateinit var btnSaveAs: Button
    private lateinit var btnUndo: Button
    private lateinit var btnRedo: Button
    private lateinit var btnTheme: Button
    private lateinit var btnPreview: Button

    private var lastAppliedTheme: ThemeMode? = null
    private var lastSyntaxMode: SyntaxMode? = null
    private var lastHighlightedGeneration: Long? = null
    private var highlightJob: Job? = null
    private var lastPreviewState: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootContainer = findViewById(R.id.rootContainer)
        topBar = findViewById(R.id.topBar)
        fileBar = findViewById(R.id.fileBar)
        tvFileName = findViewById(R.id.tvFileName)
        tvStatus = findViewById(R.id.tvStatus)
        tvEditorInfo = findViewById(R.id.tvEditorInfo)
        webViewEditor = findViewById(R.id.webViewEditor)

        btnNew = findViewById(R.id.btnNew)
        btnOpen = findViewById(R.id.btnOpen)
        btnSave = findViewById(R.id.btnSave)
        btnSaveAs = findViewById(R.id.btnSaveAs)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnTheme = findViewById(R.id.btnTheme)
        btnPreview = findViewById(R.id.btnPreview)

        EdgeToEdgeController.setupWindowInsets(rootContainer)
        setupNativeEditor()
        setupEditor()
        setupListeners()
        setupBackNavigation()
        observeState()

        if (savedInstanceState == null) {
            val request = IncomingIntentParser.parse(intent)
            if (request != null) {
                viewModel.openDocument(request.uri)
            } else {
                viewModel.restoreSessionOrDraft()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onPause() {
        highlightJob?.cancel()
        super.onPause()
        viewModel.flushDraft()
    }

    override fun onResume() {
        super.onResume()
        if (::nativeEditor.isInitialized) {
            scheduleSyntaxHighlight(viewModel.uiState.value.syntaxMode, immediate = true)
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val request = IncomingIntentParser.parse(intent) ?: return
        if (viewModel.uiState.value.isDirty) {
            showUnsavedChangesDialog {
                viewModel.openDocument(request.uri)
            }
        } else {
            viewModel.openDocument(request.uri)
        }
    }

    /**
     * Android native text owns the input connection. Ace/WebView is used
     * only for the offline formatted preview, not for live keyboard input.
     */
    private fun setupNativeEditor() {
        val holder = webViewEditor.parent as LinearLayout
        val position = holder.indexOfChild(webViewEditor)
        nativeEditor = NativeInputEditText(this)
        holder.addView(nativeEditor, position, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        webViewEditor.visibility = View.GONE

        nativeEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int,
                count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int,
                before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (applyingExternalText) return
                val content = s?.toString().orEmpty()
                nativeUndo.edited(content, SystemClock.uptimeMillis())
                viewModel.onContentChanged(content)
                scheduleSyntaxHighlight(viewModel.uiState.value.syntaxMode)
            }
        })
        nativeEditor.onCursorMoved = { line, col ->
            if (!applyingExternalText) {
                viewModel.onCursorPositionChanged(line, col)
            }
        }
    }

    private fun loadNativeDocument(state: MainUiState) {
        // StateFlow emits after every key/cursor move. NEVER call setText
        // for those echoes: it breaks Thai composing text and caret position.
        if (!documentSync.shouldLoad(state.documentGeneration)) return
        applyingExternalText = true
        try {
            nativeEditor.setText(state.content)
            val offset = NativeDocumentSyncGuard.offsetForLineColumn(
                state.content, state.cursorLine, state.cursorColumn
            )
            nativeEditor.setSelection(offset)
            nativeEditor.scrollTo(0, 0)
            nativeUndo.reset(state.content)
        } finally {
            applyingExternalText = false
        }
    }

    private fun applyUndoOrRedo(content: String?) {
        if (content == null || viewModel.uiState.value.isPreviewing) return
        applyingExternalText = true
        try {
            val offset = nativeEditor.selectionStart.coerceAtLeast(0)
            nativeEditor.setText(content)
            nativeEditor.setSelection(offset.coerceAtMost(content.length))
        } finally {
            applyingExternalText = false
        }
        viewModel.onContentChanged(content)
    }

    /**
     * Scan off the UI thread. Only the original document's current EditText is
     * painted, with no setText/selection/scroll changes or WebView input.
     */
    private fun scheduleSyntaxHighlight(mode: SyntaxMode, immediate: Boolean = false) {
        highlightJob?.cancel()
        if (!::nativeEditor.isInitialized ||
            !NativeCodePresentation.shouldHighlight(mode, nativeEditor.length()) ||
            viewModel.uiState.value.isPreviewing) {
            if (::nativeEditor.isInitialized) nativeEditor.clearSyntaxTokens()
            return
        }
        val snapshot = nativeEditor.text?.toString().orEmpty()
        highlightJob = lifecycleScope.launch {
            if (!immediate) delay(220L)
            val tokens = withContext(Dispatchers.Default) {
                CodeSyntaxScanner.scan(snapshot, mode.name)
            }
            val current = viewModel.uiState.value
            if (!current.isPreviewing && current.syntaxMode == mode &&
                nativeEditor.text?.toString() == snapshot) {
                nativeEditor.applySyntaxTokens(tokens,
                    NativeCodePresentation.colorsFor(current.themeMode))
            }
        }
    }

    private fun setupEditor() {
        editorController = EditorController(webViewEditor) { event ->
            when (event) {
                is EditorEvent.EditorReady -> {
                    val state = viewModel.uiState.value
                    editorController.execute(EditorCommand.SetTheme(state.themeMode))
                    if (state.isPreviewing) {
                        editorController.execute(EditorCommand.ShowPreview(state.content))
                    }
                }
                is EditorEvent.ContentChanged,
                is EditorEvent.CursorPositionChanged,
                is EditorEvent.ScrollChanged,
                is EditorEvent.ContentDelivered -> Unit
            }
        }
    }

    private fun setupListeners() {
        btnNew.setOnClickListener {
            if (viewModel.uiState.value.isDirty) {
                showUnsavedChangesDialog {
                    // Let the new ViewModel state drive exactly one reset.
                    viewModel.newDocument()
                }
            } else {
                // The new ViewModel state drives the reset once.
                viewModel.newDocument()
            }
        }

        btnOpen.setOnClickListener {
            if (viewModel.uiState.value.isDirty) {
                showUnsavedChangesDialog {
                    openDocumentLauncher.launch(arrayOf("*/*"))
                }
            } else {
                openDocumentLauncher.launch(arrayOf("*/*"))
            }
        }

        btnSave.setOnClickListener {
            if (viewModel.uiState.value.currentUri == null) {
                createDocumentLauncher.launch(viewModel.uiState.value.fileName)
            } else {
                viewModel.saveDocument()
            }
        }

        btnSaveAs.setOnClickListener {
            createDocumentLauncher.launch(viewModel.uiState.value.fileName)
        }

        btnUndo.setOnClickListener { applyUndoOrRedo(nativeUndo.undo()) }

        btnRedo.setOnClickListener { applyUndoOrRedo(nativeUndo.redo()) }

        btnTheme.setOnClickListener {
            showThemeSelectionDialog()
        }

        btnPreview.setOnClickListener {
            viewModel.togglePreview()
        }
    }

    private fun showThemeSelectionDialog() {
        val themes = ThemeMode.values()
        val themeNames = themes.map { it.displayName }.toTypedArray()
        val currentIndex = themes.indexOf(viewModel.uiState.value.themeMode).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_theme_title)
            .setSingleChoiceItems(themeNames, currentIndex) { dialog, which ->
                viewModel.setThemeMode(themes[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.uiState.value.isPreviewing) {
                    viewModel.togglePreview()
                    return
                }
                if (viewModel.uiState.value.isDirty) {
                    showUnsavedChangesDialog {
                        finish()
                    }
                } else {
                    finish()
                }
            }
        })
    }

    private fun showUnsavedChangesDialog(onDiscard: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_unsaved_title)
            .setMessage(R.string.dialog_unsaved_message)
            .setPositiveButton(R.string.dialog_discard) { _, _ -> onDiscard() }
            .setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val dirtyIndicator = if (state.isDirty) "*" else ""
                    tvFileName.text = "${state.fileName}$dirtyIndicator"
                    tvStatus.text = state.statusMessage

                    val modeText = if (state.isPreviewing) "Preview"
                        else state.syntaxMode.displayName + " · Native"
                    tvEditorInfo.text = "Ln ${state.cursorLine}, Col ${state.cursorColumn} | $modeText"
                    val newDocument = lastHighlightedGeneration != state.documentGeneration
                    if (newDocument) lastHighlightedGeneration = state.documentGeneration
                    loadNativeDocument(state)

                    val themeChanged = state.themeMode != lastAppliedTheme
                    val modeChanged = state.syntaxMode != lastSyntaxMode
                    if (themeChanged) {
                        lastAppliedTheme = state.themeMode
                        applyThemePalette(state.themeMode)
                        editorController.execute(EditorCommand.SetTheme(state.themeMode))
                    }
                    if (modeChanged || themeChanged) {
                        lastSyntaxMode = state.syntaxMode
                        val palette = ThemeController.getPalette(state.themeMode)
                        nativeEditor.configureGutter(
                            NativeCodePresentation.showLineNumbers(state.syntaxMode),
                            palette.surfaceColor, palette.secondaryTextColor
                        )
                    }
                    if (newDocument || modeChanged || themeChanged) {
                        scheduleSyntaxHighlight(state.syntaxMode, immediate = true)
                    }

                    // The WebView never owns the live keyboard.
                    if (state.isPreviewing != lastPreviewState) {
                        lastPreviewState = state.isPreviewing
                        nativeEditor.visibility = if (state.isPreviewing) View.GONE else View.VISIBLE
                        webViewEditor.visibility = if (state.isPreviewing) View.VISIBLE else View.GONE
                        if (state.isPreviewing) {
                            btnPreview.text = getString(R.string.action_edit)
                            btnUndo.isEnabled = false
                            btnRedo.isEnabled = false
                            btnUndo.alpha = 0.5f
                            btnRedo.alpha = 0.5f
                            editorController.execute(EditorCommand.ShowPreview(state.content))
                        } else {
                            btnPreview.text = getString(R.string.action_preview)
                            btnUndo.isEnabled = true
                            btnRedo.isEnabled = true
                            btnUndo.alpha = 1.0f
                            btnRedo.alpha = 1.0f
                            editorController.execute(EditorCommand.HidePreview)
                        }
                    }

                }
            }
        }
    }

    private fun applyThemePalette(themeMode: ThemeMode) {
        EdgeToEdgeController.applyEdgeToEdge(this, themeMode)
        val palette = ThemeController.getPalette(themeMode)

        rootContainer.setBackgroundColor(palette.surfaceColor)
        topBar.setBackgroundColor(palette.surfaceColor)
        fileBar.setBackgroundColor(palette.surfaceColor)
        tvStatus.setBackgroundColor(palette.surfaceColor)
        webViewEditor.setBackgroundColor(palette.backgroundColor)
        nativeEditor.setBackgroundColor(palette.backgroundColor)
        nativeEditor.setTextColor(palette.primaryTextColor)

        tvFileName.setTextColor(palette.primaryTextColor)
        tvEditorInfo.setTextColor(palette.secondaryTextColor)
        tvStatus.setTextColor(palette.secondaryTextColor)

        val buttons = listOf(btnNew, btnOpen, btnSave, btnSaveAs, btnUndo, btnRedo, btnTheme, btnPreview)
        for (btn in buttons) {
            btn.setBackgroundColor(palette.buttonBackgroundColor)
            btn.setTextColor(palette.buttonTextColor)
        }
    }
}
