package com.blake.editor.editor.v2

/** Bounded syntax coloring for native EditText, without rewriting source text. */
enum class CodeTokenKind { KEYWORD, STRING, NUMBER, COMMENT, TAG }

data class CodeToken(val start: Int, val end: Int, val kind: CodeTokenKind) {
    init { require(start >= 0 && end > start) }
}

object CodeSyntaxScanner {
    const val MAX_HIGHLIGHT_CHARS = 80_000

    private val common = setOf("true", "false", "null", "this", "super", "return",
        "if", "else", "for", "while", "do", "break", "continue", "try", "catch",
        "throw", "finally", "new", "class", "interface", "extends", "import", "package")
    private val jvm = common + setOf("fun", "val", "var", "when", "object", "data",
        "sealed", "private", "protected", "public", "internal", "override", "open",
        "abstract", "suspend", "inline", "const", "lateinit", "companion", "enum",
        "static", "void", "int", "long", "boolean", "float", "double", "char", "String")
    private val javascript = common + setOf("const", "let", "var", "function",
        "async", "await", "export", "default", "from", "typeof", "undefined",
        "yield", "instanceof", "implements", "type", "number", "string")
    private val python = setOf("and", "as", "assert", "async", "await", "break",
        "class", "continue", "def", "del", "elif", "else", "except", "False",
        "finally", "for", "from", "global", "if", "import", "in", "is",
        "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
        "True", "try", "while", "with", "yield", "None", "self")
    private val css = setOf("color", "background", "display", "position",
        "margin", "padding", "font", "width", "height", "flex", "grid", "auto",
        "none", "block", "inline", "important")
    private val yaml = setOf("true", "false", "null", "yes", "no", "on", "off")

    val supportedLanguages = setOf("KOTLIN", "JAVA", "JAVASCRIPT", "TYPESCRIPT",
        "PYTHON", "JSON", "HTML", "XML", "CSS", "YAML")

    fun isCode(language: String): Boolean = language in supportedLanguages

    /** Tokens never overlap. Long code stays editable without highlighting. */
    fun scan(source: String, language: String): List<CodeToken> {
        if (language !in supportedLanguages || source.length > MAX_HIGHLIGHT_CHARS) {
            return emptyList()
        }
        val tokens = ArrayList<CodeToken>()
        val words: Set<String> = when (language) {
            "KOTLIN", "JAVA" -> jvm
            "JAVASCRIPT", "TYPESCRIPT" -> javascript
            "PYTHON" -> python
            "JSON" -> setOf("true", "false", "null")
            "CSS" -> css
            "YAML" -> yaml
            else -> emptySet()
        }
        var i = 0
        while (i < source.length) {
            val start = i
            val c = source[i]
            val htmlLike = language == "HTML" || language == "XML"
            val slashComments = language in setOf("KOTLIN", "JAVA", "JAVASCRIPT",
                "TYPESCRIPT", "CSS")
            if ((slashComments && source.startsWith("//", i)) ||
                ((language == "PYTHON" || language == "YAML") && c == '#')
            ) {
                while (i < source.length && source[i] != '\n') i++
                tokens.add(CodeToken(start, i, CodeTokenKind.COMMENT))
                continue
            }
            val blockEnd = if (htmlLike && source.startsWith("<!--", i)) "-->"
                else if ((slashComments || language == "JSON") &&
                    source.startsWith("/*", i)) "*/"
                else null
            if (blockEnd != null) {
                i = (source.indexOf(blockEnd, i + 2).takeIf { it >= 0 }
                    ?.plus(blockEnd.length)) ?: source.length
                tokens.add(CodeToken(start, i, CodeTokenKind.COMMENT))
                continue
            }
            if (htmlLike && c == '<') {
                val close = source.indexOf('>', i + 1)
                if (close >= 0) {
                    i = close + 1
                    tokens.add(CodeToken(start, i, CodeTokenKind.TAG))
                    continue
                }
            }
            if (c == '"' || c == '\'' ||
                (language in setOf("JAVASCRIPT", "TYPESCRIPT") && c.code == 96)
            ) {
                val triple = language == "PYTHON" && source.startsWith("$c$c$c", i)
                i += if (triple) 3 else 1
                while (i < source.length) {
                    if (source[i] == '\\') {
                        i = (i + 2).coerceAtMost(source.length)
                    } else if (triple && source.startsWith("$c$c$c", i)) {
                        i += 3
                        break
                    } else if (!triple && source[i] == c) {
                        i++
                        break
                    } else if (!triple && source[i] == '\n' && c.code != 96) {
                        break
                    } else {
                        i++
                    }
                }
                tokens.add(CodeToken(start, i, CodeTokenKind.STRING))
                continue
            }
            if (c.isDigit()) {
                i++
                while (i < source.length && (source[i].isDigit() ||
                    source[i] == '.' || source[i] == '_' ||
                    source[i] in 'a'..'f' || source[i] in 'A'..'F' ||
                    source[i] == 'x' || source[i] == 'X')
                ) i++
                tokens.add(CodeToken(start, i, CodeTokenKind.NUMBER))
                continue
            }
            if (c.isLetter() || c == '_' || c == '$') {
                i++
                while (i < source.length && (source[i].isLetterOrDigit() ||
                    source[i] == '_' || source[i] == '$')
                ) i++
                if (source.substring(start, i) in words) {
                    tokens.add(CodeToken(start, i, CodeTokenKind.KEYWORD))
                }
                continue
            }
            i++
        }
        return tokens
    }
}
