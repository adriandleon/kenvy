package io.github.adriandleon.kenvy

import org.gradle.api.GradleException
import java.time.Duration
import java.util.Locale

internal object KenvyResolver {

    fun toEnvVarName(propertyName: String): String =
        "KENVY_${toLegacyEnvVarName(propertyName)}"

    fun toLegacyEnvVarName(propertyName: String): String =
        propertyName.trim()
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .uppercase(Locale.ROOT)

    fun toScopedEnvVarNames(propertyName: String, platform: String?, variant: String?): List<String> {
        val generic = toEnvVarName(propertyName)
        val normalizedPlatform = platform?.trim()
            ?.replace(Regex("[^A-Za-z0-9]+"), "_")
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
        val normalizedVariant = variant?.trim()
            ?.replace(Regex("[^A-Za-z0-9]+"), "_")
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
        return buildList {
            add(generic)
            if (normalizedPlatform != null) {
                add("${generic}_$normalizedPlatform")
                if (normalizedVariant != null) {
                    add("${generic}_${normalizedPlatform}_$normalizedVariant")
                }
            }
        }
    }

    fun validateEnvironmentNameCollisions(properties: List<KenvyProperty>) {
        val byEnvName = properties.groupBy { toEnvVarName(it.name) }
        val collisions = byEnvName.entries
            .filter { it.value.size > 1 }
            .map { collision ->
                val names = collision.value.joinToString { it.name }
                KenvyConfigurationIssue.resolutionConflict(
                    summary = "Properties $names map to the same environment variable '${collision.key}'. " +
                        "Rename one of the properties to avoid ambiguous environment overrides.",
                    details = listOf("Resolution chain: $KENVY_RESOLUTION_CHAIN")
                )
            }
        if (collisions.isNotEmpty()) {
            throw KenvyConfigurationConflictException(collisions)
        }
    }

    fun resolve(
        contract: ParsedKenvyContract,
        localProperties: Map<String, String>,
        legacyUnprefixedEnvironmentOverrides: Boolean = false,
        environmentProvider: (String) -> String?
    ): List<ResolvedKenvyValue> =
        resolve(
            contract = contract,
            platform = null,
            variant = null,
            localProperties = localProperties,
            legacyUnprefixedEnvironmentOverrides = legacyUnprefixedEnvironmentOverrides,
            environmentProvider = environmentProvider
        )

    fun resolve(
        contract: ParsedKenvyContract,
        platform: String? = null,
        variant: String? = null,
        localProperties: Map<String, String>,
        legacyUnprefixedEnvironmentOverrides: Boolean = false,
        externalProviderResolver: KenvyExternalProviderResolver? = null,
        externalProviderTimeout: Duration = KenvyExternalProviderTimeoutGate.DEFAULT_TIMEOUT,
        timeoutGateFactory: (Duration) -> KenvyExternalProviderTimeoutGate =
            { timeout -> KenvyExternalProviderTimeoutGate(timeout) },
        environmentProvider: (String) -> String?
    ): List<ResolvedKenvyValue> {
        validateEnvironmentNameCollisions(contract.properties)
        val propertiesByName = contract.properties.associateBy { it.name }
        val providerBackedPropertyNames = contract.externalProviderRequests.map { it.propertyName }.toSet()
        val providerRequestsRequiringProvider = contract.externalProviderRequests.filterNot { request ->
            val property = propertiesByName[request.propertyName]
            property != null && hasEnvironmentOrLocalOverride(
                property = property,
                platform = platform,
                variant = variant,
                localProperties = localProperties,
                legacyUnprefixedEnvironmentOverrides = legacyUnprefixedEnvironmentOverrides,
                environmentProvider = environmentProvider
            )
        }
        val externalProviderValues = if (providerRequestsRequiringProvider.isEmpty()) {
            emptyMap()
        } else {
            val resolver = externalProviderResolver ?: throw GradleException(
                "Kenvy: External provider requests were configured but no provider resolver is available."
            )
            timeoutGateFactory(externalProviderTimeout).resolve(providerRequestsRequiringProvider, resolver)
        }

        return contract.properties.map { prop ->
            resolve(
                property = prop,
                commonOverrides = contract.commonOverrides,
                platformOverrides = contract.platformOverrides[platform.normalizedOrNull()].orEmpty(),
                variantOverrides = contract.variantOverrides[platform.normalizedOrNull()]
                    ?.get(variant.normalizedOrNull())
                    .orEmpty(),
                externalProviderValues = externalProviderValues,
                externalProviderBacked = prop.name in providerBackedPropertyNames,
                platform = platform,
                variant = variant,
                localProperties = localProperties,
                legacyUnprefixedEnvironmentOverrides = legacyUnprefixedEnvironmentOverrides,
                environmentProvider = environmentProvider
            )
        }
    }

