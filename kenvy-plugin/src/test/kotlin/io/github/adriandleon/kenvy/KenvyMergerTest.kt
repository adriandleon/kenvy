package io.github.adriandleon.kenvy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertContains
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class KenvyMergerTest {

    private val apiKeyProp = KenvyProperty("api_key", PropertyType.STRING, "placeholder")
    private val retryProp = KenvyProperty("retry_count", PropertyType.INT, "3")
    private val baseProp = KenvyProperty("base_url", PropertyType.STRING, "https://default.example.com")

    private fun simpleContract(vararg props: KenvyProperty) =
        ParsedKenvyContract(properties = listOf(*props))

    private fun contractWithCommon(vararg props: KenvyProperty, overrides: Map<String, String>) =
        ParsedKenvyContract(
            properties = listOf(*props),
            commonOverrides = overrides
        )

    // AC1, AC5 — local-only keys survive a merge round-trip
    @Test fun `local-only keys survive merge when not in contract`() {
        val contract = simpleContract(apiKeyProp)
        val existing = mapOf("api_key" to "local-secret", "CUSTOM_KEY" to "custom-value")

        val result = KenvyMerger.mergeLocalProperties(
            contract = contract,
            platform = null,
            variant = null,
            existingLocalProperties = existing
        )

        assertEquals("custom-value", result.mergedProperties["CUSTOM_KEY"])
        assertContains(result.preservedLocalOnlyKeys, "CUSTOM_KEY")
    }

    @Test fun `multiple local-only keys all survive merge`() {
        val contract = simpleContract(apiKeyProp)
        val existing = mapOf(
            "api_key" to "local-secret",
            "MY_TOKEN" to "token-value",
            "ANOTHER_KEY" to "another-value"
        )

        val result = KenvyMerger.mergeLocalProperties(
            contract = contract,
            platform = null,
            variant = null,
            existingLocalProperties = existing
        )

        assertEquals("token-value", result.mergedProperties["MY_TOKEN"])
        assertEquals("another-value", result.mergedProperties["ANOTHER_KEY"])
        assertEquals(setOf("MY_TOKEN", "ANOTHER_KEY"), result.preservedLocalOnlyKeys)
    }

    // AC2 — new contract property with default is added when absent locally
    @Test fun `new contract property with default is added to merged view when absent locally`() {
        val contract = simpleContract(apiKeyProp, retryProp)
        val existing = mapOf("api_key" to "local-secret")

        val result = KenvyMerger.mergeLocalProperties(
            contract = contract,
            platform = null,
            variant = null,
            existingLocalProperties = existing
        )

        assertEquals("local-secret", result.mergedProperties["api_key"])
        assertEquals("3", result.mergedProperties["retry_count"])
    }

    @Test fun `existing local overrides remain untouched when new property is added`() {
        val contract = simpleContract(apiKeyProp, retryProp)
        val existing = mapOf("api_key" to "my-local-key")

        val result = KenvyMerger.mergeLocalProperties(
            contract = contract,
            platform = null,
            variant = null,
            existingLocalProperties = existing
        )

        assertEquals("my-local-key", result.mergedProperties["api_key"])
        assertTrue(result.overwrittenKeys.isEmpty())
    }

    // AC3 — local values win conflicts by default
    @Test fun `local value beats contract default by default`() {
        val contract = simpleContract(apiKeyProp)
        val existing = mapOf("api_key" to "my-local-value")

        val result = KenvyMerger.mergeLocalProperties(
            contract = contract,
            platform = null,
            variant = null,
            existingLocalProperties = existing
        )

        assertEquals("my-local-value", result.mergedProperties["api_key"])
        assertTrue(result.overwrittenKeys.isEmpty())
    }

    @Test fun `local value beats common override by default`() {
        val contract = contractWithCommon(apiKeyProp, overrides = mapOf("api_key" to "common-value"))
        val existing = mapOf("api_key" to "my-local-value")

        val result = KenvyMerger.mergeLocalProperties(
            contract = contract,
            platform = null,
            variant = null,
            existingLocalProperties = existing
        )

        assertEquals("my-local-value", result.mergedProperties["api_key"])
        assertTrue(result.overwrittenKeys.isEmpty())
    }

    @Test fun `local value beats platform override by default`() {
        val contract = ParsedKenvyContract(
            properties = listOf(baseProp),
            platformOverrides = mapOf("android" to mapOf("base_url" to "https://android.example.com"))
        )
        val existing = mapOf("base_url" to "https://local.example.com")

        val result = KenvyMerger.mergeLocalProperties(
            contract = contract,
            platform = "android",
            variant = null,
            existingLocalProperties = existing
        )

        assertEquals("https://local.example.com", result.mergedProperties["base_url"])
        assertTrue(result.overwrittenKeys.isEmpty())
    }

    @Test fun `local value beats variant override by default`() {
        val contract = ParsedKenvyContract(
            properties = listOf(baseProp),
            variantOverrides = mapOf("android" to mapOf("debug" to mapOf("base_url" to "https://android-debug.example.com")))
        )
        val existing = mapOf("base_url" to "https://local.example.com")

        val result = KenvyMerger.mergeLocalProperties(
            contract = contract,
            platform = "android",
            variant = "debug",
            existingLocalProperties = existing
        )

        assertEquals("https://local.example.com", result.mergedProperties["base_url"])
        assertTrue(result.overwrittenKeys.isEmpty())
    }

    // AC4 — force overwrites only explicitly selected keys
    @Test fun `force overwrites only explicitly selected key`() {
        val contract = simpleContract(apiKeyProp, retryProp)
        val existing = mapOf("api_key" to "local-value", "retry_count" to "10")

        val result = KenvyMerger.mergeLocalProperties(
            contract = contract,
            platform = null,
            variant = null,
            existingLocalProperties = existing,
            forceKeys = setOf("api_key")
        )

        assertEquals("placeholder", result.mergedProperties["api_key"])
        assertEquals("10", result.mergedProperties["retry_count"])
        assertEquals(setOf("api_key"), result.overwrittenKeys)
        assertEquals(mapOf("api_key" to ResolutionSource.DEFAULT), result.overwrittenSources)
    }

    @Test fun `force does not overwrite unselected keys`() {
        val contract = simpleContract(apiKeyProp, retryProp)
        val existing = mapOf("api_key" to "local-api", "retry_count" to "99")

        val result = KenvyMerger.mergeLocalProperties(
            contract = contract,
            platform = null,
            variant = null,
            existingLocalProperties = existing,
            forceKeys = setOf("retry_count")
        )

        assertEquals("local-api", result.mergedProperties["api_key"])
        assertEquals("3", result.mergedProperties["retry_count"])
        assertEquals(setOf("retry_count"), result.overwrittenKeys)
        assertEquals(mapOf("retry_count" to ResolutionSource.DEFAULT), result.overwrittenSources)
    }

    @Test fun `force overwrite source tracks contract override provenance`() {
        val contract = ParsedKenvyContract(
            properties = listOf(baseProp),
            variantOverrides = mapOf("android" to mapOf("debug" to mapOf("base_url" to "https://debug.example.com")))
        )

        val result = KenvyMerger.mergeLocalProperties(
            contract = contract,
            platform = "android",
            variant = "debug",
            existingLocalProperties = mapOf("base_url" to "https://local.example.com"),
            forceKeys = setOf("base_url")
        )

        assertEquals(setOf("base_url"), result.overwrittenKeys)
        assertEquals(mapOf("base_url" to ResolutionSource.VARIANT_OVERRIDE), result.overwrittenSources)
    }

    // Unknown force keys fail clearly
    @Test fun `unknown force key fails clearly`() {
        val contract = simpleContract(apiKeyProp)

        val error = assertFailsWith<org.gradle.api.GradleException> {
            KenvyMerger.mergeLocalProperties(
                contract = contract,
                platform = null,
                variant = null,
                existingLocalProperties = emptyMap(),
                forceKeys = setOf("unknown_prop")
            )
        }

        assertContains(error.message.orEmpty(), "unknown_prop")
        assertContains(error.message.orEmpty(), "not declared in the contract")
    }

    @Test fun `multiple unknown force keys listed in error`() {
        val contract = simpleContract(apiKeyProp)

        val error = assertFailsWith<org.gradle.api.GradleException> {
            KenvyMerger.mergeLocalProperties(
                contract = contract,
                platform = null,
                variant = null,
                existingLocalProperties = emptyMap(),
                forceKeys = setOf("bad_key_1", "bad_key_2")
            )
        }

        assertContains(error.message.orEmpty(), "bad_key_1")
        assertContains(error.message.orEmpty(), "bad_key_2")
    }

    // Warnings — overwrittenKeys contains names but merge result never exposes values
    @Test fun `overwrittenKeys contains property names not values`() {
        val contract = simpleContract(apiKeyProp)
        val existing = mapOf("api_key" to "super-secret-value")

        val result = KenvyMerger.mergeLocalProperties(
            contract = contract,
            platform = null,
            variant = null,
            existingLocalProperties = existing,
            forceKeys = setOf("api_key")
        )

        assertTrue("api_key" in result.overwrittenKeys)
        assertFalse("super-secret-value" in result.overwrittenKeys)
    }

    // Environment variables must not appear in merged output
    @Test fun `environment values are not injected into merged properties`() {
        // mergeLocalProperties has no env provider parameter — env stays out by design
        val contract = simpleContract(apiKeyProp)
        val existing = emptyMap<String, String>()

        val result = KenvyMerger.mergeLocalProperties(
            contract = contract,
            platform = null,
            variant = null,
            existingLocalProperties = existing
        )

        // Only contract-derived (default) value should appear, not any env value
        assertEquals("placeholder", result.mergedProperties["api_key"])
    }

    @Test fun `empty local properties merges all contract defaults`() {
        val contract = simpleContract(apiKeyProp, retryProp)

        val result = KenvyMerger.mergeLocalProperties(
            contract = contract,
            platform = null,
            variant = null,
            existingLocalProperties = emptyMap()
        )

        assertEquals("placeholder", result.mergedProperties["api_key"])
        assertEquals("3", result.mergedProperties["retry_count"])
        assertTrue(result.overwrittenKeys.isEmpty())
        assertTrue(result.preservedLocalOnlyKeys.isEmpty())
    }

    @Test fun `force on key equal to current value does not add to overwrittenKeys`() {
        val contract = simpleContract(retryProp)
        val existing = mapOf("retry_count" to "3")

        val result = KenvyMerger.mergeLocalProperties(
            contract = contract,
            platform = null,
            variant = null,
            existingLocalProperties = existing,
            forceKeys = setOf("retry_count")
        )

        assertEquals("3", result.mergedProperties["retry_count"])
        assertTrue(result.overwrittenKeys.isEmpty())
    }
}
