package io.github.adriandleon.kenvy

data class ResolvedKenvyValue(
    val property: KenvyProperty,
    val resolvedValue: String?,
    val source: ResolutionSource
) {
    fun displayValue(): String = KenvyValueMasker.displayValue(property, resolvedValue)
}

internal object KenvyValueMasker {
    private const val MASKED_VALUE = "****"

    fun displayValue(property: KenvyProperty, value: String?): String =
        if (property.sensitive && value != null) MASKED_VALUE else value.orEmpty()
}