    fun resolve(
        property: KenvyProperty,
        commonOverrides: Map<String, String>,
        platformOverrides: Map<String, String> = emptyMap(),
        variantOverrides: Map<String, String> = emptyMap(),
        externalProviderValues: Map<String, String> = emptyMap(),
        externalProviderBacked: Boolean = false,
        platform: String? = null,
        variant: String? = null,
        localProperties: Map<String, String>,
        legacyUnprefixedEnvironmentOverrides: Boolean = false,
        environmentProvider: (String) -> String?
    ): ResolvedKenvyValue {
        resolveEnvironmentValue(
            property = property,
            platform = platform,
            variant = variant,
            legacyUnprefixedEnvironmentOverrides = legacyUnprefixedEnvironmentOverrides,
            environmentProvider = environmentProvider
        )?.let { environmentValue ->
            return ResolvedKenvyValue(property, environmentValue, ResolutionSource.ENVIRONMENT)
        }

        val localValue = resolveScopedLocalValue(
            propertyName = property.name,
            platform = platform,
            variant = variant,
            localProperties = localProperties
        )
        if (localValue != null) {
            return ResolvedKenvyValue(property, localValue, ResolutionSource.LOCAL_PROPERTIES)
                .orUnsafeLegacyEnvironmentConflict(legacyUnprefixedEnvironmentOverrides, environmentProvider)
        }

        // Temporary internal precedence for provider-backed properties:
        // environment/local emergency overrides stay highest priority, then provider values,
        // then variant/platform/common/default until Phase 2 defines the public provider model.
        if (externalProviderBacked) {
            val providerValue = externalProviderValues[property.name]
            if (providerValue != null) {
                return ResolvedKenvyValue(property, providerValue, ResolutionSource.EXTERNAL_PROVIDER)
                    .orUnsafeLegacyEnvironmentConflict(legacyUnprefixedEnvironmentOverrides, environmentProvider)
            }
        }

        val commonValue = commonOverrides[property.name]
        val platformValue = platformOverrides[property.name]
        val variantValue = variantOverrides[property.name]

        if (variantValue != null) {
            return ResolvedKenvyValue(property, variantValue, ResolutionSource.VARIANT_OVERRIDE)
                .orUnsafeLegacyEnvironmentConflict(legacyUnprefixedEnvironmentOverrides, environmentProvider)
        }

        if (platformValue != null) {
            return ResolvedKenvyValue(property, platformValue, ResolutionSource.PLATFORM_OVERRIDE)
                .orUnsafeLegacyEnvironmentConflict(legacyUnprefixedEnvironmentOverrides, environmentProvider)
        }

        if (commonValue != null) {
            return ResolvedKenvyValue(property, commonValue, ResolutionSource.COMMON_OVERRIDE)
                .orUnsafeLegacyEnvironmentConflict(legacyUnprefixedEnvironmentOverrides, environmentProvider)
        }

        return ResolvedKenvyValue(property, property.defaultValue, ResolutionSource.DEFAULT)
            .orUnsafeLegacyEnvironmentConflict(legacyUnprefixedEnvironmentOverrides, environmentProvider)
    }

    fun loadLocalProperties(localPropertiesFile: java.io.File): Map<String, String> {
        if (!localPropertiesFile.exists()) return emptyMap()
        val props = java.util.Properties()
        localPropertiesFile.inputStream().use { props.load(it) }
        return props.entries.associate { (k, v) -> k.toString() to v.toString() }
    }

    fun loadAndMergeLocalProperties(files: List<java.io.File>): Map<String, String> {
        val merged = mutableMapOf<String, String>()
        for (file in files) {
            merged.putAll(loadLocalProperties(file))
        }
        return merged
    }
}

