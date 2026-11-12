package com.blake.editor.core.model

enum class ThemeMode(val displayName: String) {
    DARK("Dark"),
    AMOLED("AMOLED"),
    EYE_CARE("Eye Care"),
    LIGHT("Light"),
    FOLLOW_SYSTEM("Follow System");

    fun resolvedForSystem(systemDark: Boolean): ThemeMode =
        if (this == FOLLOW_SYSTEM) {
            if (systemDark) DARK else LIGHT
        } else this

    companion object {
        fun fromName(name: String?): ThemeMode {
            if (name == null) return DARK
            return values().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DARK
        }
    }
}
