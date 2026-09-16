package com.blake.editor.settings

import com.blake.editor.core.model.ThemeMode

data class ThemePalette(
    val backgroundColor: Int,
    val surfaceColor: Int,
    val primaryTextColor: Int,
    val secondaryTextColor: Int,
    val buttonBackgroundColor: Int,
    val buttonTextColor: Int,
    val aceThemeName: String
)

object ThemeController {

    const val COLOR_DARK_BG = 0xFF121212.toInt()
    const val COLOR_DARK_SURFACE = 0xFF1E1E1E.toInt()
    const val COLOR_DARK_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    const val COLOR_DARK_TEXT_SECONDARY = 0xFFAAAAAA.toInt()
    const val COLOR_DARK_BUTTON_BG = 0xFF2C2C2C.toInt()
    const val COLOR_DARK_BUTTON_TEXT = 0xFFFFFFFF.toInt()

    const val COLOR_AMOLED_BG = 0xFF000000.toInt()
    const val COLOR_AMOLED_SURFACE = 0xFF000000.toInt()
    const val COLOR_AMOLED_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    const val COLOR_AMOLED_TEXT_SECONDARY = 0xFF888888.toInt()
    const val COLOR_AMOLED_BUTTON_BG = 0xFF1A1A1A.toInt()
    const val COLOR_AMOLED_BUTTON_TEXT = 0xFFFFFFFF.toInt()

    const val COLOR_EYE_CARE_BG = 0xFFF8F1E5.toInt()
    const val COLOR_EYE_CARE_SURFACE = 0xFFEDE4D3.toInt()
    const val COLOR_EYE_CARE_TEXT_PRIMARY = 0xFF2C2523.toInt()
    const val COLOR_EYE_CARE_TEXT_SECONDARY = 0xFF7D756D.toInt()
    const val COLOR_EYE_CARE_BUTTON_BG = 0xFFDFD3BF.toInt()
    const val COLOR_EYE_CARE_BUTTON_TEXT = 0xFF2C2523.toInt()

    fun getPalette(mode: ThemeMode): ThemePalette {
        return when (mode) {
            ThemeMode.DARK -> ThemePalette(
                backgroundColor = COLOR_DARK_BG,
                surfaceColor = COLOR_DARK_SURFACE,
                primaryTextColor = COLOR_DARK_TEXT_PRIMARY,
                secondaryTextColor = COLOR_DARK_TEXT_SECONDARY,
                buttonBackgroundColor = COLOR_DARK_BUTTON_BG,
                buttonTextColor = COLOR_DARK_BUTTON_TEXT,
                aceThemeName = "twilight"
            )
            ThemeMode.AMOLED -> ThemePalette(
                backgroundColor = COLOR_AMOLED_BG,
                surfaceColor = COLOR_AMOLED_SURFACE,
                primaryTextColor = COLOR_AMOLED_TEXT_PRIMARY,
                secondaryTextColor = COLOR_AMOLED_TEXT_SECONDARY,
                buttonBackgroundColor = COLOR_AMOLED_BUTTON_BG,
                buttonTextColor = COLOR_AMOLED_BUTTON_TEXT,
                aceThemeName = "twilight"
            )
            ThemeMode.EYE_CARE -> ThemePalette(
                backgroundColor = COLOR_EYE_CARE_BG,
                surfaceColor = COLOR_EYE_CARE_SURFACE,
                primaryTextColor = COLOR_EYE_CARE_TEXT_PRIMARY,
                secondaryTextColor = COLOR_EYE_CARE_TEXT_SECONDARY,
                buttonBackgroundColor = COLOR_EYE_CARE_BUTTON_BG,
                buttonTextColor = COLOR_EYE_CARE_BUTTON_TEXT,
                aceThemeName = "chrome"
            )
        }
    }
}
