package com.blake.editor.core.util

object FileNameUtils {

    private val INVALID_CHARS = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')

    fun getExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot > 0 && lastDot < fileName.length - 1) {
            fileName.substring(lastDot + 1).lowercase()
        } else {
            ""
        }
    }

    fun sanitizeFileName(fileName: String, defaultName: String = "untitled.txt"): String {
        val trimmed = fileName.trim()
        if (trimmed.isEmpty()) return defaultName
        val sb = StringBuilder()
        for (ch in trimmed) {
            if (ch in INVALID_CHARS) {
                sb.append('_')
            } else {
                sb.append(ch)
            }
        }
        val sanitized = sb.toString().trim()
        return if (sanitized.isEmpty()) defaultName else sanitized
    }
}
