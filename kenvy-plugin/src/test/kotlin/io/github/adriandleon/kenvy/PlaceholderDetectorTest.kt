package io.github.adriandleon.kenvy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaceholderDetectorTest {

    @Test fun `null default is placeholder`() {
        val prop = KenvyProperty("key", PropertyType.STRING, null)
        assertTrue(prop.isPlaceholder)
    }

    @Test fun `empty default is placeholder`() {
        val prop = KenvyProperty("key", PropertyType.STRING, "")
        assertTrue(prop.isPlaceholder)
    }

    @Test fun `'placeholder' sentinel is placeholder`() {
        val prop = KenvyProperty("key", PropertyType.STRING, "placeholder")
        assertTrue(prop.isPlaceholder)
    }

    @Test fun `case-insensitive 'PLACEHOLDER' is placeholder`() {
        val prop = KenvyProperty("key", PropertyType.STRING, "PLACEHOLDER")
        assertTrue(prop.isPlaceholder)
    }

    @Test fun `YOUR_VALUE sentinel is placeholder`() {
        val prop = KenvyProperty("key", PropertyType.STRING, "<YOUR_VALUE>")
        assertTrue(prop.isPlaceholder)
    }

    @Test fun `REPLACE_ME sentinel is placeholder`() {
        val prop = KenvyProperty("key", PropertyType.STRING, "REPLACE_ME")
        assertTrue(prop.isPlaceholder)
    }

    @Test fun `real URL default is not placeholder`() {
        val prop = KenvyProperty("base_url", PropertyType.STRING, "https://api.example.com")
        assertFalse(prop.isPlaceholder)
    }

    @Test fun `numeric default is not placeholder`() {
        val prop = KenvyProperty("retries", PropertyType.INT, "3")
        assertFalse(prop.isPlaceholder)
    }

    @Test fun `boolean default is not placeholder`() {
        val prop = KenvyProperty("debug", PropertyType.BOOLEAN, "false")
        assertFalse(prop.isPlaceholder)
    }

    @Test fun `whitespace-only default is placeholder`() {
        val prop = KenvyProperty("key", PropertyType.STRING, "   ")
        assertTrue(prop.isPlaceholder)
    }

    @Test fun `findUnresolved returns only placeholder properties`() {
        val props = listOf(
            KenvyProperty("api_key", PropertyType.STRING, null),
            KenvyProperty("base_url", PropertyType.STRING, "https://api.example.com"),
            KenvyProperty("debug", PropertyType.BOOLEAN, "false")
        )
        val unresolved = PlaceholderDetector.findUnresolved(props)
        assertEquals(1, unresolved.size)
        assertEquals("api_key", unresolved.first().name)
    }
}
