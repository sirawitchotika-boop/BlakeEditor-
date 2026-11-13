package com.blake.editor.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class AtomicPrivateFileWriterTest {

    @Test
    fun stagedWriteFailurePreservesExistingStateAndCleansUp() {
        val directory = Files.createTempDirectory("blake_atomic_session").toFile()
        try {
            val target = File(directory, "session_state.properties")
            target.writeText("previous session")
            try {
                AtomicPrivateFileWriter.write(target) { out ->
                    out.write("incomplete replacement".toByteArray(Charsets.UTF_8))
                    throw IOException("Simulated interrupted serialization")
                }
                fail("Expected the staged write to fail")
            } catch (expected: IOException) {
                assertEquals("Simulated interrupted serialization", expected.message)
            }

            assertEquals("previous session", target.readText())
            assertEquals(
                listOf("session_state.properties"),
                directory.listFiles()!!.map { it.name }.sorted()
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun successfulWriteAtomicallyReplacesLegacyState() {
        val directory = Files.createTempDirectory("blake_atomic_session").toFile()
        try {
            val target = File(directory, "session_state.properties")
            target.writeText("legacy session")
            AtomicPrivateFileWriter.write(target) { out ->
                out.write("new session".toByteArray(Charsets.UTF_8))
            }

            assertEquals("new session", target.readText())
            assertTrue(target.exists())
            assertEquals(
                listOf("session_state.properties"),
                directory.listFiles()!!.map { it.name }.sorted()
            )
        } finally {
            directory.deleteRecursively()
        }
    }
}
