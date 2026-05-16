package io.github.adriandleon.kenvy

internal data class ParsedKenvyContract(
    val properties: List<KenvyProperty>,
    val commonOverrides: Map<String, String> = emptyMap(),
    val platformOverrides: Map<String, Map<String, String>> = emptyMap(),
    val variantOverrides: Map<String, Map<String, Map<String, String>>> = emptyMap(),
    internal val externalProviderRequests: List<KenvyExternalProviderRequest> = emptyList(),
    val security: KenvySecuritySettings = KenvySecuritySettings()
)

data class KenvySecuritySettings(
    val secretFiles: List<String> = listOf("local.properties")
)
