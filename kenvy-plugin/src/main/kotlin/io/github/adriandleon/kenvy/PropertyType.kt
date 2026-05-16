package io.github.adriandleon.kenvy

enum class PropertyType {
    STRING, INT, BOOLEAN, LONG;

    companion object {
        fun fromString(value: String): PropertyType? = when (value) {
            "String" -> STRING
            "Int" -> INT
            "Boolean" -> BOOLEAN
            "Long" -> LONG
            else -> null
        }
    }
}