private fun hasEnvironmentOrLocalOverride(
    property: KenvyProperty,
    platform: String?,
    variant: String?,
    localProperties: Map<String, String>,
    legacyUnprefixedEnvironmentOverrides: Boolean,
    environmentProvider: (String) -> String?
): Boolean {
    val envValue = resolveEnvironmentValue(
        property = property,
        platform = platform,
        variant = variant,
        legacyUnprefixedEnvironmentOverrides = legacyUnprefixedEnvironmentOverrides,
        environmentProvider = environmentProvider
    )
    if (envValue != null) return true

    return resolveScopedLocalValue(
        propertyName = property.name,
        platform = platform,
        variant = variant,
        localProperties = localProperties
    ) != null
}

private fun String?.normalizedOrNull(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }

private fun resolveScopedLocalValue(
    propertyName: String,
    platform: String?,
    variant: String?,
    localProperties: Map<String, String>
): String? {
    val normalizedPlatform = platform.normalizedOrNull()
    val normalizedVariant = variant.normalizedOrNull()
    val candidateKeys = buildList {
        add(propertyName)
        if (normalizedPlatform != null) {
            add("$propertyName.$normalizedPlatform")
            if (normalizedVariant != null) {
                add("$propertyName.$normalizedPlatform.$normalizedVariant")
            }
        }
    }

    return candidateKeys
        .asReversed()
        .firstNotNullOfOrNull { key ->
            localProperties[key]?.takeIf { it.isNotBlank() }
        }
}

private fun resolveEnvironmentValue(
    property: KenvyProperty,
    platform: String?,
    variant: String?,
    legacyUnprefixedEnvironmentOverrides: Boolean,
    environmentProvider: (String) -> String?
): String? {
    val candidates = KenvyResolver.toScopedEnvVarNames(property.name, platform, variant)
    // Last non-blank wins (higher specificity wins); blank more-specific does not mask non-blank generic
    val safeEnvValue = candidates
        .mapNotNull { name -> environmentProvider(name)?.takeIf { it.isNotBlank() } }
        .lastOrNull()
    if (safeEnvValue != null) return safeEnvValue

    if (!legacyUnprefixedEnvironmentOverrides) return null
    return environmentProvider(KenvyResolver.toLegacyEnvVarName(property.name))?.takeIf { it.isNotBlank() }
}

private fun unsafeLegacyEnvironmentIssue(
    property: KenvyProperty,
    environmentProvider: (String) -> String?
): KenvyConfigurationIssue? {
    val safeEnvName = KenvyResolver.toEnvVarName(property.name)
    if (environmentProvider(safeEnvName)?.isNotBlank() == true) return null

    val legacyEnvName = KenvyResolver.toLegacyEnvVarName(property.name)
    if (legacyEnvName !in KNOWN_UNSAFE_LEGACY_ENVIRONMENT_NAMES) return null
    if (environmentProvider(legacyEnvName)?.isNotBlank() != true) return null

    return KenvyConfigurationIssue.resolutionConflict(
        summary = "Property '${property.name}' is unresolved and maps to unsafe environment variable '$legacyEnvName', which is commonly provided by the build system.",
        details = listOf(
            "Kenvy ignored '$legacyEnvName' because unprefixed environment overrides are disabled by default.",
            "Use '$safeEnvName' for an environment override, or provide a safe local.properties, TOML override, external provider, or non-placeholder default value.",
            "Or enable kenvy { legacyUnprefixedEnvironmentOverrides.set(true) } to opt into unprefixed environment overrides explicitly.",
            "Resolution chain: $KENVY_RESOLUTION_CHAIN"
        )
    )
}

private fun ResolvedKenvyValue.isMissingPlaceholderValue(): Boolean =
    property.isPlaceholder && PlaceholderDetector.isPlaceholderValue(resolvedValue)

private fun ResolvedKenvyValue.orUnsafeLegacyEnvironmentConflict(
    legacyUnprefixedEnvironmentOverrides: Boolean,
    environmentProvider: (String) -> String?
): ResolvedKenvyValue {
    if (!legacyUnprefixedEnvironmentOverrides && isMissingPlaceholderValue()) {
        unsafeLegacyEnvironmentIssue(property, environmentProvider)?.let { issue ->
            throw KenvyConfigurationConflictException(issue)
        }
    }
    return this
}

internal val KNOWN_UNSAFE_LEGACY_ENVIRONMENT_NAMES = setOf(
    "PLATFORM_NAME",
    "CONFIGURATION",
    "SDKROOT",
    "ARCHS",
    "ACTION",
    "PRODUCT_NAME",
    "PROJECT_NAME",
    "TARGET_NAME",
    "JAVA_HOME",
    "GRADLE_USER_HOME",
    "CI",
    "HOME"
)
