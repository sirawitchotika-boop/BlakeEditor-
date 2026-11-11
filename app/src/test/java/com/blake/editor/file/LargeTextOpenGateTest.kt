package com.blake.editor.file

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeTextOpenGateTest {
    private val max = LargeTextOpenGate.MAX_EDIT_BYTES

    @Test fun reportedLargeFileRoutesToPagesWithoutOpeningStream() {
        val decision = LargeTextOpenGate.classify(max.toLong() + 1) {
            throw AssertionError("Do not open a known-oversize file during routing")
        }
        assertEquals(LargeTextOpenGate.Decision.READ_ONLY_PAGES, decision)
    }

    @Test fun unknownSizeRoutesBigFileToPages() {
        val data = ByteArray(max + 18) { 'x'.code.toByte() }
        val decision = LargeTextOpenGate.classify(null) {
            ByteArrayInputStream(data)
        }
        assertEquals(LargeTextOpenGate.Decision.READ_ONLY_PAGES, decision)
    }

    @Test fun reportedSizeThatLiesCannotBypassLimit() {
        val data = ByteArray(max + 30) { 65 }
        assertEquals(LargeTextOpenGate.Decision.READ_ONLY_PAGES,
            LargeTextOpenGate.classify(100) { ByteArrayInputStream(data) })
    }

    @Test fun exactBoundaryIsEditableAndSmallFileUnchanged() {
        val exact = ByteArray(max)
        assertEquals(LargeTextOpenGate.Decision.EDIT,
            LargeTextOpenGate.classify(0) { ByteArrayInputStream(exact) })
        assertEquals(LargeTextOpenGate.Decision.EDIT,
            LargeTextOpenGate.classify(null) {
                ByteArrayInputStream("สวัสดีครับ 🌏".toByteArray(Charsets.UTF_8))
            })
    }

    @Test fun boundedProbeStopsAfterMaxPlusOneEvenForHugeInput() {
        var consumed = 0L
        var closed = false
        val endless = object : InputStream() {
            override fun read(): Int {
                consumed++
                return 65
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                b.fill(65, off, off + len)
                consumed += len
                return len
            }
            override fun close() { closed = true }
        }
        assertEquals(LargeTextOpenGate.Decision.READ_ONLY_PAGES,
            LargeTextOpenGate.classify(null) { endless })
        assertEquals(max.toLong() + 1L, consumed)
        assertTrue(closed)
    }

    @Test fun providerReadErrorDoesNotSilentlyTreatLargeFileAsEditable() {
        val error = runCatching {
            LargeTextOpenGate.classify(null) {
                throw SecurityException("Cannot read provider")
            }
        }.exceptionOrNull()
        assertTrue(error is SecurityException)
    }
}
