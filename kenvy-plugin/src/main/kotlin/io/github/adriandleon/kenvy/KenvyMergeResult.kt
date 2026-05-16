package io.github.adriandleon.kenvy

data class KenvyMergeResult(
    val mergedProperties: Map<String, String>,
    val overwrittenKeys: Set<String>,
    val preservedLocalOnlyKeys: Set<String>,
    val overwrittenSources: Map<String, ResolutionSource> = emptyMap()
)
