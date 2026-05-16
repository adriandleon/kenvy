package io.github.adriandleon.kenvy

import org.gradle.api.GradleException

internal object KenvyMerger {

    fun mergeLocalProperties(
        contract: ParsedKenvyContract,
        platform: String?,
        variant: String?,
        existingLocalProperties: Map<String, String>,
        forceKeys: Set<String> = emptySet()
    ): KenvyMergeResult {
        val declaredNames = contract.properties.map { it.name }.toSet()

        val unknownForceKeys = forceKeys - declaredNames
        if (unknownForceKeys.isNotEmpty()) {
            throw GradleException(
                "Kenvy: Force keys ${unknownForceKeys.sorted()} are not declared in the contract. " +
                    "Only declared property names may be force-overwritten."
            )
        }

        // Resolve contract-derived candidates with no local file and no env provider.
        val contractCandidates = KenvyResolver.resolve(
            contract = contract,
            platform = platform,
            variant = variant,
            localProperties = emptyMap()
        ) { null }
        val contractSources = contractCandidates.associate { it.property.name to it.source }

        val merged = existingLocalProperties.toMutableMap()
        val overwrittenKeys = mutableSetOf<String>()
        val overwrittenSources = linkedMapOf<String, ResolutionSource>()

        for (resolved in contractCandidates) {
            val key = resolved.property.name
            val contractValue = resolved.resolvedValue ?: continue

            when {
                key in forceKeys -> {
                    if (merged[key] != contractValue) {
                        overwrittenKeys.add(key)
                        overwrittenSources[key] = contractSources.getValue(key)
                    }
                    merged[key] = contractValue
                }
                !merged.containsKey(key) -> {
                    merged[key] = contractValue
                }
                // key already present locally — leave it unchanged
            }
        }

        val preservedLocalOnlyKeys = existingLocalProperties.keys - declaredNames

        return KenvyMergeResult(
            mergedProperties = merged,
            overwrittenKeys = overwrittenKeys,
            preservedLocalOnlyKeys = preservedLocalOnlyKeys,
            overwrittenSources = overwrittenSources
        )
    }
}
