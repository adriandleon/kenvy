package io.github.adriandleon.kenvy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import org.gradle.api.GradleException

class KenvyMaskingTest {

    // ---- Task 1 / Task 5: sensitive metadata on KenvyProperty ----

    @Test fun `sensitive defaults to false`() {
        val prop = KenvyProperty("api_key", PropertyType.STRING, "placeholder")
        assertFalse(prop.sensitive)
    }

    @Test fun `sensitive can be set to true`() {
        val prop = KenvyProperty("api_key", PropertyType.STRING, "placeholder", sensitive = true)
        assertTrue(prop.sensitive)
    }

    @Test fun `non-sensitive property is not masked`() {
        val prop = KenvyProperty("base_url", PropertyType.STRING, "https://api.example.com", sensitive = false)
        val resolved = ResolvedKenvyValue(prop, "https://api.example.com", ResolutionSource.DEFAULT)
        assertEquals("https://api.example.com", resolved.displayValue())
    }

    @Test fun `sensitive resolved value is masked as four stars`() {
        val prop = KenvyProperty("api_key", PropertyType.STRING, "placeholder", sensitive = true)
        val resolved = ResolvedKenvyValue(prop, "SUPER_SECRET_12345_DO_NOT_LOG", ResolutionSource.LOCAL_PROPERTIES)
        assertEquals("****", resolved.displayValue())
    }

    @Test fun `sensitive value masked regardless of resolution source`() {
        val prop = KenvyProperty("api_key", PropertyType.STRING, "placeholder", sensitive = true)
        for (source in ResolutionSource.entries) {
            val resolved = ResolvedKenvyValue(prop, "secret_value", source)
            assertEquals("****", resolved.displayValue(), "Source $source should be masked")
        }
    }

    @Test fun `null resolved value for non-sensitive returns empty string`() {
        val prop = KenvyProperty("base_url", PropertyType.STRING, null, sensitive = false)
        val resolved = ResolvedKenvyValue(prop, null, ResolutionSource.DEFAULT)
        assertEquals("", resolved.displayValue())
    }

    @Test fun `null resolved value for sensitive is not masked`() {
        val prop = KenvyProperty("api_key", PropertyType.STRING, null, sensitive = true)
        val resolved = ResolvedKenvyValue(prop, null, ResolutionSource.DEFAULT)
        assertEquals("", resolved.displayValue())
    }

    // ---- Task 1: parser round-trips for sensitive field ----

    @Test fun `parser round-trip - sensitive = true is parsed`() {
        val toml = tempToml("""
            [properties.api_key]
            type = "String"
            default = "placeholder"
            sensitive = true
        """.trimIndent())
        val props = KenvyParser.parse(toml)
        assertEquals(1, props.size)
        assertTrue(props[0].sensitive)
    }

    @Test fun `parser round-trip - sensitive = false is parsed`() {
        val toml = tempToml("""
            [properties.base_url]
            type = "String"
            default = "https://api.example.com"
            sensitive = false
        """.trimIndent())
        val props = KenvyParser.parse(toml)
        assertEquals(1, props.size)
        assertFalse(props[0].sensitive)
    }

    @Test fun `parser round-trip - sensitive omitted defaults to false`() {
        val toml = tempToml("""
            [properties.base_url]
            type = "String"
            default = "https://api.example.com"
        """.trimIndent())
        val props = KenvyParser.parse(toml)
        assertEquals(1, props.size)
        assertFalse(props[0].sensitive)
    }

    @Test fun `parser rejects non-boolean sensitive value`() {
        val toml = tempToml("""
            [properties.api_key]
            type = "String"
            default = "placeholder"
            sensitive = "yes"
        """.trimIndent())
        val ex = assertFailsWith<GradleException> { KenvyParser.parse(toml) }
        assertTrue(ex.message?.contains("api_key") == true, "Error must name the property")
        assertTrue(ex.message?.contains("sensitive") == true, "Error must name the field")
    }

