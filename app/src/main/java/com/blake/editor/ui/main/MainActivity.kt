package com.blake.editor.ui.main

import android.content.Intent
import android.os.Bundle
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
import com.blake.editor.core.model.ThemeMode
import com.blake.editor.editor.EditorCommand
import com.blake.editor.editor.EditorController
import com.blake.editor.editor.EditorEvent
import com.blake.editor.intent.IncomingIntentParser
import com.blake.editor.settings.ThemeController
import com.blake.editor.ui.system.EdgeToEdgeController
import kotlinx.coroutines.launch

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

    private lateinit var btnNew: Button
    private lateinit var btnOpen: Button
    private lateinit var btnSave: Button
    private lateinit var btnSaveAs: Button
    private lateinit var btnUndo: Button
    private lateinit var btnRedo: Button
    private lateinit var btnTheme: Button
    private lateinit var btnPreview: Button

    private var lastLoadedContent = ""
    private var lastAppliedTheme: ThemeMode? = null
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
        super.onPause()
        viewModel.flushDraft()
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

    private fun setupEditor() {
        editorController = EditorController(webViewEditor) { event ->
            when (event) {
                is EditorEvent.EditorReady -> {
                    lastLoadedContent = viewModel.uiState.value.content
                    editorController.execute(
                        EditorCommand.SetContent(
                            viewModel.uiState.value.content,
                            viewModel.uiState.value.syntaxMode
                        )
                    )
                    editorController.execute(
                        EditorCommand.SetTheme(viewModel.uiState.value.themeMode)
                    )
                    if (viewModel.uiState.value.isPreviewing) {
                        editorController.execute(EditorCommand.ShowPreview(viewModel.uiState.value.content))
                    } else if (viewModel.uiState.value.cursorLine > 1) {
                        editorController.execute(EditorCommand.GoToLine(viewModel.uiState.value.cursorLine))
                    }
                }
                is EditorEvent.ContentChanged -> {
                    lastLoadedContent = event.text
                    viewModel.onContentChanged(event.text)
                }
                is EditorEvent.CursorPositionChanged -> {
                    viewModel.onCursorPositionChanged(event.line, event.column)
                }
                is EditorEvent.ScrollChanged -> {
                    viewModel.onScrollPositionChanged(event.scrollY)
                }
                is EditorEvent.ContentDelivered -> {
                    // Response to RequestContent
                }
            }
        }
    }

    private fun setupListeners() {
        btnNew.setOnClickListener {
            if (viewModel.uiState.value.isDirty) {
                showUnsavedChangesDialog {
                    viewModel.newDocument()
                    lastLoadedContent = ""
                    editorController.execute(EditorCommand.SetContent("", viewModel.uiState.value.syntaxMode))
                }
            } else {
                viewModel.newDocument()
                lastLoadedContent = ""
                editorController.execute(EditorCommand.SetContent("", viewModel.uiState.value.syntaxMode))
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

        btnUndo.setOnClickListener {
            editorController.execute(EditorCommand.Undo)
        }

        btnRedo.setOnClickListener {
            editorController.execute(EditorCommand.Redo)
        }

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

                    val modeText = if (state.isPreviewing) "Preview" else state.syntaxMode.displayName
                    tvEditorInfo.text = "Ln ${state.cursorLine}, Col ${state.cursorColumn} | $modeText"

                    // Theme switching
                    if (state.themeMode != lastAppliedTheme) {
                        lastAppliedTheme = state.themeMode
                        applyThemePalette(state.themeMode)
                        editorController.execute(EditorCommand.SetTheme(state.themeMode))
                    }

                    // Preview mode toggle sync
                    if (state.isPreviewing != lastPreviewState) {
                        lastPreviewState = state.isPreviewing
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

                    // Content sync
                    if (state.content != lastLoadedContent) {
                        lastLoadedContent = state.content
                        editorController.execute(EditorCommand.SetContent(state.content, state.syntaxMode))
                        if (state.isPreviewing) {
                            editorController.execute(EditorCommand.ShowPreview(state.content))
                        } else if (state.cursorLine > 1) {
                            editorController.execute(EditorCommand.GoToLine(state.cursorLine))
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
