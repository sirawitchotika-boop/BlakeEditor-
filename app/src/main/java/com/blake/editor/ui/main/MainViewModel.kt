package com.blake.editor.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blake.editor.core.model.EditorSession
import com.blake.editor.core.model.SyntaxMode
import com.blake.editor.core.model.ThemeMode
import com.blake.editor.editor.SyntaxResolver
import com.blake.editor.file.DocumentRepository
import com.blake.editor.file.LargeTextOpenGate
import com.blake.editor.file.OversizedEditableFileException
import com.blake.editor.session.DraftRepository
import com.blake.editor.session.FileDraftRepository
import com.blake.editor.session.SessionStore
import com.blake.editor.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class MainViewModel(
    private val documentRepository: DocumentRepository,
    private val draftRepository: DraftRepository,
    private val sessionStore: SessionStore,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var draftSaveJob: Job? = null
    private var sessionSaveJob: Job? = null
    private var activeDocumentOpen: Job? = null
    private var startupRestoreJob: Job? = null
    private val startupRestoreGate = OriginalStartupRestoreGate()
    private val draftMutationMutex = Mutex()
    private val documentSaveGate = SerializedDocumentSaveGate()
    private var editRevision: Long = 0L

    /** Consume the one-shot event before showing the reader prompt. */
    fun consumeLargeFileRequest() {
        if (_uiState.value.pendingLargeReaderUri != null) {
            _uiState.update { it.copy(pendingLargeReaderUri = null) }
        }
    }

    private fun requestLargeReader(uri: Uri) {
        _uiState.update {
            it.copy(isLoading = false, isPreviewing = false,
                pendingLargeReaderUri = uri,
                statusMessage = "Large file detected · Read-only paging available")
        }
    }

    init {
        viewModelScope.launch {
            settingsRepository.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
    }

    /** Claim startup once per ViewModel, not once per Activity instance. */
    fun claimInitialActivityAction(hasIncomingDocument: Boolean): OriginalStartupAction =
        startupRestoreGate.claimInitialAction(hasIncomingDocument)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun togglePreview() {
        val nextState = !_uiState.value.isPreviewing
        _uiState.update { it.copy(isPreviewing = nextState) }
    }

    fun setPreviewing(isPreviewing: Boolean) {
        _uiState.update { it.copy(isPreviewing = isPreviewing) }
    }

    fun onContentChanged(newContent: String) {
        val previousContent = _uiState.value.content
        if (previousContent == newContent) return
        // Never replace fresh typing with a late session restore.
        startupRestoreJob?.cancel()
        editRevision += 1

        _uiState.update { current ->
            current.copy(content = newContent, isDirty = true)
        }

        // Debounce auto-save draft to internal storage
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(FileDraftRepository.DEFAULT_DEBOUNCE_MILLIS)
            saveDraftInternal()
        }

        updateSession()
    }

    fun onCursorPositionChanged(line: Int, column: Int) {
        _uiState.update {
            it.copy(cursorLine = line, cursorColumn = column)
        }
        updateSession()
    }

    fun onScrollPositionChanged(scrollY: Int) {
        _uiState.update {
            it.copy(scrollPosition = scrollY)
        }
        updateSession()
    }

    fun restoreSessionOrDraft() {
        startupRestoreJob?.cancel()
        startupRestoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Checking for saved session...") }
            val sessionResult = sessionStore.loadSession()
            val session = sessionResult.getOrNull()

            if (session != null) {
                // Check if draft exists for this session's URI
                val draftResult = draftRepository.loadDraft(session.uri).getOrNull()
                if (draftResult != null) {
                    val (draftSession, draftContent) = draftResult
                    _uiState.update {
                        it.copy(
                            currentUri = draftSession.uri,
                            fileName = draftSession.displayName,
                            content = draftContent,
                            documentGeneration = it.documentGeneration + 1,
                            syntaxMode = draftSession.syntaxMode,
                            isDirty = true,
                            isLoading = false,
                            isPreviewing = false,
                            cursorLine = draftSession.cursorLine,
                            cursorColumn = draftSession.cursorColumn,
                            scrollPosition = draftSession.scrollPosition,
                            statusMessage = "Recovered unsaved draft: ${draftSession.displayName}"
                        )
                    }
                    return@launch
                }

                // If no draft exists, but we have a session with a URI, read from document repository
                if (session.uri != null) {
                    documentRepository.read(session.uri)
                        .onSuccess { (metadata, fileContent) ->
                            val syntax = SyntaxResolver.resolve(metadata.displayName, metadata.mimeType)
                            _uiState.update {
                                it.copy(
                                    currentUri = session.uri,
                                    fileName = metadata.displayName,
                                    content = fileContent,
                                    documentGeneration = it.documentGeneration + 1,
                                    syntaxMode = syntax,
                                    isDirty = false,
                                    isLoading = false,
                                    isPreviewing = false,
                                    cursorLine = session.cursorLine,
                                    cursorColumn = session.cursorColumn,
                                    scrollPosition = session.scrollPosition,
                                    statusMessage = "Restored session: ${metadata.displayName}"
                                )
                            }
                        }
                        .onFailure { error ->
                            if (error is OversizedEditableFileException) {
                                requestLargeReader(session.uri)
                            } else {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isPreviewing = false,
                                        statusMessage = "Blake Editor   Phase 8 Ready"
                                    )
                                }
                            }
                        }
                    return@launch
                }
            }

            // Fallback: check if an untitled draft exists
            val untitledDraft = draftRepository.loadDraft(null).getOrNull()
            if (untitledDraft != null) {
                val (draftSession, draftContent) = untitledDraft
                _uiState.update {
                    it.copy(
                        currentUri = null,
                        fileName = draftSession.displayName,
                        content = draftContent,
                        documentGeneration = it.documentGeneration + 1,
                        syntaxMode = draftSession.syntaxMode,
                        isDirty = true,
                        isLoading = false,
                        isPreviewing = false,
                        cursorLine = draftSession.cursorLine,
                        cursorColumn = draftSession.cursorColumn,
                        scrollPosition = draftSession.scrollPosition,
                        statusMessage = "Recovered unsaved untitled draft"
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isPreviewing = false,
                    statusMessage = "Blake Editor   Phase 8 Ready"
                )
            }
        }
    }

    fun openDocument(uri: Uri) {
        startupRestoreJob?.cancel()
        activeDocumentOpen?.cancel()
        activeDocumentOpen = viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, pendingLargeReaderUri = null,
                    statusMessage = "Checking file size before editing...")
            }
            // Independent of MainActivity's entry-point gate. A restored
            // session, direct ViewModel call or a provider with unusual
            // permissions must never bypass the native input safety limit.
            val metadata = documentRepository.getMetadata(uri).getOrNull()
            if (metadata != null && metadata.sizeBytes > LargeTextOpenGate.MAX_EDIT_BYTES) {
                requestLargeReader(uri)
                return@launch
            }
            documentRepository.read(uri)
                .onSuccess { (metadata, fileContent) ->
                    val syntax = SyntaxResolver.resolve(metadata.displayName, metadata.mimeType)
                    val draftResult = draftRepository.loadDraft(uri).getOrNull()
                    if (draftResult != null) {
                        val (draftSession, draftContent) = draftResult
                        _uiState.update {
                            it.copy(
                                currentUri = uri,
                                fileName = draftSession.displayName,
                                content = draftContent,
                                documentGeneration = it.documentGeneration + 1,
                                syntaxMode = draftSession.syntaxMode,
                                isDirty = true,
                                isLoading = false,
                                isPreviewing = false,
                                cursorLine = draftSession.cursorLine,
                                cursorColumn = draftSession.cursorColumn,
                                scrollPosition = draftSession.scrollPosition,
                                statusMessage = "Opened with unsaved draft: ${draftSession.displayName}"
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                currentUri = uri,
                                fileName = metadata.displayName,
                                content = fileContent,
                                documentGeneration = it.documentGeneration + 1,
                                syntaxMode = syntax,
                                isDirty = false,
                                isLoading = false,
                                isPreviewing = false,
                                cursorLine = 1,
                                cursorColumn = 1,
                                scrollPosition = 0,
                                statusMessage = "Opened: ${metadata.displayName} (${metadata.sizeBytes} bytes)"
                            )
                        }
                    }
                    updateSession()
                }
                .onFailure { error ->
                    if (error is OversizedEditableFileException) {
                        requestLargeReader(uri)
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                statusMessage = "Error opening file: ${error.message}"
                            )
                        }
                    }
                }
        }
    }


    private enum class SaveFinish { CURRENT, STALE, RECOVERY_FAILED }

    private fun currentSaveSnapshot(): SaveStateSnapshot {
        val state = _uiState.value
        return SaveStateSnapshot(state.currentUri?.toString(), state.documentGeneration, editRevision)
    }

    private fun sameSnapshot(snapshot: SaveStateSnapshot): Boolean =
        SaveCompletionPolicy.decide(snapshot, currentSaveSnapshot(), writeSucceeded = true) ==
            SaveCompletionDecision.CURRENT

    /**
     * Serialize recovery writes and cleanup. If editing continues while clearDraft
     * is suspended, recreate the latest dirty draft before releasing the lock.
     */
    private suspend fun completeSuccessfulWrite(
        snapshot: SaveStateSnapshot,
        draftUri: Uri?,
        cleanState: (MainUiState) -> MainUiState
    ): SaveFinish {
        draftSaveJob?.cancel()
        draftMutationMutex.lock()
        try {
            if (!sameSnapshot(snapshot)) {
                return if (persistDirtyDraftLocked()) SaveFinish.STALE else SaveFinish.RECOVERY_FAILED
            }

            val cleared = draftRepository.clearDraft(draftUri)
            if (!sameSnapshot(snapshot)) {
                return if (persistDirtyDraftLocked()) SaveFinish.STALE else SaveFinish.RECOVERY_FAILED
            }

            if (cleared.isFailure) return SaveFinish.RECOVERY_FAILED
            _uiState.update(cleanState)
            return SaveFinish.CURRENT
        } finally {
            draftMutationMutex.unlock()
        }
    }

    private suspend fun persistDirtyDraftLocked(): Boolean {
        // Repeat a bounded number of times if edits arrive during a draft write.
        repeat(3) {
            val state = _uiState.value
            if (!state.isDirty) return true
            val stamp = currentSaveSnapshot()
            val session = EditorSession(
                uri = state.currentUri,
                displayName = state.fileName,
                syntaxMode = state.syntaxMode,
                isDirty = true,
                cursorLine = state.cursorLine,
                cursorColumn = state.cursorColumn,
                scrollPosition = state.scrollPosition
            )
            if (draftRepository.saveDraft(session, state.content).isFailure) return false
            if (sameSnapshot(stamp)) return true
        }
        // Continuous editing still schedules its own debounced draft save.
        return false
    }

    private fun reportUnfinishedSave(snapshot: SaveStateSnapshot, finish: SaveFinish) {
        _uiState.update { current ->
            if (current.documentGeneration != snapshot.documentGeneration ||
                current.currentUri?.toString() != snapshot.uriKey) {
                current // Do not overwrite another document's status.
            } else {
                val message = when (finish) {
                    SaveFinish.STALE -> "Older version saved; newer edits remain unsaved"
                    SaveFinish.RECOVERY_FAILED -> "File saved; recovery draft needs attention"
                    SaveFinish.CURRENT -> current.statusMessage
                }
                current.copy(isLoading = false, statusMessage = message)
            }
        }
        updateSession()
    }

    private fun reportFailedWrite(snapshot: SaveStateSnapshot, message: String) {
        // Newer edits in the *same* document still need the loading indicator reset.
        _uiState.update { current ->
            if (current.documentGeneration == snapshot.documentGeneration &&
                current.currentUri?.toString() == snapshot.uriKey) {
                current.copy(isLoading = false, statusMessage = message)
            } else {
                current // Never disturb a different document that replaced this one.
            }
        }
    }

    fun saveDocument(onComplete: ((Boolean) -> Unit)? = null) {
        val state = _uiState.value
        val uri = state.currentUri
        if (uri == null) {
            _uiState.update { it.copy(statusMessage = "No file open. Use Save As.") }
            onComplete?.invoke(false)
            return
        }
        val snapshot = currentSaveSnapshot()
        val contentToSave = state.content

        viewModelScope.launch {
            documentSaveGate.run {
                // Never write an obsolete Save queued behind a newer operation.
                if (!sameSnapshot(snapshot)) {
                    reportUnfinishedSave(snapshot, SaveFinish.STALE)
                    onComplete?.invoke(false)
                    return@run
                }

                _uiState.update { it.copy(isLoading = true, statusMessage = "Saving file...") }
                documentRepository.write(uri, contentToSave)
                    .onSuccess {
                        val finish = completeSuccessfulWrite(snapshot, uri) {
                            it.copy(
                                isDirty = false,
                                isLoading = false,
                                statusMessage = "Saved: ${it.fileName}"
                            )
                        }
                        if (finish == SaveFinish.CURRENT) {
                            updateSession()
                            onComplete?.invoke(true)
                        } else {
                            reportUnfinishedSave(snapshot, finish)
                            onComplete?.invoke(false)
                        }
                    }
                    .onFailure { error ->
                        reportFailedWrite(snapshot, "Error saving file: ${error.message}")
                        onComplete?.invoke(false)
                    }
                Unit
            }
        }
    }

    fun saveAsDocument(newUri: Uri) {
        val state = _uiState.value
        val oldUri = state.currentUri
        val snapshot = currentSaveSnapshot()
        val contentToSave = state.content

        viewModelScope.launch {
            documentSaveGate.run {
                // Save As also shares the same ordered write gate with Save.
                if (!sameSnapshot(snapshot)) {
                    reportUnfinishedSave(snapshot, SaveFinish.STALE)
                    return@run
                }

                _uiState.update { it.copy(isLoading = true, statusMessage = "Saving to new file...") }
                documentRepository.writeNewDocument(newUri, contentToSave, oldUri)
                    .onSuccess {
                        val metadata = documentRepository.getMetadata(newUri).getOrNull()
                        val name = metadata?.displayName ?: "saved_file.txt"
                        val syntax = SyntaxResolver.resolve(name, metadata?.mimeType)
                        val finish = completeSuccessfulWrite(snapshot, oldUri) {
                            it.copy(
                                currentUri = newUri,
                                fileName = name,
                                syntaxMode = syntax,
                                isDirty = false,
                                isLoading = false,
                                statusMessage = "Saved As: $name"
                            )
                        }
                        if (finish == SaveFinish.CURRENT) {
                            updateSession()
                        } else {
                            // Keep the existing URI and unsaved recovery when edits arrived.
                            reportUnfinishedSave(snapshot, finish)
                        }
                    }
                    .onFailure { error ->
                        reportFailedWrite(snapshot, "Error in Save As: ${error.message}")
                    }
                Unit
            }
        }
    }

    fun newDocument() {
        startupRestoreJob?.cancel()
        draftSaveJob?.cancel()
        sessionSaveJob?.cancel()
        viewModelScope.launch {
            val oldUri = _uiState.value.currentUri
            draftRepository.clearDraft(oldUri)
            _uiState.update {
                MainUiState(
                    currentUri = null,
                    fileName = "untitled.txt",
                    content = "",
                    documentGeneration = _uiState.value.documentGeneration + 1,
                    syntaxMode = SyntaxMode.PLAIN_TEXT,
                    themeMode = _uiState.value.themeMode,
                    isDirty = false,
                    isLoading = false,
                    isPreviewing = false,
                    statusMessage = "New file ready"
                )
            }
            updateSession()
        }
    }

    fun flushDraft() {
        // Do not replace an existing persisted session with an empty startup state.
        if (startupRestoreJob?.isActive == true) return
        draftSaveJob?.cancel()
        sessionSaveJob?.cancel()
        viewModelScope.launch {
            saveDraftInternal()
            val state = _uiState.value
            val session = EditorSession(
                uri = state.currentUri,
                displayName = state.fileName,
                syntaxMode = state.syntaxMode,
                isDirty = state.isDirty,
                cursorLine = state.cursorLine,
                cursorColumn = state.cursorColumn,
                scrollPosition = state.scrollPosition
            )
            sessionStore.saveSession(session)
        }
    }

    private suspend fun saveDraftInternal() {
        draftMutationMutex.lock()
        try {
            persistDirtyDraftLocked()
        } finally {
            draftMutationMutex.unlock()
        }
    }

    private fun updateSession() {
        sessionSaveJob?.cancel()
        sessionSaveJob = viewModelScope.launch {
            delay(300L)
            val state = _uiState.value
            val session = EditorSession(
                uri = state.currentUri,
                displayName = state.fileName,
                syntaxMode = state.syntaxMode,
                isDirty = state.isDirty,
                cursorLine = state.cursorLine,
                cursorColumn = state.cursorColumn,
                scrollPosition = state.scrollPosition
            )
            sessionStore.saveSession(session)
        }
    }

    companion object {
        fun provideFactory(
            documentRepository: DocumentRepository,
            draftRepository: DraftRepository,
            sessionStore: SessionStore,
            settingsRepository: SettingsRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(
                        documentRepository,
                        draftRepository,
                        sessionStore,
                        settingsRepository
                    ) as T
                }
            }
    }
}
