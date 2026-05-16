package io.github.adriandleon.kenvy

import java.io.StringReader
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KenvyExampleGeneratorTest {

    // AC2 — placeholder properties appear as editable entries with type comment
    @Test fun `placeholder property is included as blank assignment with type comment`() {
        val contract = ParsedKenvyContract(
            properties = listOf(KenvyProperty("api_key", PropertyType.STRING, "placeholder"))
        )
        val output = buildLocalPropertiesExample(contract, null, null)
        assertContains(output, "# api_key (String)")
        assertContains(output, "api_key=")
    }

    // AC2 — description comment is emitted when present
    @Test fun `description is emitted as comment above entry`() {
        val contract = ParsedKenvyContract(
            properties = listOf(KenvyProperty("api_key", PropertyType.STRING, "placeholder", description = "Backend API key"))
        )
        val output = buildLocalPropertiesExample(contract, null, null)
        assertContains(output, "# Backend API key")
    }

    // AC2 — help_url is emitted when present
    @Test fun `help_url is emitted as comment above entry`() {
        val contract = ParsedKenvyContract(
            properties = listOf(
                KenvyProperty("api_key", PropertyType.STRING, "placeholder", helpUrl = "https://wiki.internal/api-setup")
            )
        )
        val output = buildLocalPropertiesExample(contract, null, null)
        assertContains(output, "# Setup: https://wiki.internal/api-setup")
    }

    // AC2 — both description and help_url present
    @Test fun `description and help_url both appear when both are set`() {
        val contract = ParsedKenvyContract(
            properties = listOf(
                KenvyProperty(
                    "api_key", PropertyType.STRING, "placeholder",
                    description = "Backend API key",
                    helpUrl = "https://wiki.internal/api-setup"
                )
            )
        )
        val output = buildLocalPropertiesExample(contract, null, null)
        assertContains(output, "# Backend API key")
        assertContains(output, "# Setup: https://wiki.internal/api-setup")
    }

    // AC6 — Epic 1 contract with no overrides still generates successfully
    @Test fun `Epic 1 style contract with no overrides generates successfully`() {
        val contract = ParsedKenvyContract(
            properties = listOf(
                KenvyProperty("api_key", PropertyType.STRING, "placeholder"),
                KenvyProperty("retry_count", PropertyType.INT, "3")
            )
        )
        val output = buildLocalPropertiesExample(contract, null, null)
        // Placeholder property appears as editable
        assertContains(output, "api_key=")
        // Non-placeholder with default appears as comment only (not an active assignment)
        assertContains(output, "retry_count")
    }

    // AC3 — effective shared value from common override is surfaced as comment
    @Test fun `common override effective value is surfaced as comment for non-placeholder`() {
        val contract = ParsedKenvyContract(
            properties = listOf(KenvyProperty("base_url", PropertyType.STRING, "https://default.example.com")),
            commonOverrides = mapOf("base_url" to "https://api.shared.example.com")
        )
        val output = buildLocalPropertiesExample(contract, null, null)
        assertContains(output, "https://api.shared.example.com")
        // The active line should be a comment, not a real assignment
        assertFalse(output.lines().any { it.trim() == "base_url=https://api.shared.example.com" },
            "Non-placeholder with effective value must not be an active assignment")
    }

    // AC3 — effective value source is identified in comment
    @Test fun `common override source is identified in comment`() {
        val contract = ParsedKenvyContract(
            properties = listOf(KenvyProperty("base_url", PropertyType.STRING, "https://default.example.com")),
            commonOverrides = mapOf("base_url" to "https://api.shared.example.com")
        )
        val output = buildLocalPropertiesExample(contract, null, null)
        assertContains(output, "common_override")
    }

    // AC3 — platform override effective value surfaced as comment
    @Test fun `platform override effective value is surfaced as comment`() {
        val contract = ParsedKenvyContract(
            properties = listOf(KenvyProperty("base_url", PropertyType.STRING, "https://default.example.com")),
            platformOverrides = mapOf("android" to mapOf("base_url" to "https://api.android.example.com"))
        )
        val output = buildLocalPropertiesExample(contract, "android", null)
        assertContains(output, "https://api.android.example.com")
        assertContains(output, "platform_override")
    }

    // Output determinism — property order is contract order
    @Test fun `properties appear in contract declaration order`() {
        val contract = ParsedKenvyContract(
            properties = listOf(
                KenvyProperty("zzz", PropertyType.STRING, "placeholder"),
                KenvyProperty("aaa", PropertyType.STRING, "placeholder")
            )
        )
        val output = buildLocalPropertiesExample(contract, null, null)
        val zzzIndex = output.indexOf("zzz=")
        val aaaIndex = output.indexOf("aaa=")
        assertTrue(zzzIndex < aaaIndex, "Properties must appear in contract declaration order")
    }

    // Security — local/env values never appear
    @Test fun `output does not include any local or environment values (contract-only source)`() {
        val contract = ParsedKenvyContract(
            properties = listOf(KenvyProperty("api_key", PropertyType.STRING, "placeholder"))
        )
        val output = buildLocalPropertiesExample(contract, null, null)
        // Only empty assignment — no values leaked from local or env
        val assignmentLine = output.lines().first { it.startsWith("api_key") }
        assertTrue(assignmentLine.trim() == "api_key=", "Placeholder entry must be blank: '$assignmentLine'")
    }

    // null default (also a placeholder) is included as editable entry
    @Test fun `property with null default is included as placeholder entry`() {
        val contract = ParsedKenvyContract(
            properties = listOf(KenvyProperty("secret", PropertyType.STRING, null))
        )
        val output = buildLocalPropertiesExample(contract, null, null)
        assertContains(output, "secret=")
    }

    // Non-placeholder property without overrides — not a real assignment
    @Test fun `non-placeholder property with default appears as comment not active assignment`() {
        val contract = ParsedKenvyContract(
            properties = listOf(KenvyProperty("timeout", PropertyType.INT, "30"))
        )
        val output = buildLocalPropertiesExample(contract, null, null)
        assertFalse(output.lines().any { it.trim() == "timeout=30" },
            "Non-placeholder property must not appear as an active assignment")
        assertContains(output, "timeout")
    }

    @Test fun `declaration default value is not emitted into example`() {
        val contract = ParsedKenvyContract(
            properties = listOf(KenvyProperty("service_url", PropertyType.STRING, "https://internal.example.com"))
        )

        val output = buildLocalPropertiesExample(contract, null, null)

        assertFalse(output.contains("https://internal.example.com"),
            "Declaration defaults should stay in kenvy.toml, not be copied into local.properties.example")
        assertContains(output, "# service_url=")
    }

    @Test fun `property keys are escaped for java properties files`() {
        val contract = ParsedKenvyContract(
            properties = listOf(KenvyProperty("api key", PropertyType.STRING, "placeholder"))
        )

        val output = buildLocalPropertiesExample(contract, null, null)

        assertContains(output, "api\\ key=")

        val filled = output.replace("api\\ key=", "api\\ key=filled")
        val props = Properties()
        props.load(StringReader(filled))
        assertEquals("filled", props.getProperty("api key"))
    }

    @Test fun `multiline description is emitted as comments only`() {
        val contract = ParsedKenvyContract(
            properties = listOf(
                KenvyProperty(
                    name = "api_key",
                    type = PropertyType.STRING,
                    defaultValue = "placeholder",
                    description = "Backend API key\napi_key=injected"
                )
            )
        )

        val output = buildLocalPropertiesExample(contract, null, null)

        assertContains(output, "# Backend API key")
        assertContains(output, "# api_key=injected")
        assertFalse(output.lines().any { it == "api_key=injected" },
            "Metadata newlines must not inject active properties")
    }

    @Test fun `placeholder with shared override surfaces override comment and remains editable`() {
        val contract = ParsedKenvyContract(
            properties = listOf(KenvyProperty("api_key", PropertyType.STRING, "placeholder")),
            commonOverrides = mapOf("api_key" to "shared-value")
        )

        val output = buildLocalPropertiesExample(contract, null, null)

        assertContains(output, "# Effective shared value from common_override: shared-value")
        assertTrue(output.lines().any { it == "api_key=" },
            "Placeholder declarations should still provide an editable local entry")
    }

    @Test fun `multiline shared override value is emitted as comments only`() {
        val contract = ParsedKenvyContract(
            properties = listOf(KenvyProperty("base_url", PropertyType.STRING, "https://default.example.com")),
            commonOverrides = mapOf("base_url" to "line1\nbase_url=injected")
        )

        val output = buildLocalPropertiesExample(contract, null, null)

        assertContains(output, "# Effective shared value from common_override: line1")
        assertContains(output, "# base_url=injected")
        assertFalse(output.lines().any { it == "base_url=injected" },
            "Shared override newlines must not inject active properties")
    }

    // Header comment
    @Test fun `output starts with a header comment`() {
        val contract = ParsedKenvyContract(
            properties = listOf(KenvyProperty("api_key", PropertyType.STRING, "placeholder"))
        )
        val output = buildLocalPropertiesExample(contract, null, null)
        assertTrue(output.startsWith("#"), "Output must start with a header comment line")
    }
}
