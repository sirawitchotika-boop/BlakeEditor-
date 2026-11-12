package com.blake.editor.settings

import android.content.Context
import com.blake.editor.core.model.ThemeMode

/**
 * App-wide theme choice. Independent of document, draft and other settings.
 * Existing Original DataStore and V2 preferences are preserved for upgrades.
 */
object SharedEditorTheme {
    private const val FILE = "blake_shared_editor_theme"
    private const val KEY = "selected_theme"

    fun fromStored(raw: String?): ThemeMode? =
        ThemeMode.values().firstOrNull { it.name == raw }

    fun read(context: Context): ThemeMode? =
        fromStored(context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, null))

    fun remember(context: Context, theme: ThemeMode) {
        context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY, theme.name).apply()
    }
}
