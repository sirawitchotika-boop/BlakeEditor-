package com.blake.editor.editor

import com.blake.editor.core.model.SyntaxMode
import com.blake.editor.core.util.FileNameUtils

object SyntaxResolver {
    fun resolve(fileName: String, mimeType: String? = null): SyntaxMode {
        val ext = FileNameUtils.getExtension(fileName)
        return when (ext) {
            "txt", "log", "conf", "ini", "text" -> SyntaxMode.PLAIN_TEXT
            "md", "markdown" -> SyntaxMode.MARKDOWN
            "kt", "kts" -> SyntaxMode.KOTLIN
            "java" -> SyntaxMode.JAVA
            "js", "mjs", "cjs" -> SyntaxMode.JAVASCRIPT
            "ts" -> SyntaxMode.TYPESCRIPT
            "py" -> SyntaxMode.PYTHON
            "html", "htm" -> SyntaxMode.HTML
            "css" -> SyntaxMode.CSS
            "json" -> SyntaxMode.JSON
            "xml", "svg" -> SyntaxMode.XML
            "yml", "yaml" -> SyntaxMode.YAML
            "sql" -> SyntaxMode.SQL
            "sh", "bash", "zsh", "fish" -> SyntaxMode.SHELL
            "c", "cpp", "h", "hpp", "cc" -> SyntaxMode.C_CPP
            "rs" -> SyntaxMode.RUST
            "go" -> SyntaxMode.GO
            else -> resolveFromMime(mimeType)
        }
    }

    private fun resolveFromMime(mimeType: String?): SyntaxMode {
        return when (mimeType) {
            "text/markdown", "text/x-markdown" -> SyntaxMode.MARKDOWN
            "text/html" -> SyntaxMode.HTML
            "text/css" -> SyntaxMode.CSS
            "application/json" -> SyntaxMode.JSON
            "text/xml", "application/xml" -> SyntaxMode.XML
            "application/x-yaml", "text/yaml" -> SyntaxMode.YAML
            "text/x-python" -> SyntaxMode.PYTHON
            "text/x-shellscript" -> SyntaxMode.SHELL
            else -> SyntaxMode.PLAIN_TEXT
        }
    }
}
