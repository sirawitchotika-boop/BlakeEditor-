package com.blake.editor.ui.preview

import android.content.Context
import android.content.res.Configuration

enum class PreviewTheme(val title: String) {
    FOLLOW_SYSTEM("Follow System"),
    LIGHT("Light"),
    DARK("Dark"),
    AMOLED("AMOLED"),
    EYE_CARE("Eye Care");

    /** Pure policy: the chosen theme remains fixed unless it follows Android. */
    fun resolvedForSystem(systemDark: Boolean): PreviewTheme =
        if (this == FOLLOW_SYSTEM) {
            if (systemDark) DARK else LIGHT
        } else this
}

data class PreviewPalette(
    val background: Int,
    val surface: Int,
    val foreground: Int,
    val secondary: Int,
    val gutter: Int,
    val keyword: Int,
    val string: Int,
    val number: Int,
    val comment: Int,
    val tag: Int,
    val readerTheme: String
)

class PreviewEditorSettings(private val context: Context) {
    // Keep this existing preference key: moving the class must not reset users' settings.
    private val prefs = context.getSharedPreferences(
        "blake_v2_preview_editor_settings", Context.MODE_PRIVATE
    )

    var theme: PreviewTheme
        get() = runCatching {
            PreviewTheme.valueOf(prefs.getString("theme", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM")
        }.getOrDefault(PreviewTheme.FOLLOW_SYSTEM)
        set(value) { prefs.edit().putString("theme", value.name).apply() }

    var fontSp: Int
        get() = prefs.getInt("font_sp", 16).coerceIn(12, 26)
        set(value) { prefs.edit().putInt("font_sp", value.coerceIn(12, 26)).apply() }

    var wrapCode: Boolean
        get() = prefs.getBoolean("wrap_code", false)
        set(value) { prefs.edit().putBoolean("wrap_code", value).apply() }

    var showLineNumbers: Boolean
        get() = prefs.getBoolean("line_numbers", true)
        set(value) { prefs.edit().putBoolean("line_numbers", value).apply() }

    fun palette(): PreviewPalette {
        val systemDark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val mode = theme.resolvedForSystem(systemDark)
        return when (mode) {
            PreviewTheme.LIGHT -> PreviewPalette(
                0xFFF6F7FA.toInt(), 0xFFFFFFFF.toInt(), 0xFF202939.toInt(),
                0xFF64748B.toInt(), 0xFFE9EDF3.toInt(), 0xFF5B21B6.toInt(),
                0xFF047857.toInt(), 0xFFB45309.toInt(), 0xFF64748B.toInt(),
                0xFF0369A1.toInt(), "light"
            )
            PreviewTheme.AMOLED -> PreviewPalette(
                0xFF000000.toInt(), 0xFF080808.toInt(), 0xFFF8FAFC.toInt(),
                0xFFAAB5C5.toInt(), 0xFF151515.toInt(), 0xFFBAA7FF.toInt(),
                0xFF6EE7B7.toInt(), 0xFFFCCB76.toInt(), 0xFF94A3B8.toInt(),
                0xFF7DD3FC.toInt(), "amoled"
            )
            PreviewTheme.EYE_CARE -> PreviewPalette(
                0xFFF2E7CC.toInt(), 0xFFFAF1DC.toInt(), 0xFF493D2C.toInt(),
                0xFF776950.toInt(), 0xFFE5D9BC.toInt(), 0xFF84533D.toInt(),
                0xFF386A52.toInt(), 0xFF97631D.toInt(), 0xFF86745C.toInt(),
                0xFF365F85.toInt(), "eye"
            )
            else -> PreviewPalette(
                0xFF151820.toInt(), 0xFF232833.toInt(), 0xFFF1F5F9.toInt(),
                0xFFBBC6D4.toInt(), 0xFF1A202A.toInt(), 0xFFC4A7FF.toInt(),
                0xFF8FE5BC.toInt(), 0xFFF8C776.toInt(), 0xFF8298AC.toInt(),
                0xFF89BEFF.toInt(), "dark"
            )
        }
    }
}
