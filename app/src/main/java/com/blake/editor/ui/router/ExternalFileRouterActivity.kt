package com.blake.editor.ui.router

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.blake.editor.BlakeEditorApp
import com.blake.editor.data.BlakeUserSettings
import com.blake.editor.file.DocumentMetadataReader
import com.blake.editor.file.LargeTextOpenGate
import com.blake.editor.intent.IncomingIntentParser
import com.blake.editor.ui.main.MainActivity
import com.blake.editor.ui.reader.LargeFileReaderActivity
import com.blake.editor.ui.theme.BlakeTheme
import com.blake.editor.ui.theme.LocalBlakePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight external ACTION_VIEW/ACTION_EDIT router.
 * Classifies incoming documents against the 2 MiB edit limit before loading text into the editor.
 */
class ExternalFileRouterActivity : ComponentActivity() {

    private var largeFilePromptUri by mutableStateOf<Uri?>(null)
    private var errorText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val request = IncomingIntentParser.parse(intent) ?: run {
            finish()
            return
        }
        val uri = request.uri
        val appContainer = (application as BlakeEditorApp).appContainer

        setContent {
            val settings by appContainer.preferences.settingsFlow
                .collectAsState(initial = BlakeUserSettings())

            BlakeTheme(themeMode = settings.themeMode) {
                val palette = LocalBlakePalette.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.canvas),
                    contentAlignment = Alignment.Center
                ) {
                    if (largeFilePromptUri == null && errorText == null) {
                        CircularProgressIndicator(color = palette.accent)
                    }

                    largeFilePromptUri?.let { targetUri ->
                        AlertDialog(
                            onDismissRequest = { finish() },
                            containerColor = palette.surface,
                            title = { Text("Large file — Read only", color = palette.ink) },
                            text = {
                                Text(
                                    "This file is larger than 2 MB. Open it in Blake's paged reader? The original file stays untouched.",
                                    color = palette.inkSecondary
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    openLargeReader(targetUri)
                                }) {
                                    Text("Open Reader", color = palette.accent)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { finish() }) {
                                    Text("Cancel", color = palette.inkSecondary)
                                }
                            }
                        )
                    }

                    errorText?.let { msg ->
                        AlertDialog(
                            onDismissRequest = { finish() },
                            containerColor = palette.surface,
                            title = { Text("Cannot open file", color = palette.ink) },
                            text = { Text(msg, color = palette.inkSecondary) },
                            confirmButton = {
                                TextButton(onClick = { finish() }) {
                                    Text("OK", color = palette.accent)
                                }
                            }
                        )
                    }
                }
            }
        }

        lifecycleScope.launch {
            val decision = withContext(Dispatchers.IO) {
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

            decision.onSuccess { result ->
                when (result) {
                    LargeTextOpenGate.Decision.READ_ONLY_PAGES -> largeFilePromptUri = uri
                    LargeTextOpenGate.Decision.EDIT -> forwardToEditor(uri, request.action)
                }
            }.onFailure { err ->
                errorText = err.message ?: "Storage provider did not allow reading this file."
            }
        }
    }

    private fun openLargeReader(uri: Uri) {
        startActivity(Intent(this, LargeFileReaderActivity::class.java).apply {
            data = uri
            forwardAccess(uri, this)
        })
        finish()
    }

    private fun forwardToEditor(uri: Uri, action: String?) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            this.action = action ?: Intent.ACTION_VIEW
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            forwardAccess(uri, this)
        })
        finish()
    }

    private fun forwardAccess(uri: Uri, target: Intent) {
        val incomingAccess = intent.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        target.addFlags(incomingAccess or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (uri.scheme == "content") {
            target.clipData = ClipData.newUri(contentResolver, "Blake document", uri)
        }
    }
}
