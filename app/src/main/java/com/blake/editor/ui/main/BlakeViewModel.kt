package com.blake.editor.ui.main

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blake.editor.app.AppContainer
import com.blake.editor.core.util.FileNameUtils
import com.blake.editor.editor.DocumentSearch
import com.blake.editor.editor.NativeUndoHistory
import com.blake.editor.file.DocumentMetadataReader
import com.blake.editor.file.LargeTextOpenGate
import com.blake.editor.markdown.MarkdownAction
import com.blake.editor.markdown.MarkdownFormatActions
import com.blake.editor.model.ActiveDocument
import com.blake.editor.model.AppThemeMode
import com.blake.editor.model.EditorFontSize
import com.blake.editor.model.RecentFile
import com.blake.editor.model.ScratchDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class BlakeViewModel(
    private val container: AppContainer,
    private val contentResolver: ContentResolver
) : ViewModel() {

    private val undoHistory = NativeUndoHistory()
    private var recoveryDebounceJob: Job? = null

    private val _uiState = MutableStateFlow(BlakeUiState())
    val uiState: StateFlow<BlakeUiState> = _uiState.asStateFlow()

    private val _effects = Channel<BlakeSideEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        observePreferences()
        restoreInitialWorkspace()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            container.preferences.settingsFlow.collect { prefs ->
                _uiState.update { state ->
                    state.copy(
                        themeMode = prefs.themeMode,
                        fontSize = prefs.fontSize,
                        showMarkdownBar = prefs.showMarkdownBar
                    )
                }
            }
        }
    }

    private fun restoreInitialWorkspace() {
        viewModelScope.launch {
            val (workspace, recents) = withContext(Dispatchers.IO) {
                container.draftRecoveryStore.load() to container.recentDocumentsStore.load()
            }
            val restored = workspace.activeDraftId
                ?.let { activeId -> workspace.drafts.firstOrNull { it.id == activeId } }
                ?: workspace.drafts.firstOrNull()

            if (restored != null) {
                val doc = ActiveDocument(
                    id = restored.id,
                    title = restored.title,
                    uriString = restored.uriString,
                    mimeType = restored.mimeType,
                    content = restored.content,
                    isDirty = restored.isDirty,
                    // Restored drafts do not hold a verified opening snapshot; Save triggers Save As safely.
                    hasVerifiedSnapshot = false,
                    updatedAtMs = restored.updatedAtMs
                )
                undoHistory.reset(doc.content)
                _uiState.update {
                    it.copy(
                        document = doc,
                        documentVersion = it.documentVersion + 1,
                        drafts = workspace.drafts,
                        recentFiles = recents,
                        canUndo = false,
                        canRedo = false
                    )
                }
            } else {
                val blank = ActiveDocument()
                undoHistory.reset("")
                _uiState.update {
                    it.copy(
                        document = blank,
                        documentVersion = it.documentVersion + 1,
                        drafts = emptyList(),
                        recentFiles = recents
                    )
                }
            }
        }
    }

    fun onContentChanged(newContent: String) {
        val current = _uiState.value.document
        if (newContent == current.content) return
        val now = System.currentTimeMillis()
        undoHistory.edited(newContent, now)

        val updatedDoc = current.copy(
            content = newContent,
            isDirty = true,
            updatedAtMs = now
        )
        _uiState.update { state ->
            val updatedFind = recomputeFind(state.find, newContent, keepCurrentIndex = true)
            state.copy(
                document = updatedDoc,
                canUndo = undoHistory.canUndo,
                canRedo = undoHistory.canRedo,
                find = updatedFind
            )
        }
        scheduleDraftSave(updatedDoc)
    }

    fun onPendingSelectionConsumed() {
        _uiState.update { it.copy(pendingSelection = null) }
    }

    fun onUndo() {
        val previous = undoHistory.undo() ?: return
        applyProgrammaticText(previous, selection = previous.length to previous.length)
    }

    fun onRedo() {
        val next = undoHistory.redo() ?: return
        applyProgrammaticText(next, selection = next.length to next.length)
    }

    fun onApplyMarkdownAction(action: MarkdownAction, selectionStart: Int, selectionEnd: Int) {
        val current = _uiState.value.document.content
        val result = MarkdownFormatActions.apply(current, selectionStart, selectionEnd, action)
        if (result.text == current) return
        undoHistory.edited(result.text, System.currentTimeMillis())
        applyProgrammaticText(
            updatedText = result.text,
            selection = result.selectionStart to result.selectionEnd
        )
    }

    private fun applyProgrammaticText(updatedText: String, selection: Pair<Int, Int>?) {
        val now = System.currentTimeMillis()
        val updatedDoc = _uiState.value.document.copy(
            content = updatedText,
            isDirty = true,
            updatedAtMs = now
        )
        _uiState.update { state ->
            val updatedFind = recomputeFind(state.find, updatedText, keepCurrentIndex = true)
            state.copy(
                document = updatedDoc,
                documentVersion = state.documentVersion + 1,
                pendingSelection = selection,
                canUndo = undoHistory.canUndo,
                canRedo = undoHistory.canRedo,
                find = updatedFind
            )
        }
        scheduleDraftSave(updatedDoc)
    }

    // --- Find & Replace ---

    fun onToggleFind(cursorOffset: Int = 0) {
        _uiState.update { state ->
            if (state.find.isOpen) {
                state.copy(find = FindReplaceState())
            } else {
                val refreshed = recomputeFind(
                    state.find.copy(isOpen = true),
                    state.document.content,
                    cursorOffset = cursorOffset,
                    jump = true
                )
                state.copy(isReadMode = false, find = refreshed)
            }
        }
    }

    fun onCloseFind() {
        _uiState.update { it.copy(find = FindReplaceState()) }
    }

    fun onFindQueryChange(query: String, cursorOffset: Int = 0) {
        _uiState.update { state ->
            val nextFind = recomputeFind(
                state.find.copy(query = query),
                state.document.content,
                cursorOffset = cursorOffset,
                jump = true
            )
            state.copy(find = nextFind)
        }
    }

    fun onReplacementChange(replacement: String) {
        _uiState.update { it.copy(find = it.find.copy(replacement = replacement)) }
    }

    fun onToggleReplaceRow() {
        _uiState.update { it.copy(find = it.find.copy(showReplace = !it.find.showReplace)) }
    }

    fun onToggleMatchCase(cursorOffset: Int = 0) {
        _uiState.update { state ->
            val nextFind = recomputeFind(
                state.find.copy(matchCase = !state.find.matchCase),
                state.document.content,
                cursorOffset = cursorOffset,
                jump = true
            )
            state.copy(find = nextFind)
        }
    }

    fun onNextMatch() {
        _uiState.update { state ->
            val f = state.find
            if (f.matches.isEmpty()) return@update state
            val nextIdx = DocumentSearch.nextIndex(f.activeIndex, f.matches.size)
            state.copy(
                find = f.copy(
                    activeIndex = nextIdx,
                    jumpNonce = f.jumpNonce + 1
                )
            )
        }
    }

    fun onPreviousMatch() {
        _uiState.update { state ->
            val f = state.find
            if (f.matches.isEmpty()) return@update state
            val prevIdx = DocumentSearch.previousIndex(f.activeIndex, f.matches.size)
            state.copy(
                find = f.copy(
                    activeIndex = prevIdx,
                    jumpNonce = f.jumpNonce + 1
                )
            )
        }
    }

    fun onReplaceCurrent() {
        val state = _uiState.value
        val match = state.find.matches.getOrNull(state.find.activeIndex) ?: return
        val result = DocumentSearch.replaceAt(state.document.content, match, state.find.replacement)
        val now = System.currentTimeMillis()
        undoHistory.edited(result.text, now)

        val updatedDoc = state.document.copy(
            content = result.text,
            isDirty = true,
            updatedAtMs = now
        )
        val nextFind = recomputeFind(
            state.find,
            result.text,
            cursorOffset = result.newCursorOffset,
            jump = true
        )
        _uiState.update {
            it.copy(
                document = updatedDoc,
                documentVersion = it.documentVersion + 1,
                canUndo = undoHistory.canUndo,
                canRedo = undoHistory.canRedo,
                find = nextFind
            )
        }
        scheduleDraftSave(updatedDoc)
    }

    fun onReplaceAll() {
        val state = _uiState.value
        if (state.find.query.isEmpty()) return
        val result = runCatching {
            DocumentSearch.replaceAll(
                text = state.document.content,
                query = state.find.query,
                replacement = state.find.replacement,
                ignoreCase = !state.find.matchCase
            )
        }.getOrElse { err ->
            showMessage(err.message ?: "Replace failed.")
            return
        }
        if (result.replacedCount == 0) return

        val now = System.currentTimeMillis()
        undoHistory.edited(result.text, now)
        val updatedDoc = state.document.copy(
            content = result.text,
            isDirty = true,
            updatedAtMs = now
        )
        val nextFind = recomputeFind(state.find, result.text, jump = false)
        _uiState.update {
            it.copy(
                document = updatedDoc,
                documentVersion = it.documentVersion + 1,
                canUndo = undoHistory.canUndo,
                canRedo = undoHistory.canRedo,
                find = nextFind,
                snackbarMessage = "Replaced ${result.replacedCount} occurrence${if (result.replacedCount == 1) "" else "s"}"
            )
        }
        scheduleDraftSave(updatedDoc)
    }

    private fun recomputeFind(
        find: FindReplaceState,
        content: String,
        cursorOffset: Int = 0,
        keepCurrentIndex: Boolean = false,
        jump: Boolean = false
    ): FindReplaceState {
        if (!find.isOpen || find.query.isEmpty()) {
            return find.copy(matches = emptyList(), activeIndex = -1)
        }
        val matches = DocumentSearch.findAll(
            text = content,
            query = find.query,
            ignoreCase = !find.matchCase
        )
        val newActive = when {
            matches.isEmpty() -> -1
            keepCurrentIndex && find.activeIndex in matches.indices -> find.activeIndex
            else -> DocumentSearch.nearestMatchIndex(matches, cursorOffset)
        }
        return find.copy(
            matches = matches,
            activeIndex = newActive,
            jumpNonce = if (jump && newActive >= 0) find.jumpNonce + 1 else find.jumpNonce
        )
    }

    // --- Read Mode / Sheets / Preferences ---

    fun onToggleReadMode() {
        _uiState.update { state ->
            val enteringRead = !state.isReadMode
            state.copy(
                isReadMode = enteringRead,
                find = if (enteringRead) FindReplaceState() else state.find
            )
        }
    }

    fun onSetDocumentsSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isDocumentsSheetOpen = open) }
    }

    fun onSetSettingsSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isSettingsSheetOpen = open) }
    }

    fun onSetRenameDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isRenameDialogOpen = open) }
    }

    fun onRenameCurrentDraft(newTitleRaw: String) {
        val current = _uiState.value.document
        if (current.isExternal) return
        val sanitized = FileNameUtils.sanitizeFileName(newTitleRaw, defaultName = "Untitled.md")
        val finalName = if (sanitized.contains('.')) sanitized else "$sanitized.md"
        val updated = current.copy(title = finalName, updatedAtMs = System.currentTimeMillis())
        _uiState.update { it.copy(document = updated, isRenameDialogOpen = false) }
        persistDraftImmediately(updated)
    }

    fun onSelectTheme(mode: AppThemeMode) {
        viewModelScope.launch { container.preferences.setThemeMode(mode) }
    }

    fun onSelectFontSize(size: EditorFontSize) {
        viewModelScope.launch { container.preferences.setFontSize(size) }
    }

    fun onSetShowMarkdownBar(visible: Boolean) {
        viewModelScope.launch { container.preferences.setShowMarkdownBar(visible) }
    }

    fun onShareCurrent() {
        val doc = _uiState.value.document
        viewModelScope.launch {
            _effects.send(BlakeSideEffect.ShareContent(doc.title, doc.content))
        }
    }

    fun onDismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // --- Documents & File Operations ---

    fun onNewNote() {
        val current = _uiState.value.document
        if (current.content.isNotBlank() || current.isDirty) {
            persistDraftImmediately(current)
        }
        val existingTitles = _uiState.value.drafts.map { it.title }.toSet()
        val title = nextUntitledName(existingTitles)
        val newDoc = ActiveDocument(
            id = UUID.randomUUID().toString(),
            title = title,
            uriString = null,
            mimeType = "text/markdown",
            content = "",
            isDirty = false,
            hasVerifiedSnapshot = false
        )
        undoHistory.reset("")
        _uiState.update {
            it.copy(
                document = newDoc,
                documentVersion = it.documentVersion + 1,
                pendingSelection = 0 to 0,
                isReadMode = false,
                canUndo = false,
                canRedo = false,
                find = FindReplaceState()
            )
        }
        persistDraftImmediately(newDoc)
    }

    fun onSelectDraft(draft: ScratchDraft) {
        val current = _uiState.value.document
        if (current.id == draft.id) return
        if (current.content.isNotBlank() || current.isDirty) {
            persistDraftImmediately(current)
        }
        val doc = ActiveDocument(
            id = draft.id,
            title = draft.title,
            uriString = draft.uriString,
            mimeType = draft.mimeType,
            content = draft.content,
            isDirty = draft.isDirty,
            hasVerifiedSnapshot = false,
            updatedAtMs = draft.updatedAtMs
        )
        undoHistory.reset(doc.content)
        _uiState.update {
            it.copy(
                document = doc,
                documentVersion = it.documentVersion + 1,
                pendingSelection = 0 to 0,
                isReadMode = false,
                canUndo = false,
                canRedo = false,
                find = FindReplaceState()
            )
        }
        persistDraftImmediately(doc)
    }

    fun onDeleteDraft(draft: ScratchDraft) {
        viewModelScope.launch {
            val nextWorkspace = withContext(Dispatchers.IO) {
                container.draftRecoveryStore.remove(draft.id)
            }
            val current = _uiState.value.document
            if (current.id == draft.id) {
                val fallback = nextWorkspace.drafts.firstOrNull()
                if (fallback != null) {
                    val doc = ActiveDocument(
                        id = fallback.id,
                        title = fallback.title,
                        uriString = fallback.uriString,
                        mimeType = fallback.mimeType,
                        content = fallback.content,
                        isDirty = fallback.isDirty,
                        hasVerifiedSnapshot = false,
                        updatedAtMs = fallback.updatedAtMs
                    )
                    undoHistory.reset(doc.content)
                    _uiState.update {
                        it.copy(
                            document = doc,
                            documentVersion = it.documentVersion + 1,
                            drafts = nextWorkspace.drafts,
                            canUndo = false,
                            canRedo = false,
                            find = FindReplaceState()
                        )
                    }
                } else {
                    val fresh = ActiveDocument()
                    undoHistory.reset("")
                    _uiState.update {
                        it.copy(
                            document = fresh,
                            documentVersion = it.documentVersion + 1,
                            drafts = emptyList(),
                            canUndo = false,
                            canRedo = false,
                            find = FindReplaceState()
                        )
                    }
                }
            } else {
                _uiState.update { it.copy(drafts = nextWorkspace.drafts) }
            }
        }
    }

    fun onRemoveRecent(recent: RecentFile) {
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                container.recentDocumentsStore.remove(recent.uriString)
            }
            _uiState.update { it.copy(recentFiles = updated) }
        }
    }

    fun onOpenUri(uri: Uri) {
        val current = _uiState.value.document
        if (current.isDirty) {
            persistDraftImmediately(current)
        }

        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val gateDecision = withContext(Dispatchers.IO) {
                runCatching {
                    val knownBytes = runCatching {
                        DocumentMetadataReader(contentResolver).readMetadata(uri).sizeBytes
                    }.getOrNull()
                    LargeTextOpenGate.classify(knownBytes) {
                        contentResolver.openInputStream(uri)
                            ?: error("Storage provider could not open this file.")
                    }
                }
            }

            if (gateDecision.isFailure) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        snackbarMessage = gateDecision.exceptionOrNull()?.message ?: "Could not open file."
                    )
                }
                return@launch
            }

            if (gateDecision.getOrThrow() == LargeTextOpenGate.Decision.READ_ONLY_PAGES) {
                _uiState.update { it.copy(isBusy = false) }
                _effects.send(BlakeSideEffect.OpenLargeReader(uri.toString()))
                return@launch
            }

            val result = container.documentRepository.read(uri)
            result.onSuccess { (metadata, content) ->
                val now = System.currentTimeMillis()
                val existingDraft = _uiState.value.drafts.firstOrNull { it.uriString == uri.toString() }
                val docId = existingDraft?.id ?: UUID.randomUUID().toString()
                val opened = ActiveDocument(
                    id = docId,
                    title = metadata.displayName,
                    uriString = uri.toString(),
                    mimeType = metadata.mimeType,
                    content = content,
                    isDirty = false,
                    hasVerifiedSnapshot = true,
                    updatedAtMs = now
                )
                undoHistory.reset(content)
                val updatedRecents = withContext(Dispatchers.IO) {
                    container.recentDocumentsStore.record(
                        RecentFile(
                            uriString = uri.toString(),
                            title = metadata.displayName,
                            mimeType = metadata.mimeType,
                            openedAtMs = now
                        )
                    )
                }
                val updatedWorkspace = withContext(Dispatchers.IO) {
                    container.draftRecoveryStore.upsert(opened.toScratchDraft(), setActive = true)
                }
                _uiState.update {
                    it.copy(
                        document = opened,
                        documentVersion = it.documentVersion + 1,
                        pendingSelection = 0 to 0,
                        isBusy = false,
                        isReadMode = false,
                        canUndo = false,
                        canRedo = false,
                        find = FindReplaceState(),
                        drafts = updatedWorkspace.drafts,
                        recentFiles = updatedRecents
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        snackbarMessage = err.message ?: "Unable to read this document."
                    )
                }
            }
        }
    }

    fun onSaveClicked() {
        val doc = _uiState.value.document
        if (doc.canOverwriteDirectly) {
            val uri = Uri.parse(doc.uriString)
            saveExisting(uri, doc)
        } else {
            onSaveAsClicked()
        }
    }

    fun onSaveAsClicked() {
        val doc = _uiState.value.document
        viewModelScope.launch {
            _effects.send(
                BlakeSideEffect.LaunchSaveAs(
                    suggestedName = doc.title,
                    mimeType = doc.mimeType.ifBlank { "text/markdown" }
                )
            )
        }
    }

    fun onSaveAsDestinationPicked(targetUri: Uri) {
        val doc = _uiState.value.document
        val sourceUri = doc.uriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val writeResult = container.documentRepository.writeNewDocument(targetUri, doc.content, sourceUri)
            writeResult.onSuccess {
                val metadata = container.documentRepository.getMetadata(targetUri).getOrNull()
                val now = System.currentTimeMillis()
                val savedTitle = metadata?.displayName ?: doc.title
                val savedMime = metadata?.mimeType ?: doc.mimeType
                val savedDoc = doc.copy(
                    title = savedTitle,
                    uriString = targetUri.toString(),
                    mimeType = savedMime,
                    isDirty = false,
                    hasVerifiedSnapshot = true,
                    updatedAtMs = now
                )
                val recents = withContext(Dispatchers.IO) {
                    container.recentDocumentsStore.record(
                        RecentFile(targetUri.toString(), savedTitle, savedMime, now)
                    )
                }
                val workspace = withContext(Dispatchers.IO) {
                    container.draftRecoveryStore.upsert(savedDoc.toScratchDraft(), setActive = true)
                }
                _uiState.update {
                    it.copy(
                        document = savedDoc,
                        isBusy = false,
                        drafts = workspace.drafts,
                        recentFiles = recents,
                        snackbarMessage = "Saved $savedTitle"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        snackbarMessage = err.message ?: "Could not save file."
                    )
                }
            }
        }
    }

    private fun saveExisting(uri: Uri, doc: ActiveDocument) {
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = container.documentRepository.write(uri, doc.content)
            result.onSuccess {
                val saved = doc.copy(isDirty = false, updatedAtMs = System.currentTimeMillis())
                val workspace = withContext(Dispatchers.IO) {
                    container.draftRecoveryStore.upsert(saved.toScratchDraft(), setActive = true)
                }
                _uiState.update {
                    it.copy(
                        document = saved,
                        isBusy = false,
                        drafts = workspace.drafts,
                        snackbarMessage = "Saved"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        snackbarMessage = err.message ?: "Save failed. Use Save As to keep a copy."
                    )
                }
            }
        }
    }

    fun flushDraftOnPause() {
        recoveryDebounceJob?.cancel()
        val doc = _uiState.value.document
        if (doc.content.isNotBlank() || doc.isDirty || doc.isExternal) {
            persistDraftImmediately(doc)
        }
    }

    private fun scheduleDraftSave(doc: ActiveDocument) {
        recoveryDebounceJob?.cancel()
        recoveryDebounceJob = viewModelScope.launch {
            delay(400L)
            persistDraftInternal(doc)
        }
    }

    private fun persistDraftImmediately(doc: ActiveDocument) {
        recoveryDebounceJob?.cancel()
        viewModelScope.launch {
            persistDraftInternal(doc)
        }
    }

    private suspend fun persistDraftInternal(doc: ActiveDocument) {
        val updated = withContext(Dispatchers.IO) {
            runCatching {
                container.draftRecoveryStore.upsert(doc.toScratchDraft(), setActive = true)
            }.getOrNull()
        }
        if (updated != null) {
            _uiState.update { it.copy(drafts = updated.drafts) }
        }
    }

    private fun showMessage(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    private fun ActiveDocument.toScratchDraft() = ScratchDraft(
        id = id,
        title = title,
        uriString = uriString,
        mimeType = mimeType,
        content = content,
        isDirty = isDirty,
        updatedAtMs = updatedAtMs
    )

    private fun nextUntitledName(existing: Set<String>): String {
        if ("Untitled.md" !in existing) return "Untitled.md"
        var index = 2
        while ("Untitled $index.md" in existing) {
            index++
        }
        return "Untitled $index.md"
    }

    class Factory(
        private val container: AppContainer,
        private val contentResolver: ContentResolver
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BlakeViewModel(container, contentResolver) as T
    }
}
