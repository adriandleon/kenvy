package io.github.adriandleon.kenvy

object PlaceholderDetector {

    val PLACEHOLDER_SENTINELS: Set<String> = setOf(
        "",
        "placeholder",
        "<your_value>",
        "replace_me"
    )

    fun isPlaceholderValue(value: String?): Boolean =
        value == null || value.trim().lowercase() in PLACEHOLDER_SENTINELS

    fun findUnresolved(properties: List<KenvyProperty>): List<KenvyProperty> =
        properties.filter { it.isPlaceholder }
}
