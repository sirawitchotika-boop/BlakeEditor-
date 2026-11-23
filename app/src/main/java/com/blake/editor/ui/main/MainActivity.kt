package com.blake.editor.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blake.editor.BlakeEditorApp
import com.blake.editor.intent.IncomingIntentParser
import com.blake.editor.ui.editor.BlakeEditorCanvas
import com.blake.editor.ui.editor.BlakeTopBar
import com.blake.editor.ui.editor.DocumentsSheet
import com.blake.editor.ui.editor.EditorCanvasController
import com.blake.editor.ui.editor.FindReplaceBar
import com.blake.editor.ui.editor.MarkdownQuickBar
import com.blake.editor.ui.editor.MarkdownReaderView
import com.blake.editor.ui.editor.SettingsSheet
import com.blake.editor.ui.reader.LargeFileReaderActivity
import com.blake.editor.ui.theme.BlakeTheme
import com.blake.editor.ui.theme.LocalBlakePalette

class MainActivity : ComponentActivity() {

    private val viewModel: BlakeViewModel by viewModels {
        val container = (application as BlakeEditorApp).appContainer
        BlakeViewModel.Factory(container, contentResolver)
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onOpenUri(uri)
        }
    }

    private var pendingCreateMimeType: String = "text/markdown"
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            viewModel.onSaveAsDestinationPicked(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingIntent(intent)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
            val editorController = remember { EditorCanvasController() }

            LaunchedEffect(Unit) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is BlakeSideEffect.LaunchSaveAs -> launchSaveAs(effect.suggestedName, effect.mimeType)
                        is BlakeSideEffect.OpenLargeReader -> openLargeReader(effect.uriString)
                        is BlakeSideEffect.ShareContent -> shareDocument(effect.title, effect.text)
                    }
                }
            }

            LaunchedEffect(state.snackbarMessage) {
                val msg = state.snackbarMessage ?: return@LaunchedEffect
                snackbarHostState.showSnackbar(msg)
                viewModel.onDismissSnackbar()
            }

            BlakeTheme(themeMode = state.themeMode) {
                BlakeMainScreen(
                    state = state,
                    editorController = editorController,
                    snackbarHostState = snackbarHostState,
                    onLaunchOpenPicker = {
                        openDocumentLauncher.launch(arrayOf("text/*", "text/plain", "text/markdown"))
                    },
                    viewModel = viewModel
                )
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
        viewModel.flushDraftOnPause()
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        val request = IncomingIntentParser.parse(incoming) ?: return
        viewModel.onOpenUri(request.uri)
    }

    private fun launchSaveAs(suggestedName: String, mimeType: String) {
        pendingCreateMimeType = mimeType
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType.ifBlank { "text/plain" }
            putExtra(Intent.EXTRA_TITLE, suggestedName)
        }
        createDocumentLauncher.launch(intent)
    }

    private fun openLargeReader(uriString: String) {
        val uri = Uri.parse(uriString)
        startActivity(Intent(this, LargeFileReaderActivity::class.java).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    private fun shareDocument(title: String, content: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        startActivity(Intent.createChooser(shareIntent, "Share $title"))
    }
}

@Composable
private fun BlakeMainScreen(
    state: BlakeUiState,
    editorController: EditorCanvasController,
    snackbarHostState: SnackbarHostState,
    onLaunchOpenPicker: () -> Unit,
    viewModel: BlakeViewModel
) {
    val palette = LocalBlakePalette.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BlakeTopBar(
                title = state.document.title,
                isDirty = state.document.isDirty,
                isExternal = state.document.isExternal,
                isReadMode = state.isReadMode,
                isFindOpen = state.find.isOpen,
                showMarkdownBar = state.showMarkdownBar,
                onOpenDocuments = { viewModel.onSetDocumentsSheetOpen(true) },
                onRenameDraft = { viewModel.onSetRenameDialogOpen(true) },
                onToggleReadMode = { viewModel.onToggleReadMode() },
                onSave = { viewModel.onSaveClicked() },
                onToggleFind = {
                    val cursor = editorController.currentSelection().first
                    viewModel.onToggleFind(cursorOffset = cursor)
                },
                onSaveAs = { viewModel.onSaveAsClicked() },
                onShare = { viewModel.onShareCurrent() },
                onToggleMarkdownBar = { viewModel.onSetShowMarkdownBar(!state.showMarkdownBar) },
                onOpenSettings = { viewModel.onSetSettingsSheetOpen(true) }
            )

            if (state.isBusy) {
                LinearProgressIndicator(
                    color = palette.accent,
                    trackColor = palette.surface,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AnimatedVisibility(
                visible = state.find.isOpen && !state.isReadMode,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                FindReplaceBar(
                    query = state.find.query,
                    replacement = state.find.replacement,
                    showReplace = state.find.showReplace,
                    matchCase = state.find.matchCase,
                    activeMatchIndex = state.find.activeIndex,
                    totalMatches = state.find.matches.size,
                    onQueryChange = { q ->
                        val cursor = editorController.currentSelection().first
                        viewModel.onFindQueryChange(q, cursorOffset = cursor)
                    },
                    onReplacementChange = viewModel::onReplacementChange,
                    onToggleReplace = viewModel::onToggleReplaceRow,
                    onToggleMatchCase = {
                        val cursor = editorController.currentSelection().first
                        viewModel.onToggleMatchCase(cursorOffset = cursor)
                    },
                    onPreviousMatch = viewModel::onPreviousMatch,
                    onNextMatch = viewModel::onNextMatch,
                    onReplaceCurrent = viewModel::onReplaceCurrent,
                    onReplaceAll = viewModel::onReplaceAll,
                    onClose = viewModel::onCloseFind
                )
            }

            // Main Unobstructed Writing / Reading Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (state.isReadMode) {
                    MarkdownReaderView(
                        markdown = state.document.content,
                        baseFontSp = state.fontSize.readerSp,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    BlakeEditorCanvas(
                        content = state.document.content,
                        documentVersion = state.documentVersion,
                        pendingSelection = state.pendingSelection,
                        fontSizeSp = state.fontSize.editorSp,
                        searchMatches = state.find.matches,
                        activeMatchIndex = state.find.activeIndex,
                        searchJumpNonce = state.find.jumpNonce,
                        controller = editorController,
                        onContentChanged = viewModel::onContentChanged,
                        onPendingSelectionConsumed = viewModel::onPendingSelectionConsumed,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            AnimatedVisibility(
                visible = !state.isReadMode && state.showMarkdownBar,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                MarkdownQuickBar(
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    onUndo = viewModel::onUndo,
                    onRedo = viewModel::onRedo,
                    onFormatAction = { action ->
                        val (start, end) = editorController.currentSelection()
                        viewModel.onApplyMarkdownAction(action, start, end)
                    },
                    onHide = { viewModel.onSetShowMarkdownBar(false) }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (!state.isReadMode && state.showMarkdownBar) 52.dp else 12.dp, start = 16.dp, end = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = palette.ink,
                contentColor = palette.canvas
            )
        }
    }

    if (state.isDocumentsSheetOpen) {
        DocumentsSheet(
            activeDraftId = state.document.id,
            drafts = state.drafts,
            recentFiles = state.recentFiles,
            onDismiss = { viewModel.onSetDocumentsSheetOpen(false) },
            onNewNote = viewModel::onNewNote,
            onOpenFilePicker = onLaunchOpenPicker,
            onSelectDraft = viewModel::onSelectDraft,
            onDeleteDraft = viewModel::onDeleteDraft,
            onOpenRecent = { recent -> viewModel.onOpenUri(Uri.parse(recent.uriString)) },
            onRemoveRecent = viewModel::onRemoveRecent
        )
    }

    if (state.isSettingsSheetOpen) {
        SettingsSheet(
            themeMode = state.themeMode,
            fontSize = state.fontSize,
            showMarkdownBar = state.showMarkdownBar,
            onSelectTheme = viewModel::onSelectTheme,
            onSelectFontSize = viewModel::onSelectFontSize,
            onToggleMarkdownBar = viewModel::onSetShowMarkdownBar,
            onDismiss = { viewModel.onSetSettingsSheetOpen(false) }
        )
    }

    if (state.isRenameDialogOpen) {
        var draftName by remember(state.document.title) { mutableStateOf(state.document.title) }
        AlertDialog(
            onDismissRequest = { viewModel.onSetRenameDialogOpen(false) },
            containerColor = palette.surface,
            title = { Text("Rename Note", color = palette.ink) },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onRenameCurrentDraft(draftName) }) {
                    Text("Rename", color = palette.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onSetRenameDialogOpen(false) }) {
                    Text("Cancel", color = palette.inkSecondary)
                }
            }
        )
    }
}
