package com.blake.editor.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSafeAreaTest {
    @Test fun cameraCutoutAndStatusBarUseMaximumNotSum() {
        val base = SafeEdges(left = 10, top = 12, right = 10, bottom = 10)
        val bars = SafeEdges(top = 56, bottom = 24)
        val cutout = SafeEdges(top = 82)
        val p = PreviewSafeArea.padding(base, bars, cutout, keyboardBottom = 0)
        assertEquals(94, p.top)
        assertEquals(34, p.bottom)
        assertEquals(10, p.left)
    }

    @Test fun landscapeCameraCutoutProtectsSideButtons() {
        val p = PreviewSafeArea.padding(
            SafeEdges(8, 8, 8, 8),
            SafeEdges(left = 12, top = 25, right = 0, bottom = 22),
            SafeEdges(left = 65),
            0
        )
        assertEquals(73, p.left)
        assertEquals(33, p.top)
    }

    @Test fun keyboardUsesMaxInsteadOfAddingNavigationBar() {
        val base = SafeEdges(bottom = 10)
        val bars = SafeEdges(bottom = 27)
        assertEquals(510, PreviewSafeArea.padding(base, bars, SafeEdges(), 500).bottom)
        assertEquals(37, PreviewSafeArea.padding(base, bars, SafeEdges(), 0).bottom)
    }

    @Test fun paddingNeverAccumulatesOnRepeatedInsetNotifications() {
        val base = SafeEdges(10, 12, 10, 10)
        val status = SafeEdges(top = 73)
        assertEquals(
            PreviewSafeArea.padding(base, status, SafeEdges(), 0),
            PreviewSafeArea.padding(base, status, SafeEdges(), 0)
        )
        assertEquals(85, PreviewSafeArea.padding(base, status, SafeEdges(), 0).top)
    }

    @Test fun systemIconsContrastWithAllPreviewThemeBackgrounds() {
        assertFalse(PreviewSafeArea.darkSystemIcons(0xFF151820.toInt()))
        assertFalse(PreviewSafeArea.darkSystemIcons(0xFF000000.toInt()))
        assertTrue(PreviewSafeArea.darkSystemIcons(0xFFF6F7FA.toInt()))
        assertTrue(PreviewSafeArea.darkSystemIcons(0xFFF2E7CC.toInt()))
    }
}
