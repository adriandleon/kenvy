package io.github.adriandleon.kenvy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KenvyPropertyMetadataTest {

    @Test fun `property with description and helpUrl stores both fields`() {
        val prop = KenvyProperty(
            name = "api_key",
            type = PropertyType.STRING,
            defaultValue = "placeholder",
            description = "Backend API key",
            helpUrl = "https://wiki.internal/api-setup"
        )
        assertEquals("Backend API key", prop.description)
        assertEquals("https://wiki.internal/api-setup", prop.helpUrl)
    }

    @Test fun `property without description defaults to null`() {
        val prop = KenvyProperty("base_url", PropertyType.STRING, "https://api.example.com")
        assertNull(prop.description)
    }

    @Test fun `property without helpUrl defaults to null`() {
        val prop = KenvyProperty("base_url", PropertyType.STRING, "https://api.example.com")
        assertNull(prop.helpUrl)
    }

    @Test fun `isPlaceholder is unaffected by metadata fields`() {
        val placeholder = KenvyProperty(
            name = "api_key",
            type = PropertyType.STRING,
            defaultValue = null,
            helpUrl = "https://wiki.internal/api-setup"
        )
        val valid = KenvyProperty(
            name = "base_url",
            type = PropertyType.STRING,
            defaultValue = "https://api.example.com",
            helpUrl = "https://docs.example.com"
        )
        assertTrue(placeholder.isPlaceholder)
        assertFalse(valid.isPlaceholder)
    }

    @Test fun `backward compatibility - old constructor without metadata fields`() {
        val prop = KenvyProperty("old_prop", PropertyType.STRING, "someDefault")
        assertNull(prop.description)
        assertNull(prop.helpUrl)
    }

    @Test fun `parser round-trip stores description and helpUrl from TOML`() {
        val toml = File.createTempFile("kenvy-test", ".toml").apply {
            writeText("""
                [properties.api_key]
                type = "String"
                default = "placeholder"
                description = "Backend API key"
                help_url = "https://wiki.internal/api-setup"
            """.trimIndent())
            deleteOnExit()
        }
        val props = KenvyParser.parse(toml)
        assertEquals(1, props.size)
        assertEquals("Backend API key", props[0].description)
        assertEquals("https://wiki.internal/api-setup", props[0].helpUrl)
    }

    @Test fun `parser round-trip - metadata absent yields null fields`() {
        val toml = File.createTempFile("kenvy-test", ".toml").apply {
            writeText("""
                [properties.base_url]
                type = "String"
                default = "https://api.example.com"
            """.trimIndent())
            deleteOnExit()
        }
        val props = KenvyParser.parse(toml)
        assertEquals(1, props.size)
        assertNull(props[0].description)
        assertNull(props[0].helpUrl)
    }
}
