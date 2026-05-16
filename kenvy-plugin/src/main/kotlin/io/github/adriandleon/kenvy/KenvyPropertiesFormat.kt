package io.github.adriandleon.kenvy

internal object KenvyPropertiesFormat {

    fun escapePropertyKey(value: String): String =
        escapePropertyToken(value, escapeSpace = true, escapeSeparators = true)

    fun escapePropertyValue(value: String): String =
        escapePropertyToken(value, escapeSpace = false, escapeSeparators = false)

    private fun escapePropertyToken(
        value: String,
        escapeSpace: Boolean,
        escapeSeparators: Boolean
    ): String = buildString {
        value.forEachIndexed { index, char ->
            when (char) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\u000C' -> append("\\f")
                ' ' -> if (index == 0 || escapeSpace) append("\\ ") else append(char)
                '=', ':' -> if (escapeSeparators) append('\\').append(char) else append(char)
                '#', '!' -> if (index == 0) append('\\').append(char) else append(char)
                else -> append(char)
            }
        }
    }
}
