package com.blake.editor.core.model

enum class SyntaxMode(val aceMode: String, val displayName: String) {
    PLAIN_TEXT("text", "Plain Text"),
    MARKDOWN("markdown", "Markdown"),
    KOTLIN("kotlin", "Kotlin"),
    JAVA("java", "Java"),
    JAVASCRIPT("javascript", "JavaScript"),
    TYPESCRIPT("typescript", "TypeScript"),
    PYTHON("python", "Python"),
    HTML("html", "HTML"),
    CSS("css", "CSS"),
    JSON("json", "JSON"),
    XML("xml", "XML"),
    YAML("yaml", "YAML"),
    SQL("sql", "SQL"),
    SHELL("sh", "Shell"),
    C_CPP("c_cpp", "C/C++"),
    RUST("rust", "Rust"),
    GO("golang", "Go")
}
