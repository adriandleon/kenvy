package io.github.adriandleon.kenvy

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

abstract class KenvyExtension {
    /**
     * The path to the kenvy.toml file.
     * Default: kenvy.toml in project root.
     */
    abstract val configFile: RegularFileProperty

    /**
     * Package name for the generated code.
     */
    abstract val packageName: Property<String>

    /**
     * Interface name for the generated code.
     * Default: AppConfig
     */
    abstract val interfaceName: Property<String>

    /**
     * Optional platform key used when resolving platform-scoped overrides.
     */
    abstract val platform: Property<String>

    /**
     * Optional build variant key used when resolving variant-scoped overrides.
     */
    abstract val variant: Property<String>

    /**
     * Allows Gradle build-cache storage for generated sources that may contain
     * values from local.properties or environment variables.
     * Default: false.
     */
    abstract val cacheGeneratedOutput: Property<Boolean>

    /**
     * Allows the legacy unprefixed environment variable convention, such as
     * API_KEY, to participate in resolution. Default: false.
     */
    abstract val legacyUnprefixedEnvironmentOverrides: Property<Boolean>

    /**
     * Controls how generated Kotlin property names are emitted.
     * Default: lower-camel.
     */
    abstract val generatedPropertyNameStyle: Property<String>

    /**
     * Controls the visibility modifier on the generated object and its members.
     * Accepted values: "public" (default), "internal".
     * Use "internal" for API-surface-sensitive shared modules.
     */
    abstract val generatedVisibility: Property<String>

    /**
     * Local properties files consulted during resolution, in order. Later files override earlier
     * files for the same key. Defaults to root `local.properties` followed by module
     * `local.properties` (root only for single-project builds).
     *
     * - `from(...)` appends to the current collection (adds to the defaults).
     * - `setFrom(...)` replaces the entire collection.
     *
     * Direct task-level configuration via `GenerateKenvyTask.localPropertiesFiles` is still
     * supported for advanced use cases and tests.
     */
    abstract val localPropertiesFiles: ConfigurableFileCollection
}
