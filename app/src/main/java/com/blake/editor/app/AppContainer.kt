package com.blake.editor.app

import android.content.Context
import com.blake.editor.data.BlakePreferences
import com.blake.editor.data.DraftRecoveryStore
import com.blake.editor.data.RecentDocumentsStore
import com.blake.editor.file.AndroidDocumentRepository
import com.blake.editor.file.DocumentMetadataReader
import com.blake.editor.file.DocumentRepository
import com.blake.editor.file.UriPermissionManager
import com.blake.editor.intent.IncomingIntentParser
import java.io.File

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val blakeV1Dir = File(appContext.filesDir, "blake_v1")

    val uriPermissionManager = UriPermissionManager(appContext.contentResolver)
    val metadataReader = DocumentMetadataReader(appContext.contentResolver)
    val incomingIntentParser = IncomingIntentParser()

    val documentRepository: DocumentRepository = AndroidDocumentRepository(
        contentResolver = appContext.contentResolver,
        metadataReader = metadataReader,
        uriPermissionManager = uriPermissionManager,
        backupDirectory = File(blakeV1Dir, "save_backups")
    )

    val draftRecoveryStore = DraftRecoveryStore(File(blakeV1Dir, "drafts.bin"))
    val recentDocumentsStore = RecentDocumentsStore(File(blakeV1Dir, "recents.bin"))
    val preferences = BlakePreferences(appContext)
}
