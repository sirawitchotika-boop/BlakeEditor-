package com.blake.editor.settings

import com.blake.editor.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeControllerTest {

    @Test
    fun testThemeModeParsing() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromName("DARK"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromName("dark"))
        assertEquals(ThemeMode.AMOLED, ThemeMode.fromName("AMOLED"))
        assertEquals(ThemeMode.AMOLED, ThemeMode.fromName("amoled"))
        assertEquals(ThemeMode.EYE_CARE, ThemeMode.fromName("EYE_CARE"))
        assertEquals(ThemeMode.EYE_CARE, ThemeMode.fromName("eye_care"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromName(null))
        assertEquals(ThemeMode.DARK, ThemeMode.fromName("unknown_theme"))
    }

    @Test
    fun testDarkPalette() {
        val palette = ThemeController.getPalette(ThemeMode.DARK)
        assertNotNull(palette)
        assertEquals(ThemeController.COLOR_DARK_BG, palette.backgroundColor)
        assertEquals(ThemeController.COLOR_DARK_SURFACE, palette.surfaceColor)
        assertEquals(ThemeController.COLOR_DARK_TEXT_PRIMARY, palette.primaryTextColor)
        assertEquals(ThemeController.COLOR_DARK_TEXT_SECONDARY, palette.secondaryTextColor)
        assertEquals(ThemeController.COLOR_DARK_BUTTON_BG, palette.buttonBackgroundColor)
        assertEquals(ThemeController.COLOR_DARK_BUTTON_TEXT, palette.buttonTextColor)
        assertEquals("twilight", palette.aceThemeName)
    }

    @Test
    fun testAmoledPaletteIsPureBlack() {
        val palette = ThemeController.getPalette(ThemeMode.AMOLED)
        assertNotNull(palette)
        assertEquals(0xFF000000.toInt(), palette.backgroundColor)
        assertEquals(0xFF000000.toInt(), palette.surfaceColor)
        assertEquals(ThemeController.COLOR_AMOLED_TEXT_PRIMARY, palette.primaryTextColor)
        assertEquals("twilight", palette.aceThemeName)
    }

    @Test
    fun testEyeCarePaletteIsWarmSepia() {
        val palette = ThemeController.getPalette(ThemeMode.EYE_CARE)
        assertNotNull(palette)
        assertEquals(ThemeController.COLOR_EYE_CARE_BG, palette.backgroundColor)
        assertEquals(ThemeController.COLOR_EYE_CARE_SURFACE, palette.surfaceColor)
        assertEquals(ThemeController.COLOR_EYE_CARE_TEXT_PRIMARY, palette.primaryTextColor)
        assertEquals(ThemeController.COLOR_EYE_CARE_TEXT_SECONDARY, palette.secondaryTextColor)
        assertEquals(ThemeController.COLOR_EYE_CARE_BUTTON_BG, palette.buttonBackgroundColor)
        assertEquals(ThemeController.COLOR_EYE_CARE_BUTTON_TEXT, palette.buttonTextColor)
        assertEquals("chrome", palette.aceThemeName)
    }

    @Test
    fun testPaletteColorsAreDistinctAcrossThemes() {
        val dark = ThemeController.getPalette(ThemeMode.DARK)
        val amoled = ThemeController.getPalette(ThemeMode.AMOLED)
        val eyeCare = ThemeController.getPalette(ThemeMode.EYE_CARE)

        assertTrue(dark.backgroundColor != amoled.backgroundColor)
        assertTrue(dark.backgroundColor != eyeCare.backgroundColor)
        assertTrue(amoled.backgroundColor != eyeCare.backgroundColor)
    }
}
