package com.blake.editor.file

object SupportedFileTypes {
    val SUPPORTED_EXTENSIONS = setOf(
        "txt", "md", "markdown", "log", "conf", "ini",
        "kt", "kts", "java", "js", "mjs", "cjs", "ts",
        "py", "html", "htm", "css", "json", "xml", "svg",
        "yml", "yaml", "sql", "sh", "bash", "zsh", "fish",
        "c", "cpp", "h", "hpp", "cc", "rs", "go"
    )

    val SUPPORTED_MIME_TYPES = setOf(
        "text/plain",
        "text/markdown",
        "text/x-markdown",
        "text/html",
        "text/css",
        "application/json",
        "text/xml",
        "application/xml",
        "application/x-yaml",
        "text/yaml",
        "text/x-python",
        "text/x-shellscript"
    )

    fun isSupportedExtension(extension: String): Boolean {
        return SUPPORTED_EXTENSIONS.contains(extension.lowercase())
    }
}
