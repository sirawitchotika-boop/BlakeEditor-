package com.blake.editor.ui.reader

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blake.editor.BlakeEditorApp
import com.blake.editor.data.BlakeUserSettings
import com.blake.editor.file.DocumentMetadataReader
import com.blake.editor.file.PagedTextReader
import com.blake.editor.file.ReaderPageHistory
import com.blake.editor.file.TextFileEncoding
import com.blake.editor.file.TextPage
import com.blake.editor.ui.theme.BlakeTheme
import com.blake.editor.ui.theme.LocalBlakePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Calm, zero-WebView Editorial read-only paged reader for files larger than 2 MiB.
 * Reads bounded chunks on demand so huge files never freeze or exhaust memory.
 */
class LargeFileReaderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val uri: Uri? = intent?.data
        if (uri == null) {
            finish()
            return
        }

        val appContainer = (application as BlakeEditorApp).appContainer

        setContent {
            val settings by appContainer.preferences.settingsFlow
                .collectAsState(initial = BlakeUserSettings())

            BlakeTheme(themeMode = settings.themeMode) {
                LargeFileReaderScreen(
                    uri = uri,
                    fontSizeSp = settings.fontSize.readerSp,
                    onBack = { finish() }
                )
            }
        }
    }

    @Composable
    private fun LargeFileReaderScreen(
        uri: Uri,
        fontSizeSp: Float,
        onBack: () -> Unit
    ) {
        val palette = LocalBlakePalette.current
        val scope = rememberCoroutineScope()
        val scrollState = rememberScrollState()

        var title by remember { mutableStateOf("Large File") }
        var encoding by remember { mutableStateOf(TextFileEncoding.UTF8) }
        val history = remember { ReaderPageHistory() }
        var pageNumber by remember { mutableStateOf(1) }
        var canGoBack by remember { mutableStateOf(false) }
        var page by remember { mutableStateOf<TextPage?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var encodingMenuOpen by remember { mutableStateOf(false) }

        fun loadPage(
            startChar: Long,
            targetEncoding: TextFileEncoding,
            onSuccessHistoryUpdate: (() -> Unit)? = null
        ) {
            isLoading = true
            errorMessage = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val stream = contentResolver.openInputStream(uri)
                            ?: error("Unable to open file stream")
                        PagedTextReader.readPage(stream, targetEncoding, startChar)
                    }
                }
                isLoading = false
                result.onSuccess { loaded ->
                    onSuccessHistoryUpdate?.invoke()
                    pageNumber = history.pageNumber
                    canGoBack = history.canGoBack
                    page = loaded
                    scrollState.scrollTo(0)
                }.onFailure { err ->
                    errorMessage = err.message ?: "Could not decode this page with ${targetEncoding.charsetName}."
                }
            }
        }

        LaunchedEffect(uri) {
            val init = withContext(Dispatchers.IO) {
                val metaName = runCatching {
                    DocumentMetadataReader(contentResolver).readMetadata(uri).displayName
                }.getOrDefault("Large File")
                val detected = runCatching {
                    contentResolver.openInputStream(uri)?.let { PagedTextReader.initialEncoding(it) }
                }.getOrNull() ?: TextFileEncoding.UTF8
                metaName to detected
            }
            title = init.first
            encoding = init.second
            history.reset()
            loadPage(0L, init.second)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.canvas)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = palette.ink
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Read-only · ${encoding.charsetName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.inkSecondary
                    )
                }

                Box {
                    IconButton(onClick = { encodingMenuOpen = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = "Change encoding",
                            tint = palette.ink
                        )
                    }
                    DropdownMenu(
                        expanded = encodingMenuOpen,
                        onDismissRequest = { encodingMenuOpen = false },
                        containerColor = palette.surface
                    ) {
                        for (candidate in TextFileEncoding.entries) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = candidate.charsetName,
                                        color = if (candidate == encoding) palette.accent else palette.ink,
                                        fontWeight = if (candidate == encoding) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    encodingMenuOpen = false
                                    encoding = candidate
                                    history.reset()
                                    loadPage(0L, candidate)
                                }
                            )
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = palette.hairline)

            // Body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            color = palette.accent,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    errorMessage != null -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.danger
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Try choosing another encoding from the top-right menu.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.inkSecondary
                            )
                        }
                    }
                    else -> {
                        SelectionContainer(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = page?.content.orEmpty(),
                                style = TextStyle(
                                    color = palette.ink,
                                    fontSize = fontSizeSp.sp,
                                    lineHeight = (fontSizeSp * 1.5f).sp
                                ),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(horizontal = 20.dp, vertical = 18.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Paging Controls
            HorizontalDivider(thickness = 1.dp, color = palette.hairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val canPrev = !isLoading && canGoBack
                val canNext = !isLoading && (page?.hasMore == true)

                IconButton(
                    enabled = canPrev,
                    onClick = {
                        val prevOffset = history.previousStart() ?: return@IconButton
                        loadPage(prevOffset, encoding) {
                            history.recordPrevious()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.NavigateBefore,
                        contentDescription = "Previous page",
                        tint = if (canPrev) palette.ink else palette.inkMuted
                    )
                }

                Text(
                    text = "Page $pageNumber",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.inkSecondary
                )

                IconButton(
                    enabled = canNext,
                    onClick = {
                        val currentPage = page ?: return@IconButton
                        loadPage(currentPage.nextChar, encoding) {
                            history.recordNext(currentPage.startChar)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                        contentDescription = "Next page",
                        tint = if (canNext) palette.ink else palette.inkMuted
                    )
                }
            }
        }
    }
}