    @Test fun `parser rejects numeric sensitive value`() {
        val toml = tempToml("""
            [properties.api_key]
            type = "String"
            default = "placeholder"
            sensitive = 1
        """.trimIndent())
        val ex = assertFailsWith<GradleException> { KenvyParser.parse(toml) }
        assertTrue(ex.message?.contains("api_key") == true)
        assertTrue(ex.message?.contains("sensitive") == true)
    }

    // ---- Task 5: example generator masks sensitive shared values ----

    @Test fun `common override for sensitive property is masked in example`() {
        val contract = ParsedKenvyContract(
            properties = listOf(KenvyProperty("api_key", PropertyType.STRING, "placeholder", sensitive = true)),
            commonOverrides = mapOf("api_key" to "shared-secret-value")
        )
        val output = buildLocalPropertiesExample(contract, null, null)
        assertFalse(output.contains("shared-secret-value"), "Sensitive shared value must not appear in example")
        assertTrue(output.contains("****"), "Sensitive shared value must be masked as ****")
    }

    @Test fun `non-sensitive common override is still visible in example`() {
        val contract = ParsedKenvyContract(
            properties = listOf(KenvyProperty("base_url", PropertyType.STRING, "https://default.example.com", sensitive = false)),
            commonOverrides = mapOf("base_url" to "https://shared.example.com")
        )
        val output = buildLocalPropertiesExample(contract, null, null)
        assertTrue(output.contains("https://shared.example.com"), "Non-sensitive shared value must remain visible")
    }

    // ---- Task 5: invalid type conversion errors mask sensitive values ----

    @Test fun `invalid Int conversion error for sensitive property masks raw value`() {
        val resolvedValues = listOf(
            ResolvedKenvyValue(
                property = KenvyProperty("secret_count", PropertyType.INT, "placeholder", sensitive = true),
                resolvedValue = "not-a-number",
                source = ResolutionSource.LOCAL_PROPERTIES
            )
        )
        val ex = assertFailsWith<GradleException> { buildGeneratedSource("com.example", "Config", resolvedValues) }
        assertFalse(ex.message?.contains("not-a-number") == true, "Raw sensitive value must not appear in error")
        assertTrue(ex.message?.contains("secret_count") == true, "Property name must appear in error")
    }

    @Test fun `invalid Int conversion error for non-sensitive property shows raw value`() {
        val resolvedValues = listOf(
            ResolvedKenvyValue(
                property = KenvyProperty("retry_count", PropertyType.INT, "3", sensitive = false),
                resolvedValue = "not-a-number",
                source = ResolutionSource.LOCAL_PROPERTIES
            )
        )
        val ex = assertFailsWith<GradleException> { buildGeneratedSource("com.example", "Config", resolvedValues) }
        assertTrue(ex.message?.contains("not-a-number") == true, "Non-sensitive raw value must appear in error")
    }

    @Test fun `multiple type mismatches are reported together while masking sensitive values`() {
        val resolvedValues = listOf(
            ResolvedKenvyValue(
                property = KenvyProperty("retry_count", PropertyType.INT, "3", sensitive = false),
                resolvedValue = "abc",
                source = ResolutionSource.LOCAL_PROPERTIES
            ),
            ResolvedKenvyValue(
                property = KenvyProperty("secret_timeout", PropertyType.LONG, "1", sensitive = true),
                resolvedValue = "nope",
                source = ResolutionSource.ENVIRONMENT
            )
        )

        val ex = assertFailsWith<GradleException> { buildGeneratedSource("com.example", "Config", resolvedValues) }
        assertTrue(ex.message?.contains("Type mismatches:") == true)
        assertTrue(ex.message?.contains("retry_count") == true)
        assertTrue(ex.message?.contains("abc") == true)
        assertTrue(ex.message?.contains("secret_timeout") == true)
        assertTrue(ex.message?.contains("****") == true)
        assertFalse(ex.message?.contains("nope") == true)
    }

    private fun tempToml(content: String): File = File.createTempFile("kenvy-test", ".toml").apply {
        writeText(content)
        deleteOnExit()
    }
}
