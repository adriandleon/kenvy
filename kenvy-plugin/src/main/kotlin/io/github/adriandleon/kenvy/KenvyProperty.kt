package io.github.adriandleon.kenvy

data class KenvyProperty(
    val name: String,
    val type: PropertyType,
    val defaultValue: String? = null,
    val description: String? = null,
    val helpUrl: String? = null,
    val sensitive: Boolean = false
) {
    val isPlaceholder: Boolean
        get() = defaultValue == null ||
                defaultValue.trim().lowercase() in PlaceholderDetector.PLACEHOLDER_SENTINELS
}
