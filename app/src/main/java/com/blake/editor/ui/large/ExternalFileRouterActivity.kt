package com.blake.editor.ui.large

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blake.editor.file.DocumentMetadataReader
import com.blake.editor.file.LargeTextOpenGate
import com.blake.editor.intent.IncomingIntentParser
import com.blake.editor.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight external ACTION_VIEW/ACTION_EDIT entry point.
 *
 * The original MainActivity loads an editor and a WebView before it can
 * classify external files. Route external files before creating either.
 * No document bytes enter an editable view until the bound check passes.
 */
class ExternalFileRouterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = IncomingIntentParser.parse(intent) ?: run {
            finish()
            return
        }
        val uri = request.uri
        setContentView(TextView(this).apply {
            text = "Checking file size..."
            textSize = 17f
            setPadding(24, 48, 24, 24)
        })
        lifecycleScope.launch {
            val decision = withContext(Dispatchers.IO) {
                runCatching {
                    val knownBytes = runCatching {
                        DocumentMetadataReader(contentResolver)
                            .readMetadata(uri).sizeBytes
                    }.getOrNull()
                    LargeTextOpenGate.classify(knownBytes) {
                        contentResolver.openInputStream(uri)
                            ?: error("Storage provider could not open this document")
                    }
                }
            }
            decision.onSuccess { result ->
                when (result) {
                    LargeTextOpenGate.Decision.READ_ONLY_PAGES ->
                        showReadOnlyChoice(uri)
                    LargeTextOpenGate.Decision.EDIT ->
                        forwardSmallDocument(uri, request.action)
                }
            }.onFailure {
                AlertDialog.Builder(this@ExternalFileRouterActivity)
                    .setTitle("Cannot open file")
                    .setMessage(it.message ?: "Storage provider did not allow reading.")
                    .setPositiveButton("OK") { _, _ -> finish() }
                    .setOnCancelListener { finish() }
                    .show()
            }
        }
    }

    private fun showReadOnlyChoice(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Large file — read only")
            .setMessage("This file is larger than 2 MB. Read it in small pages " +
                "with Previous and Next? The original file stays unchanged.")
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setPositiveButton("Open Reader") { _, _ ->
                startActivity(Intent(this, LargeTextReaderActivity::class.java).apply {
                    data = uri
                    forwardReadAccess(uri, this)
                })
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun forwardSmallDocument(uri: Uri, action: String?) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            this.action = action ?: Intent.ACTION_VIEW
            data = uri
            // Reuse a visible editor instead of creating a duplicate screen.
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            forwardReadAccess(uri, this)
        })
        finish()
    }

    private fun forwardReadAccess(uri: Uri, target: Intent) {
        val incomingAccess = intent.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        target.addFlags(incomingAccess or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (uri.scheme == "content") {
            target.clipData = ClipData.newUri(
                contentResolver, "Blake text document", uri
            )
        }
    }
}
