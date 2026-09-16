package com.blake.editor.ui.main

import com.blake.editor.core.model.SyntaxMode
import com.blake.editor.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiStateTest {

    @Test
    fun testDefaultUiState() {
        val state = MainUiState()
        assertNull(state.currentUri)
        assertEquals("untitled.txt", state.fileName)
        assertEquals("", state.content)
        assertEquals(SyntaxMode.PLAIN_TEXT, state.syntaxMode)
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertFalse(state.isDirty)
        assertFalse(state.isLoading)
        assertFalse(state.isPreviewing)
        assertEquals(1, state.cursorLine)
        assertEquals(1, state.cursorColumn)
        assertEquals(0, state.scrollPosition)
        assertEquals("Blake Editor   Phase 8 Ready", state.statusMessage)
    }

    @Test
    fun testTogglePreviewState() {
        val state = MainUiState()
        assertFalse(state.isPreviewing)
        val previewingState = state.copy(isPreviewing = true)
        assertTrue(previewingState.isPreviewing)
        val editingState = previewingState.copy(isPreviewing = false)
        assertFalse(editingState.isPreviewing)
    }
}
