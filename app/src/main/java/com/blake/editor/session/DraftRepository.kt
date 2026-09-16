package com.blake.editor.session

import android.net.Uri
import com.blake.editor.core.model.EditorSession

interface DraftRepository {
    suspend fun saveDraft(session: EditorSession, content: String): Result<Unit>
    suspend fun loadDraft(uri: Uri?): Result<Pair<EditorSession, String>?>
    suspend fun clearDraft(uri: Uri?): Result<Unit>
    suspend fun hasDraft(uri: Uri?): Boolean
}
