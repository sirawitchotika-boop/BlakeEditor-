package com.blake.editor.core.model

enum class ThemeMode(val displayName: String) {
    DARK("Dark"),
    AMOLED("AMOLED"),
    EYE_CARE("Eye Care");

    companion object {
        fun fromName(name: String?): ThemeMode {
            if (name == null) return DARK
            return values().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DARK
        }
    }
}
