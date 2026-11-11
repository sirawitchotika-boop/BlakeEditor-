package com.blake.editor.settings

import com.blake.editor.core.model.ThemeMode
import com.blake.editor.ui.preview.PreviewTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedEditorThemeTest {
    @Test fun bothEditorsOfferTheSameThemeNames() {
        assertEquals(ThemeMode.values().map { it.name }.toSet(),
            PreviewTheme.values().map { it.name }.toSet())
        for (theme in PreviewTheme.values()) {
            assertEquals(ThemeMode.valueOf(theme.name),
                SharedEditorTheme.fromStored(theme.name))
        }
    }

    @Test fun missingSharedSelectionKeepsTheLegacyPreference() {
        assertNull(SharedEditorTheme.fromStored(null))
        assertNull(SharedEditorTheme.fromStored(""))
        assertNull(SharedEditorTheme.fromStored("unknown"))
        assertEquals(ThemeMode.EYE_CARE, SharedEditorTheme.fromStored("EYE_CARE"))
    }
}
