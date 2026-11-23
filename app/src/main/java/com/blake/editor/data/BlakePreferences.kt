package com.blake.editor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.blake.editor.model.AppThemeMode
import com.blake.editor.model.EditorFontSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.blakePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "blake_v1_preferences"
)

data class BlakeUserSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val fontSize: EditorFontSize = EditorFontSize.MEDIUM,
    val showMarkdownBar: Boolean = true
)

class BlakePreferences(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val SHOW_MARKDOWN_BAR = booleanPreferencesKey("show_markdown_bar")
    }

    val settingsFlow: Flow<BlakeUserSettings> = context.blakePreferencesDataStore.data.map { prefs ->
        val theme = prefs[Keys.THEME_MODE]
            ?.let { raw -> AppThemeMode.entries.firstOrNull { it.name == raw } }
            ?: AppThemeMode.SYSTEM
        val fontSize = prefs[Keys.FONT_SIZE]
            ?.let { raw -> EditorFontSize.entries.firstOrNull { it.name == raw } }
            ?: EditorFontSize.MEDIUM
        val showBar = prefs[Keys.SHOW_MARKDOWN_BAR] ?: true
        BlakeUserSettings(
            themeMode = theme,
            fontSize = fontSize,
            showMarkdownBar = showBar
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.blakePreferencesDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setFontSize(size: EditorFontSize) {
        context.blakePreferencesDataStore.edit { it[Keys.FONT_SIZE] = size.name }
    }

    suspend fun setShowMarkdownBar(visible: Boolean) {
        context.blakePreferencesDataStore.edit { it[Keys.SHOW_MARKDOWN_BAR] = visible }
    }
}
