package com.blake.editor.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewThemeSelectionTest {
    @Test fun followSystemChoosesLightOrDarkWithoutChangingUserChoice() {
        assertEquals(PreviewTheme.LIGHT,
            PreviewTheme.FOLLOW_SYSTEM.resolvedForSystem(systemDark = false))
        assertEquals(PreviewTheme.DARK,
            PreviewTheme.FOLLOW_SYSTEM.resolvedForSystem(systemDark = true))
    }

    @Test fun explicitUserThemesIgnoreSystemNightMode() {
        val fixed = listOf(
            PreviewTheme.LIGHT, PreviewTheme.DARK,
            PreviewTheme.AMOLED, PreviewTheme.EYE_CARE
        )
        for (choice in fixed) {
            assertEquals(choice, choice.resolvedForSystem(systemDark = false))
            assertEquals(choice, choice.resolvedForSystem(systemDark = true))
        }
    }
}
