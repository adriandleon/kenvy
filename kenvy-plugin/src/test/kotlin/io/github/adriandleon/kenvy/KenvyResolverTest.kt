package io.github.adriandleon.kenvy

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class KenvyResolverTest {

    private val apiKeyProp = KenvyProperty("api_key", PropertyType.STRING, "placeholder")
    private val countProp = KenvyProperty("retry_count", PropertyType.INT, "3")
    private val hyphenProp = KenvyProperty("api-key", PropertyType.STRING, "default-value")
    private val platformNameProp = KenvyProperty("platform_name", PropertyType.STRING, "placeholder")

    private fun resolve(
        prop: KenvyProperty,
        commonOverrides: Map<String, String> = emptyMap(),
        platformOverrides: Map<String, String> = emptyMap(),
        variantOverrides: Map<String, String> = emptyMap(),
        externalProviderValues: Map<String, String> = emptyMap(),
        externalProviderBacked: Boolean = false,
        platform: String? = null,
        variant: String? = null,
        localProps: Map<String, String> = emptyMap(),
        env: Map<String, String> = emptyMap(),
        legacyUnprefixedEnvironmentOverrides: Boolean = false
    ) = KenvyResolver.resolve(
        property = prop,
        commonOverrides = commonOverrides,
        platformOverrides = platformOverrides,
        variantOverrides = variantOverrides,
        externalProviderValues = externalProviderValues,
        externalProviderBacked = externalProviderBacked,
        platform = platform,
        variant = variant,
        localProperties = localProps,
        legacyUnprefixedEnvironmentOverrides = legacyUnprefixedEnvironmentOverrides
    ) { env[it] }

    @Test fun `default is used when no external source exists`() {
        val result = resolve(countProp)
        assertEquals("3", result.resolvedValue)
        assertEquals(ResolutionSource.DEFAULT, result.source)
    }

    @Test fun `local file value beats default`() {
        val result = resolve(apiKeyProp, localProps = mapOf("api_key" to "local-secret"))
        assertEquals("local-secret", result.resolvedValue)
        assertEquals(ResolutionSource.LOCAL_PROPERTIES, result.source)
    }

    @Test fun `environment value beats default`() {
        val result = resolve(apiKeyProp, env = mapOf("KENVY_API_KEY" to "env-secret"))
        assertEquals("env-secret", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
    }

    @Test fun `unprefixed environment value is ignored by default`() {
        val result = resolve(apiKeyProp, env = mapOf("API_KEY" to "env-secret"))
        assertEquals("placeholder", result.resolvedValue)
        assertEquals(ResolutionSource.DEFAULT, result.source)
    }

    @Test fun `legacy opt in consumes unprefixed environment value intentionally`() {
        val result = resolve(
            apiKeyProp,
            env = mapOf("API_KEY" to "env-secret"),
            legacyUnprefixedEnvironmentOverrides = true
        )
        assertEquals("env-secret", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
    }

    @Test fun `safe prefixed environment value wins over legacy value when opt in is enabled`() {
        val result = resolve(
            apiKeyProp,
            env = mapOf(
                "API_KEY" to "legacy-env-secret",
                "KENVY_API_KEY" to "safe-env-secret"
            ),
            legacyUnprefixedEnvironmentOverrides = true
        )

        assertEquals("safe-env-secret", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
    }

    @Test fun `environment value beats local properties`() {
        val result = resolve(
            apiKeyProp,
            localProps = mapOf("api_key" to "local-value"),
            env = mapOf("KENVY_API_KEY" to "env-value")
        )
        assertEquals("env-value", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
    }

    @Test fun `common override beats default when no local or env value exists`() {
        val result = resolve(apiKeyProp, commonOverrides = mapOf("api_key" to "common-value"))
        assertEquals("common-value", result.resolvedValue)
        assertEquals(ResolutionSource.COMMON_OVERRIDE, result.source)
    }

    @Test fun `local properties beats common override`() {
        val result = resolve(
            apiKeyProp,
            commonOverrides = mapOf("api_key" to "common-value"),
            localProps = mapOf("api_key" to "local-value")
        )
        assertEquals("local-value", result.resolvedValue)
        assertEquals(ResolutionSource.LOCAL_PROPERTIES, result.source)
    }

    @Test fun `environment beats common override`() {
        val result = resolve(
            apiKeyProp,
            commonOverrides = mapOf("api_key" to "common-value"),
            env = mapOf("KENVY_API_KEY" to "env-value")
        )
        assertEquals("env-value", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
    }

    @Test fun `platform override beats common for matching platform`() {
        val result = resolve(
            apiKeyProp,
            commonOverrides = mapOf("api_key" to "common-value"),
            platformOverrides = mapOf("api_key" to "android-value")
        )
        assertEquals("android-value", result.resolvedValue)
        assertEquals(ResolutionSource.PLATFORM_OVERRIDE, result.source)
    }

    @Test fun `variant override beats platform and common for matching target`() {
        val result = resolve(
            apiKeyProp,
            commonOverrides = mapOf("api_key" to "common-value"),
            platformOverrides = mapOf("api_key" to "android-value"),
            variantOverrides = mapOf("api_key" to "android-debug-value")
        )
        assertEquals("android-debug-value", result.resolvedValue)
        assertEquals(ResolutionSource.VARIANT_OVERRIDE, result.source)
    }

    @Test fun `local properties beat platform and variant overrides`() {
        val result = resolve(
            apiKeyProp,
            commonOverrides = mapOf("api_key" to "common-value"),
            platformOverrides = mapOf("api_key" to "android-value"),
            variantOverrides = mapOf("api_key" to "android-debug-value"),
            localProps = mapOf("api_key" to "local-value")
        )
        assertEquals("local-value", result.resolvedValue)
        assertEquals(ResolutionSource.LOCAL_PROPERTIES, result.source)
    }

    @Test fun `platform scoped local properties beat unscoped local and contract overrides`() {
        val result = resolve(
            apiKeyProp,
            commonOverrides = mapOf("api_key" to "common-value"),
            platformOverrides = mapOf("api_key" to "android-value"),
            localProps = mapOf(
                "api_key" to "local-value",
                "api_key.android" to "android-local-value"
            ),
            platform = "android"
        )

        assertEquals("android-local-value", result.resolvedValue)
        assertEquals(ResolutionSource.LOCAL_PROPERTIES, result.source)
    }

    @Test fun `platform and variant scoped local properties beat platform scoped and unscoped local values`() {
        val result = resolve(
            apiKeyProp,
            platformOverrides = mapOf("api_key" to "android-value"),
            variantOverrides = mapOf("api_key" to "android-debug-value"),
            localProps = mapOf(
                "api_key" to "local-value",
                "api_key.android" to "android-local-value",
                "api_key.android.debug" to "android-debug-local-value"
            ),
            platform = "android",
            variant = "debug"
        )

        assertEquals("android-debug-local-value", result.resolvedValue)
        assertEquals(ResolutionSource.LOCAL_PROPERTIES, result.source)
    }

    @Test fun `environment beats platform and variant scoped local properties`() {
        val result = resolve(
            apiKeyProp,
            localProps = mapOf(
                "api_key" to "local-value",
                "api_key.android" to "android-local-value",
                "api_key.android.debug" to "android-debug-local-value"
            ),
            platform = "android",
            variant = "debug",
            env = mapOf("KENVY_API_KEY" to "env-value")
        )

        assertEquals("env-value", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
    }

    @Test fun `non matching scoped local property is ignored`() {
        val result = resolve(
            apiKeyProp,
            platformOverrides = mapOf("api_key" to "android-value"),
            variantOverrides = mapOf("api_key" to "android-debug-value"),
            localProps = mapOf("api_key.ios.debug" to "ios-debug-local-value"),
            platform = "android",
            variant = "debug"
        )

        assertEquals("android-debug-value", result.resolvedValue)
        assertEquals(ResolutionSource.VARIANT_OVERRIDE, result.source)
    }

    @Test fun `environment beats platform and variant overrides`() {
        val result = resolve(
            apiKeyProp,
            commonOverrides = mapOf("api_key" to "common-value"),
            platformOverrides = mapOf("api_key" to "android-value"),
            variantOverrides = mapOf("api_key" to "android-debug-value"),
            env = mapOf("KENVY_API_KEY" to "env-value")
        )
        assertEquals("env-value", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
    }

    @Test fun `snake_case property maps to uppercase env var`() {
        assertEquals("KENVY_API_KEY", KenvyResolver.toEnvVarName("api_key"))
    }

    @Test fun `hyphenated property maps to underscore env var`() {
        assertEquals("KENVY_API_KEY", KenvyResolver.toEnvVarName("api-key"))
    }

    @Test fun `non-alphanumeric sequences collapse to one underscore in env var`() {
        assertEquals("KENVY_API_KEY", KenvyResolver.toEnvVarName("api--key"))
    }

    @Test fun `property starting with digit maps correctly`() {
        assertEquals("KENVY_1_API_KEY", KenvyResolver.toEnvVarName("1/api-key"))
    }

    @Test fun `resolved value includes correct property reference`() {
        val result = resolve(apiKeyProp)
        assertEquals(apiKeyProp, result.property)
    }

    @Test fun `resolve all properties in contract`() {
        val contract = ParsedKenvyContract(
            properties = listOf(apiKeyProp, countProp),
            commonOverrides = mapOf("api_key" to "common-api")
        )
        val results = KenvyResolver.resolve(contract, emptyMap()) { null }
        assertEquals(2, results.size)
        val apiResult = results.first { it.property.name == "api_key" }
        assertEquals("common-api", apiResult.resolvedValue)
        assertEquals(ResolutionSource.COMMON_OVERRIDE, apiResult.source)
        val countResult = results.first { it.property.name == "retry_count" }
        assertEquals("3", countResult.resolvedValue)
        assertEquals(ResolutionSource.DEFAULT, countResult.source)
    }

    @Test fun `matching platform override beats common while non matching platform keeps common`() {
        val contract = ParsedKenvyContract(
            properties = listOf(apiKeyProp),
            commonOverrides = mapOf("api_key" to "common-api"),
            platformOverrides = mapOf("android" to mapOf("api_key" to "android-api"))
        )

        val androidResult = KenvyResolver.resolve(
            contract = contract,
            platform = "android",
            localProperties = emptyMap()
        ) { null }.single()
        val iosResult = KenvyResolver.resolve(
            contract = contract,
            platform = "ios",
            localProperties = emptyMap()
        ) { null }.single()

        assertEquals("android-api", androidResult.resolvedValue)
        assertEquals(ResolutionSource.PLATFORM_OVERRIDE, androidResult.source)
        assertEquals("common-api", iosResult.resolvedValue)
        assertEquals(ResolutionSource.COMMON_OVERRIDE, iosResult.source)
    }

    @Test fun `matching variant override beats platform and common while non matching variant keeps platform`() {
        val contract = ParsedKenvyContract(
            properties = listOf(apiKeyProp),
            commonOverrides = mapOf("api_key" to "common-api"),
            platformOverrides = mapOf("android" to mapOf("api_key" to "android-api")),
            variantOverrides = mapOf(
                "android" to mapOf(
                    "debug" to mapOf("api_key" to "android-debug-api")
                )
            )
        )

        val debugResult = KenvyResolver.resolve(
            contract = contract,
            platform = "android",
            variant = "debug",
            localProperties = emptyMap()
        ) { null }.single()
        val releaseResult = KenvyResolver.resolve(
            contract = contract,
            platform = "android",
            variant = "release",
            localProperties = emptyMap()
        ) { null }.single()

        assertEquals("android-debug-api", debugResult.resolvedValue)
        assertEquals(ResolutionSource.VARIANT_OVERRIDE, debugResult.source)
        assertEquals("android-api", releaseResult.resolvedValue)
        assertEquals(ResolutionSource.PLATFORM_OVERRIDE, releaseResult.source)
    }

    @Test fun `hyphenated property name resolved from env with correct mapping`() {
        val result = resolve(hyphenProp, env = mapOf("KENVY_API_KEY" to "hyphen-env-val"))
        assertEquals("hyphen-env-val", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
    }

    @Test fun `blank env value does not override default`() {
        val result = resolve(apiKeyProp, env = mapOf("KENVY_API_KEY" to ""))
        assertEquals("placeholder", result.resolvedValue)
        assertEquals(ResolutionSource.DEFAULT, result.source)
    }

    @Test fun `blank unprefixed env value does not trigger collision failure`() {
        val result = resolve(platformNameProp, env = mapOf("PLATFORM_NAME" to "   "))
        assertEquals("placeholder", result.resolvedValue)
        assertEquals(ResolutionSource.DEFAULT, result.source)
    }

    @Test fun `blank local value does not override default`() {
        val result = resolve(apiKeyProp, localProps = mapOf("api_key" to "   "))
        assertEquals("placeholder", result.resolvedValue)
        assertEquals(ResolutionSource.DEFAULT, result.source)
    }

    @Test fun `blank scoped local value does not override default`() {
        val result = resolve(
            apiKeyProp,
            platform = "android",
            variant = "debug",
            localProps = mapOf("api_key.android.debug" to "   ")
        )

        assertEquals("placeholder", result.resolvedValue)
        assertEquals(ResolutionSource.DEFAULT, result.source)
    }

    @Test fun `environment name collision fails clearly`() {
        val contract = ParsedKenvyContract(
            properties = listOf(
                KenvyProperty("api_key", PropertyType.STRING, "a"),
                KenvyProperty("api-key", PropertyType.STRING, "b")
            )
        )

        val error = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
            KenvyResolver.resolve(contract, emptyMap()) { null }
        }

        kotlin.test.assertContains(error.message.orEmpty(), "same environment variable")
        kotlin.test.assertContains(error.message.orEmpty(), "KENVY_API_KEY")
        kotlin.test.assertContains(error.message.orEmpty(), "Resolution chain:")
    }

    @Test fun `scoped environment name collision fails for active platform`() {
        val contract = ParsedKenvyContract(
            properties = listOf(
                KenvyProperty("api_key", PropertyType.STRING, "a"),
                KenvyProperty("api_key_android", PropertyType.STRING, "b")
            )
        )

        val error = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
            KenvyResolver.resolve(
                contract = contract,
                platform = "android",
                localProperties = emptyMap()
            ) { null }
        }

        kotlin.test.assertContains(error.message.orEmpty(), "same environment variable")
        kotlin.test.assertContains(error.message.orEmpty(), "KENVY_API_KEY_ANDROID")
        kotlin.test.assertContains(error.message.orEmpty(), "api_key")
        kotlin.test.assertContains(error.message.orEmpty(), "api_key_android")
    }

    @Test fun `scoped environment name collision does not fail without matching scope`() {
        val contract = ParsedKenvyContract(
            properties = listOf(
                KenvyProperty("api_key", PropertyType.STRING, "a"),
                KenvyProperty("api_key_android", PropertyType.STRING, "b")
            )
        )

        val result = KenvyResolver.resolve(
            contract = contract,
            localProperties = emptyMap()
        ) { null }

        assertEquals(listOf("a", "b"), result.map { it.resolvedValue })
    }

    @Test fun `multiple environment name collisions are reported together`() {
        val contract = ParsedKenvyContract(
            properties = listOf(
                KenvyProperty("api_key", PropertyType.STRING, "a"),
                KenvyProperty("api-key", PropertyType.STRING, "b"),
                KenvyProperty("base_url", PropertyType.STRING, "c"),
                KenvyProperty("base-url", PropertyType.STRING, "d")
            )
        )

        val error = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
            KenvyResolver.resolve(contract, emptyMap()) { null }
        }

        kotlin.test.assertContains(error.message.orEmpty(), "Kenvy: Configuration has 2 issue(s).")
        kotlin.test.assertContains(error.message.orEmpty(), "KENVY_API_KEY")
        kotlin.test.assertContains(error.message.orEmpty(), "KENVY_BASE_URL")
    }

    @Test fun `known build system collision fails with actionable diagnostic`() {
        val error = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
            resolve(platformNameProp, env = mapOf("PLATFORM_NAME" to "iphonesimulator"))
        }

        kotlin.test.assertContains(error.message.orEmpty(), "unresolved")
        kotlin.test.assertContains(error.message.orEmpty(), "platform_name")
        kotlin.test.assertContains(error.message.orEmpty(), "PLATFORM_NAME")
        kotlin.test.assertContains(error.message.orEmpty(), "KENVY_PLATFORM_NAME")
        kotlin.test.assertContains(error.message.orEmpty(), "legacyUnprefixedEnvironmentOverrides")
    }

    @Test fun `known build system collision does not fail when local properties resolve value`() {
        val result = resolve(
            platformNameProp,
            localProps = mapOf("platform_name" to "local-ios"),
            env = mapOf("PLATFORM_NAME" to "iphonesimulator")
        )

        assertEquals("local-ios", result.resolvedValue)
        assertEquals(ResolutionSource.LOCAL_PROPERTIES, result.source)
    }

    @Test fun `safe prefixed environment value wins without collision failure`() {
        val result = resolve(
            platformNameProp,
            env = mapOf(
                "PLATFORM_NAME" to "iphonesimulator",
                "KENVY_PLATFORM_NAME" to "consumer-ios"
            )
        )

        assertEquals("consumer-ios", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
    }

    @Test fun `provider backed property uses provider value after env and local overrides`() {
        val result = resolve(
            apiKeyProp,
            commonOverrides = mapOf("api_key" to "common-value"),
            platformOverrides = mapOf("api_key" to "platform-value"),
            variantOverrides = mapOf("api_key" to "variant-value"),
            externalProviderValues = mapOf("api_key" to "provider-value"),
            externalProviderBacked = true
        )

        assertEquals("provider-value", result.resolvedValue)
        assertEquals(ResolutionSource.EXTERNAL_PROVIDER, result.source)
    }

    @Test fun `environment and local properties remain emergency overrides for provider backed property`() {
        val envResult = resolve(
            apiKeyProp,
            commonOverrides = mapOf("api_key" to "common-value"),
            externalProviderValues = mapOf("api_key" to "provider-value"),
            externalProviderBacked = true,
            env = mapOf("KENVY_API_KEY" to "env-value")
        )
        val localResult = resolve(
            apiKeyProp,
            commonOverrides = mapOf("api_key" to "common-value"),
            externalProviderValues = mapOf("api_key" to "provider-value"),
            externalProviderBacked = true,
            localProps = mapOf("api_key" to "local-value")
        )

        assertEquals("env-value", envResult.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, envResult.source)
        assertEquals("local-value", localResult.resolvedValue)
        assertEquals(ResolutionSource.LOCAL_PROPERTIES, localResult.source)
    }

    @Test fun `contract resolve skips provider timeout machinery when there are no provider requests`() {
        var gateCreated = false
        var providerCalled = false
        val contract = ParsedKenvyContract(
            properties = listOf(apiKeyProp),
            commonOverrides = mapOf("api_key" to "common-value")
        )

        val result = KenvyResolver.resolve(
            contract = contract,
            localProperties = emptyMap(),
            externalProviderResolver = KenvyExternalProviderResolver {
                providerCalled = true
                mapOf("api_key" to "provider-value")
            },
            externalProviderTimeout = Duration.ofMillis(50),
            timeoutGateFactory = { timeout ->
                gateCreated = true
                KenvyExternalProviderTimeoutGate(timeout)
            }
        ) { null }.single()

        assertEquals("common-value", result.resolvedValue)
        assertEquals(ResolutionSource.COMMON_OVERRIDE, result.source)
        assertFalse(gateCreated)
        assertFalse(providerCalled)
    }

    @Test fun `contract resolve does not call provider for environment overridden provider backed property`() {
        var gateCreated = false
        var providerCalled = false
        val contract = ParsedKenvyContract(
            properties = listOf(apiKeyProp),
            externalProviderRequests = listOf(KenvyExternalProviderRequest("api_key", "ci-vault"))
        )

        val result = KenvyResolver.resolve(
            contract = contract,
            localProperties = emptyMap(),
            externalProviderResolver = KenvyExternalProviderResolver {
                providerCalled = true
                mapOf("api_key" to "provider-value")
            },
            externalProviderTimeout = Duration.ofMillis(50),
            timeoutGateFactory = { timeout ->
                gateCreated = true
                KenvyExternalProviderTimeoutGate(timeout)
            }
        ) { envName ->
            if (envName == "KENVY_API_KEY") "env-value" else null
        }.single()

        assertEquals("env-value", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
        assertFalse(gateCreated)
        assertFalse(providerCalled)
    }

    @Test fun `contract resolve does not call provider for local overridden provider backed property`() {
        var gateCreated = false
        var providerCalled = false
        val contract = ParsedKenvyContract(
            properties = listOf(apiKeyProp),
            externalProviderRequests = listOf(KenvyExternalProviderRequest("api_key", "ci-vault"))
        )

        val result = KenvyResolver.resolve(
            contract = contract,
            localProperties = mapOf("api_key" to "local-value"),
            externalProviderResolver = KenvyExternalProviderResolver {
                providerCalled = true
                mapOf("api_key" to "provider-value")
            },
            externalProviderTimeout = Duration.ofMillis(50),
            timeoutGateFactory = { timeout ->
                gateCreated = true
                KenvyExternalProviderTimeoutGate(timeout)
            }
        ) { null }.single()

        assertEquals("local-value", result.resolvedValue)
        assertEquals(ResolutionSource.LOCAL_PROPERTIES, result.source)
        assertFalse(gateCreated)
        assertFalse(providerCalled)
    }

    @Test fun `contract resolve does not call provider for scoped local overridden provider backed property`() {
        var gateCreated = false
        var providerCalled = false
        val contract = ParsedKenvyContract(
            properties = listOf(apiKeyProp),
            externalProviderRequests = listOf(KenvyExternalProviderRequest("api_key", "ci-vault"))
        )

        val result = KenvyResolver.resolve(
            contract = contract,
            platform = "android",
            variant = "debug",
            localProperties = mapOf("api_key.android.debug" to "local-value"),
            externalProviderResolver = KenvyExternalProviderResolver {
                providerCalled = true
                mapOf("api_key" to "provider-value")
            },
            externalProviderTimeout = Duration.ofMillis(50),
            timeoutGateFactory = { timeout ->
                gateCreated = true
                KenvyExternalProviderTimeoutGate(timeout)
            }
        ) { null }.single()

        assertEquals("local-value", result.resolvedValue)
        assertEquals(ResolutionSource.LOCAL_PROPERTIES, result.source)
        assertFalse(gateCreated)
        assertFalse(providerCalled)
    }

    @Test fun `provider backed blank value is resolved for downstream validation`() {
        val result = resolve(
            apiKeyProp,
            commonOverrides = mapOf("api_key" to "common-value"),
            externalProviderValues = mapOf("api_key" to ""),
            externalProviderBacked = true
        )

        assertEquals("", result.resolvedValue)
        assertEquals(ResolutionSource.EXTERNAL_PROVIDER, result.source)
    }

    // Group A — toScopedEnvVarNames output

    @Test fun `toScopedEnvVarNames returns only generic when no platform given`() {
        assertEquals(
            listOf("KENVY_API_KEY"),
            KenvyResolver.toScopedEnvVarNames("api_key", null, null)
        )
    }

    @Test fun `toScopedEnvVarNames returns generic and platform when only platform is given`() {
        assertEquals(
            listOf("KENVY_API_KEY", "KENVY_API_KEY_ANDROID"),
            KenvyResolver.toScopedEnvVarNames("api_key", "android", null)
        )
    }

    @Test fun `toScopedEnvVarNames returns all three when platform and variant are given`() {
        assertEquals(
            listOf("KENVY_API_KEY", "KENVY_API_KEY_ANDROID", "KENVY_API_KEY_ANDROID_DEBUG"),
            KenvyResolver.toScopedEnvVarNames("api_key", "android", "debug")
        )
    }

    @Test fun `toScopedEnvVarNames normalizes platform and variant segments`() {
        assertEquals(
            listOf("KENVY_BASE_URL", "KENVY_BASE_URL_IOS", "KENVY_BASE_URL_IOS_RELEASE"),
            KenvyResolver.toScopedEnvVarNames("base-url", "ios", "release")
        )
    }

    @Test fun `toScopedEnvVarNames ignores blank platform`() {
        assertEquals(
            listOf("KENVY_API_KEY"),
            KenvyResolver.toScopedEnvVarNames("api_key", "  ", "debug")
        )
    }

    // Group B — precedence tests

    @Test fun `platform scoped env beats generic env`() {
        val result = resolve(
            apiKeyProp,
            platform = "android",
            env = mapOf(
                "KENVY_API_KEY" to "generic-value",
                "KENVY_API_KEY_ANDROID" to "android-value"
            )
        )
        assertEquals("android-value", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
    }

    @Test fun `platform+variant scoped env beats platform env and generic env`() {
        val result = resolve(
            apiKeyProp,
            platform = "android",
            variant = "debug",
            env = mapOf(
                "KENVY_API_KEY" to "generic-value",
                "KENVY_API_KEY_ANDROID" to "android-value",
                "KENVY_API_KEY_ANDROID_DEBUG" to "android-debug-value"
            )
        )
        assertEquals("android-debug-value", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
    }

    @Test fun `platform env beats generic env when variant env is absent`() {
        val result = resolve(
            apiKeyProp,
            platform = "android",
            variant = "debug",
            env = mapOf(
                "KENVY_API_KEY" to "generic-value",
                "KENVY_API_KEY_ANDROID" to "android-value"
            )
        )
        assertEquals("android-value", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
    }

    @Test fun `blank platform scoped env does not suppress nonblank generic env`() {
        val result = resolve(
            apiKeyProp,
            platform = "android",
            env = mapOf(
                "KENVY_API_KEY" to "generic-value",
                "KENVY_API_KEY_ANDROID" to ""
            )
        )
        assertEquals("generic-value", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
    }

    @Test fun `blank variant scoped env does not suppress nonblank platform or generic env`() {
        val result = resolve(
            apiKeyProp,
            platform = "android",
            variant = "debug",
            env = mapOf(
                "KENVY_API_KEY" to "generic-value",
                "KENVY_API_KEY_ANDROID" to "android-value",
                "KENVY_API_KEY_ANDROID_DEBUG" to ""
            )
        )
        assertEquals("android-value", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
    }

    @Test fun `scoped env value beats scoped local properties`() {
        val result = resolve(
            apiKeyProp,
            platform = "android",
            variant = "debug",
            localProps = mapOf(
                "api_key" to "local-generic",
                "api_key.android" to "local-android",
                "api_key.android.debug" to "local-android-debug"
            ),
            env = mapOf("KENVY_API_KEY_ANDROID" to "android-env-value")
        )
        assertEquals("android-env-value", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
    }

    @Test fun `generic env fallback when no scoped env matches current platform`() {
        val result = resolve(
            apiKeyProp,
            platform = "ios",
            variant = "debug",
            env = mapOf("KENVY_API_KEY" to "generic-env-value")
        )
        assertEquals("generic-env-value", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
    }

    // Group C — provider short-circuit with scoped env

    @Test fun `contract resolve does not call provider when scoped env overrides provider backed property`() {
        var providerCalled = false
        val contract = ParsedKenvyContract(
            properties = listOf(apiKeyProp),
            externalProviderRequests = listOf(KenvyExternalProviderRequest("api_key", "ci-vault"))
        )

        val result = KenvyResolver.resolve(
            contract = contract,
            platform = "android",
            variant = "debug",
            localProperties = emptyMap(),
            externalProviderResolver = KenvyExternalProviderResolver {
                providerCalled = true
                mapOf("api_key" to "provider-value")
            },
            externalProviderTimeout = Duration.ofMillis(50),
            timeoutGateFactory = { timeout -> KenvyExternalProviderTimeoutGate(timeout) }
        ) { envName ->
            if (envName == "KENVY_API_KEY_ANDROID_DEBUG") "scoped-env-value" else null
        }.single()

        assertEquals("scoped-env-value", result.resolvedValue)
        assertEquals(ResolutionSource.ENVIRONMENT, result.source)
        assertFalse(providerCalled)
    }
}
