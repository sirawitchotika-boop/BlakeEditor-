package com.blake.editor.editor

import com.blake.editor.core.model.SyntaxMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SyntaxResolverTest {

    @Test
    fun testResolveByExtension() {
        assertEquals(SyntaxMode.PLAIN_TEXT, SyntaxResolver.resolve("file.txt"))
        assertEquals(SyntaxMode.MARKDOWN, SyntaxResolver.resolve("README.md"))
        assertEquals(SyntaxMode.KOTLIN, SyntaxResolver.resolve("App.kt"))
        assertEquals(SyntaxMode.JAVA, SyntaxResolver.resolve("Main.java"))
        assertEquals(SyntaxMode.JAVASCRIPT, SyntaxResolver.resolve("index.js"))
        assertEquals(SyntaxMode.TYPESCRIPT, SyntaxResolver.resolve("app.ts"))
        assertEquals(SyntaxMode.PYTHON, SyntaxResolver.resolve("script.py"))
        assertEquals(SyntaxMode.HTML, SyntaxResolver.resolve("index.html"))
        assertEquals(SyntaxMode.CSS, SyntaxResolver.resolve("style.css"))
        assertEquals(SyntaxMode.JSON, SyntaxResolver.resolve("data.json"))
        assertEquals(SyntaxMode.XML, SyntaxResolver.resolve("manifest.xml"))
        assertEquals(SyntaxMode.YAML, SyntaxResolver.resolve("config.yaml"))
        assertEquals(SyntaxMode.SQL, SyntaxResolver.resolve("query.sql"))
        assertEquals(SyntaxMode.SHELL, SyntaxResolver.resolve("deploy.sh"))
        assertEquals(SyntaxMode.C_CPP, SyntaxResolver.resolve("main.cpp"))
        assertEquals(SyntaxMode.RUST, SyntaxResolver.resolve("lib.rs"))
        assertEquals(SyntaxMode.GO, SyntaxResolver.resolve("server.go"))
    }

    @Test
    fun testResolveByMimeFallback() {
        assertEquals(SyntaxMode.MARKDOWN, SyntaxResolver.resolve("unknown_file", "text/markdown"))
        assertEquals(SyntaxMode.JSON, SyntaxResolver.resolve("unknown_file", "application/json"))
        assertEquals(SyntaxMode.PLAIN_TEXT, SyntaxResolver.resolve("unknown_file", "unknown/mime"))
    }
}
