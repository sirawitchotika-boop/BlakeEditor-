package com.blake.editor.intent

import org.junit.Assert.assertNull
import org.junit.Test

class IncomingIntentParserTest {

    @Test
    fun testParseNullIntentReturnsNull() {
        val request = IncomingIntentParser.parse(null)
        assertNull(request)
    }

    @Test
    fun testInstanceMethodParseNullReturnsNull() {
        val parser = IncomingIntentParser()
        assertNull(parser.parse(null))
    }
}
