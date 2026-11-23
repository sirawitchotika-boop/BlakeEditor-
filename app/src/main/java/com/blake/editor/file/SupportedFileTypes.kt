package com.blake.editor.file

import com.blake.editor.core.util.FileNameUtils

object SupportedFileTypes {
    val MARKDOWN_EXTENSIONS = setOf("md", "markdown")
    val SUPPORTED_EXTENSIONS = setOf("txt", "md", "markdown", "text", "log")

    val SUPPORTED_MIME_TYPES = setOf(
        "text/plain",
        "text/markdown",
        "text/x-markdown"
    )

    fun isSupportedExtension(extension: String): Boolean =
        SUPPORTED_EXTENSIONS.contains(extension.lowercase())

    fun isMarkdownFile(fileName: String, mimeType: String? = null): Boolean {
        val ext = FileNameUtils.getExtension(fileName)
        if (ext in MARKDOWN_EXTENSIONS) return true
        val mime = mimeType?.lowercase().orEmpty()
        return mime == "text/markdown" || mime == "text/x-markdown"
    }
}
