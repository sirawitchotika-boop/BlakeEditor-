package com.blake.editor.file

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageHistoryTest {
    @Test fun nextThenPreviousReturnsToOriginalPage() {
        val bytes = "First page. Second page. Third page.".toByteArray()
        val history = ReaderPageHistory()
        val first = PagedTextReader.readPage(
            ByteArrayInputStream(bytes), TextFileEncoding.UTF8, 0, 12
        )
        assertTrue(first.hasMore)
        assertFalse(history.canGoBack)
        assertEquals(1, history.pageNumber)

        val second = PagedTextReader.readPage(
            ByteArrayInputStream(bytes), TextFileEncoding.UTF8, first.nextChar, 12
        )
        // The activity must record navigation on successful page load.
        history.recordNext(first.startChar)
        assertEquals(2, history.pageNumber)
        assertTrue(history.canGoBack)
        assertEquals(0L, history.previousStart())

        val previous = PagedTextReader.readPage(
            ByteArrayInputStream(bytes), TextFileEncoding.UTF8, history.previousStart()!!, 12
        )
        assertEquals(first, previous)
        history.recordPrevious()
        assertEquals(1, history.pageNumber)
        assertFalse(history.canGoBack)
        assertNull(history.previousStart())
        assertTrue(second.startChar > 0)
    }

    @Test fun multiplePagesHaveCorrectBackStackOrder() {
        val history = ReaderPageHistory()
        history.recordNext(0L)
        history.recordNext(16000L)
        history.recordNext(32000L)
        assertEquals(4, history.pageNumber)
        assertEquals(32000L, history.previousStart())
        history.recordPrevious()
        assertEquals(16000L, history.previousStart())
        history.recordPrevious()
        assertEquals(0L, history.previousStart())
        history.recordPrevious()
        assertFalse(history.canGoBack)
        assertEquals(1, history.pageNumber)
    }

    @Test fun failedNextLoadDoesNotAlterHistory() {
        val history = ReaderPageHistory()
        // No recordNext call when an asynchronous page read fails.
        assertEquals(1, history.pageNumber)
        assertNull(history.previousStart())
        history.recordNext(0L)
        // No recordPrevious call when an asynchronous back-page read fails.
        assertEquals(2, history.pageNumber)
        assertEquals(0L, history.previousStart())
    }

    @Test fun encodingSwitchResetsPageHistory() {
        val history = ReaderPageHistory()
        history.recordNext(0L)
        history.recordNext(16000L)
        history.reset()
        assertEquals(1, history.pageNumber)
        assertFalse(history.canGoBack)
        assertNull(history.previousStart())
    }
}
