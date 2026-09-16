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

    init {
        viewModelScope.launch {
            settingsRepository.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
    }

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
        viewModelScope.launch {
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
                        .onFailure {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isPreviewing = false,
                                    statusMessage = "Blake Editor   Phase 8 Ready"
                                )
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Reading file...") }
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
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Error opening file: ${error.message}"
                        )
                    }
                }
        }
    }

    fun saveDocument(onComplete: ((Boolean) -> Unit)? = null) {
        val uri = _uiState.value.currentUri
        if (uri == null) {
            _uiState.update { it.copy(statusMessage = "No file open. Use Save As.") }
            onComplete?.invoke(false)
            return
        }

        val contentToSave = _uiState.value.content
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Saving file...") }
            documentRepository.write(uri, contentToSave)
                .onSuccess {
                    draftRepository.clearDraft(uri)
                    _uiState.update {
                        it.copy(
                            isDirty = false,
                            isLoading = false,
                            statusMessage = "Saved: ${it.fileName}"
                        )
                    }
                    updateSession()
                    onComplete?.invoke(true)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Error saving file: ${error.message}"
                        )
                    }
                    onComplete?.invoke(false)
                }
        }
    }

    fun saveAsDocument(newUri: Uri) {
        val oldUri = _uiState.value.currentUri
        val contentToSave = _uiState.value.content

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Saving to new file...") }
            documentRepository.write(newUri, contentToSave)
                .onSuccess {
                    draftRepository.clearDraft(oldUri)
                    val metadataResult = documentRepository.getMetadata(newUri)
                    val name = metadataResult.getOrNull()?.displayName ?: "saved_file.txt"
                    val syntax = SyntaxResolver.resolve(name, metadataResult.getOrNull()?.mimeType)
                    _uiState.update {
                        it.copy(
                            currentUri = newUri,
                            fileName = name,
                            syntaxMode = syntax,
                            isDirty = false,
                            isLoading = false,
                            statusMessage = "Saved As: $name"
                        )
                    }
                    updateSession()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Error in Save As: ${error.message}"
                        )
                    }
                }
        }
    }

    fun newDocument() {
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
        val state = _uiState.value
        if (!state.isDirty) return

        val session = EditorSession(
            uri = state.currentUri,
            displayName = state.fileName,
            syntaxMode = state.syntaxMode,
            isDirty = true,
            cursorLine = state.cursorLine,
            cursorColumn = state.cursorColumn,
            scrollPosition = state.scrollPosition
        )
        draftRepository.saveDraft(session, state.content)
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
