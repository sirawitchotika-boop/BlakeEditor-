package com.blake.editor.ui.lab

internal data class InputProbeSample(val name: String, val content: String)

/** Real text samples; the caret itself must be assessed on an Android device. */
internal object InputProbeSamples {
    val all = listOf(
        InputProbeSample("Thai", "สวัสดีครับ"),
        InputProbeSample("Mixed", "Hello สวัสดีครับ 123 🌏"),
        InputProbeSample("Emoji", "👩‍💻 สวัสดีครับ 😊"),
        InputProbeSample(
            "Wrapped",
            "สวัสดีครับ English 👩‍💻 ".repeat(7).trim()
        ),
        InputProbeSample(
            "Code",
            "fun main() {\n    println(\"สวัสดีครับ 👋\")\n}"
        )
    )
}
