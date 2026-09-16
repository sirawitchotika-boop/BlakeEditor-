package com.blake.editor.app

import android.content.Context
import com.blake.editor.file.AndroidDocumentRepository
import com.blake.editor.file.DocumentMetadataReader
import com.blake.editor.file.DocumentRepository
import com.blake.editor.file.UriPermissionManager
import com.blake.editor.session.DraftRepository
import com.blake.editor.session.FileDraftRepository
import com.blake.editor.session.SessionStore
import com.blake.editor.settings.DataStoreSettingsRepository
import com.blake.editor.settings.SettingsRepository

class AppContainer(private val context: Context) {
    val documentMetadataReader: DocumentMetadataReader by lazy {
        DocumentMetadataReader(context.contentResolver)
    }

    val uriPermissionManager: UriPermissionManager by lazy {
        UriPermissionManager(context.contentResolver)
    }

    val documentRepository: DocumentRepository by lazy {
        AndroidDocumentRepository(
            contentResolver = context.contentResolver,
            metadataReader = documentMetadataReader,
            uriPermissionManager = uriPermissionManager
        )
    }

    val draftRepository: DraftRepository by lazy {
        FileDraftRepository(context)
    }

    val sessionStore: SessionStore by lazy {
        SessionStore(context)
    }

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(context)
    }
}
