package io.github.adriandleon.kenvy

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerateKenvyTaskTest {

    private fun resolved(prop: KenvyProperty, value: String? = prop.defaultValue) =
        listOf(ResolvedKenvyValue(prop, value, ResolutionSource.DEFAULT))

    @Test fun `String property with default generates correct val`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("api_key", PropertyType.STRING, "my-value")))
        assertContains(src, "val apiKey: String = \"my-value\"")
    }

    @Test fun `Int property with numeric default generates Int literal`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("count", PropertyType.INT, "42")))
        assertContains(src, "val count: Int = 42")
    }

    @Test fun `Boolean property with true default generates Boolean literal`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("enabled", PropertyType.BOOLEAN, "true")))
        assertContains(src, "val enabled: Boolean = true")
    }

    @Test fun `Long property with large default generates Long literal`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("timeout", PropertyType.LONG, "5000")))
        assertContains(src, "val timeout: Long = 5000L")
    }

    @Test fun `property with description generates KDoc`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("api_key", PropertyType.STRING, "configured-value", description = "The API key")))
        assertContains(src, "/** The API key */")
        assertContains(src, "val apiKey: String = \"configured-value\"")
    }

    @Test fun `property without description has no KDoc`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("base_url", PropertyType.STRING, "https://api.example.com")))
        assertFalse(src.contains("/**"))
    }

    @Test fun `String property with null default is reported as missing configuration`() {
        val error = assertFailsWith<GradleException> {
            buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("missing", PropertyType.STRING, null), null))
        }
        assertContains(error.message.orEmpty(), "Missing values:")
        assertContains(error.message.orEmpty(), "+ missing=<YOUR_VALUE>")
    }

    @Test fun `local placeholder sentinel is reported as missing configuration`() {
        val error = assertFailsWith<GradleException> {
            buildGeneratedSource(
                "com.example",
                "Kenvy",
                listOf(
                    ResolvedKenvyValue(
                        property = KenvyProperty("api_key", PropertyType.STRING, "placeholder"),
                        resolvedValue = "placeholder",
                        source = ResolutionSource.LOCAL_PROPERTIES
                    )
                )
            )
        }
        assertContains(error.message.orEmpty(), "Missing values:")
        assertContains(error.message.orEmpty(), "+ api_key=<YOUR_VALUE>")
    }

    @Test fun `missing value error includes checked local files list`() {
        val error = assertFailsWith<GradleException> {
            buildGeneratedSource(
                pkg = "com.example",
                name = "Kenvy",
                resolvedValues = listOf(
                    ResolvedKenvyValue(
                        property = KenvyProperty("api_key", PropertyType.STRING, null),
                        resolvedValue = null,
                        source = ResolutionSource.DEFAULT
                    )
                ),
                checkedLocalFiles = listOf(
                    LocalPropertiesFileInfo(label = "root", absolutePath = "/project/local.properties", exists = false),
                    LocalPropertiesFileInfo(label = "module :shared", absolutePath = "/project/shared/local.properties", exists = false)
                )
            )
        }

        assertContains(error.message.orEmpty(), "Checked local properties files:")
        assertContains(error.message.orEmpty(), "- root: /project/local.properties (missing)")
        assertContains(error.message.orEmpty(), "- module :shared: /project/shared/local.properties (missing)")
        assertContains(error.message.orEmpty(), "Docs:")
    }

    @Test fun `missing value error without checked files has no file list`() {
        val error = assertFailsWith<GradleException> {
            buildGeneratedSource(
                pkg = "com.example",
                name = "Kenvy",
                resolvedValues = listOf(
                    ResolvedKenvyValue(
                        property = KenvyProperty("api_key", PropertyType.STRING, null),
                        resolvedValue = null,
                        source = ResolutionSource.DEFAULT
                    )
                )
            )
        }

        assertFalse(error.message.orEmpty().contains("Checked local properties files:"))
    }

    @Test fun `platform variant generation suggests scoped local key for missing value`() {
        val error = assertFailsWith<GradleException> {
            buildGeneratedSource(
                pkg = "com.example",
                name = "Kenvy",
                resolvedValues = listOf(
                    ResolvedKenvyValue(
                        property = KenvyProperty("api_key", PropertyType.STRING, null),
                        resolvedValue = null,
                        source = ResolutionSource.DEFAULT
                    )
                ),
                platform = "ios",
                variant = "debug"
            )
        }

        assertContains(error.message.orEmpty(), "Missing values:")
        assertContains(error.message.orEmpty(), "+ api_key.ios.debug=<YOUR_VALUE>")
    }

    @Test fun `package name appears as package declaration`() {
        val src = buildGeneratedSource("io.example.app", "Kenvy", resolved(KenvyProperty("x", PropertyType.STRING, "y")))
        assertTrue(src.startsWith("// Auto-generated by Kenvy. Do not edit manually.\npackage io.example.app"))
    }

    @Test fun `Int property with null default is reported as missing configuration`() {
        val error = assertFailsWith<GradleException> {
            buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("count", PropertyType.INT, null), null))
        }
        assertContains(error.message.orEmpty(), "Missing values:")
        assertContains(error.message.orEmpty(), "+ count=<YOUR_VALUE>")
    }

    @Test fun `Boolean property with false default generates false literal`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("flag", PropertyType.BOOLEAN, "false")))
        assertContains(src, "val flag: Boolean = false")
    }

    @Test fun `Long property with null default is reported as missing configuration`() {
        val error = assertFailsWith<GradleException> {
            buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("timeout", PropertyType.LONG, null), null))
        }
        assertContains(error.message.orEmpty(), "Missing values:")
        assertContains(error.message.orEmpty(), "+ timeout=<YOUR_VALUE>")
    }

    @Test fun `String property with dollar sign in value is escaped`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("key", PropertyType.STRING, "costs \$100")))
        assertContains(src, """val key: String = "costs \$100" """.trim())
    }

    @Test fun `String property with line breaks is escaped`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("key", PropertyType.STRING, "line1\nline2\rline3")))
        assertContains(src, "val key: String = \"line1\\nline2\\rline3\"")
    }

    @Test fun `snake case property name generates lower camel case`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("app_platform_name", PropertyType.STRING, "value")))
        assertContains(src, "val appPlatformName: String = \"value\"")
    }

    @Test fun `retry_count property name generates retryCount`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("retry_count", PropertyType.INT, "3")))
        assertContains(src, "val retryCount: Int = 3")
    }

    @Test fun `timeout_ms property name generates timeoutMs`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("timeout_ms", PropertyType.LONG, "5000")))
        assertContains(src, "val timeoutMs: Long = 5000L")
    }

    @Test fun `property name with hyphen is generated as lower camel case`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("api-key", PropertyType.STRING, "value")))
        assertContains(src, "val apiKey: String = \"value\"")
    }

    @Test fun `uppercase underscore property name is generated as lower camel case`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("API_KEY", PropertyType.STRING, "value")))
        assertContains(src, "val apiKey: String = \"value\"")
    }

    @Test fun `existing camel case property name stays camel case`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("appPlatformName", PropertyType.STRING, "value")))
        assertContains(src, "val appPlatformName: String = \"value\"")
    }

    @Test fun `pascal case property name is decapitalized`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("AppPlatformName", PropertyType.STRING, "value")))
        assertContains(src, "val appPlatformName: String = \"value\"")
    }

    @Test fun `property name with unsupported characters is sanitized to valid lower camel identifier`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("1/api-key", PropertyType.STRING, "value")))
        assertContains(src, "val _1ApiKey: String = \"value\"")
    }

    @Test fun `property name matching Kotlin keyword is sanitized`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("class", PropertyType.STRING, "value")))
        assertContains(src, "val class_: String = \"value\"")
    }

    @Test fun `duplicate generated property names fail clearly`() {
        val props = listOf(
            ResolvedKenvyValue(KenvyProperty("api-key", PropertyType.STRING, "a"), "a", ResolutionSource.DEFAULT),
            ResolvedKenvyValue(KenvyProperty("api_key", PropertyType.STRING, "b"), "b", ResolutionSource.DEFAULT)
        )
        val error = assertFailsWith<GradleException> {
            buildGeneratedSource("com.example", "Kenvy", props)
        }
        assertContains(error.message.orEmpty(), "generated Kotlin property 'apiKey'")
        assertContains(error.message.orEmpty(), "api-key, api_key")
        assertContains(error.message.orEmpty(), "compatibility naming style")
    }

    @Test fun `camel case collision against snake case fails clearly`() {
        val props = listOf(
            ResolvedKenvyValue(KenvyProperty("app_platform_name", PropertyType.STRING, "a"), "a", ResolutionSource.DEFAULT),
            ResolvedKenvyValue(KenvyProperty("appPlatformName", PropertyType.STRING, "b"), "b", ResolutionSource.DEFAULT)
        )
        val error = assertFailsWith<GradleException> {
            buildGeneratedSource("com.example", "Kenvy", props)
        }
        assertContains(error.message.orEmpty(), "generated Kotlin property 'appPlatformName'")
        assertContains(error.message.orEmpty(), "app_platform_name, appPlatformName")
        assertContains(error.message.orEmpty(), "compatibility naming style")
    }

    @Test fun `preserve compatibility style keeps underscore based names`() {
        val src = buildGeneratedSource(
            "com.example",
            "Kenvy",
            resolved(KenvyProperty("api-key", PropertyType.STRING, "value")),
            propertyNameStyle = GeneratedPropertyNameStyle.PRESERVE
        )

        assertContains(src, "val api_key: String = \"value\"")
    }

    @Test fun `description with KDoc terminator is sanitized`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("key", PropertyType.STRING, "value", description = "Comment with */ terminator")))
        assertContains(src, "/** Comment with * / terminator */")
    }

    @Test fun `file header comment is present`() {
        val src = buildGeneratedSource("com.example", "Kenvy", resolved(KenvyProperty("x", PropertyType.STRING, "y")))
        assertContains(src, "// Auto-generated by Kenvy. Do not edit manually.")
    }

    @Test fun `object block uses provided interface name`() {
        val src = buildGeneratedSource("com.example", "AppConfig", resolved(KenvyProperty("x", PropertyType.STRING, "y")))
        assertContains(src, "object AppConfig {")
    }

    @Test fun `android resolved values keep configured package object names and Kotlin literals`() {
        val contract = ParsedKenvyContract(
            properties = listOf(
                KenvyProperty("api-key", PropertyType.STRING, "common-key"),
                KenvyProperty("retry_count", PropertyType.INT, "3"),
                KenvyProperty("enabled", PropertyType.BOOLEAN, "false"),
                KenvyProperty("timeout", PropertyType.LONG, "30")
            ),
            commonOverrides = mapOf("api-key" to "common-override"),
            platformOverrides = mapOf("android" to mapOf("api-key" to "android-key"))
        )

        val resolved = KenvyResolver.resolve(
            contract = contract,
            platform = "android",
            localProperties = emptyMap()
        ) { null }

        val src = buildGeneratedSource("com.example.android", "AndroidConfig", resolved)

        assertContains(src, "package com.example.android")
        assertContains(src, "object AndroidConfig {")
        assertContains(src, "val apiKey: String = \"android-key\"")
        assertContains(src, "val retryCount: Int = 3")
        assertContains(src, "val enabled: Boolean = false")
        assertContains(src, "val timeout: Long = 30L")
    }

    @Test fun `iOS resolved values keep configured package object names and Kotlin literals`() {
        val contract = ParsedKenvyContract(
            properties = listOf(
                KenvyProperty("api-key", PropertyType.STRING, "common-key"),
                KenvyProperty("retry_count", PropertyType.INT, "3"),
                KenvyProperty("enabled", PropertyType.BOOLEAN, "false"),
                KenvyProperty("timeout", PropertyType.LONG, "30")
            ),
            commonOverrides = mapOf("api-key" to "common-override"),
            platformOverrides = mapOf("ios" to mapOf("api-key" to "ios-key"))
        )

        val resolved = KenvyResolver.resolve(
            contract = contract,
            platform = "ios",
            localProperties = emptyMap()
        ) { null }

        val src = buildGeneratedSource("com.example.ios", "IosConfig", resolved)

        assertContains(src, "package com.example.ios")
        assertContains(src, "object IosConfig {")
        assertContains(src, "val apiKey: String = \"ios-key\"")
        assertContains(src, "val retryCount: Int = 3")
        assertContains(src, "val enabled: Boolean = false")
        assertContains(src, "val timeout: Long = 30L")
    }

    @Test fun `overrides dot ios beats common and default for iOS output`() {
        val contract = ParsedKenvyContract(
            properties = listOf(
                KenvyProperty("api-key", PropertyType.STRING, "default-value")
            ),
            commonOverrides = mapOf("api-key" to "common-value"),
            platformOverrides = mapOf("ios" to mapOf("api-key" to "ios-value"))
        )

        val resolved = KenvyResolver.resolve(
            contract = contract,
            platform = "ios",
            localProperties = emptyMap()
        ) { null }

        val src = buildGeneratedSource("com.example", "Kenvy", resolved)
        assertContains(src, "val apiKey: String = \"ios-value\"")
    }

    @Test fun `iOS no-override falls back to common then default`() {
        val contract = ParsedKenvyContract(
            properties = listOf(
                KenvyProperty("api-key", PropertyType.STRING, "default-value"),
                KenvyProperty("base-url", PropertyType.STRING, "default-url")
            ),
            commonOverrides = mapOf("api-key" to "common-value")
        )

        val resolved = KenvyResolver.resolve(
            contract = contract,
            platform = "ios",
            localProperties = emptyMap()
        ) { null }

        val src = buildGeneratedSource("com.example", "Kenvy", resolved)
        assertContains(src, "val apiKey: String = \"common-value\"")
        assertContains(src, "val baseUrl: String = \"default-url\"")
    }

    @Test fun `canonical ios platform resolves overrides dot ios values`() {
        val contract = ParsedKenvyContract(
            properties = listOf(KenvyProperty("feature_flag", PropertyType.BOOLEAN, "false")),
            platformOverrides = mapOf("ios" to mapOf("feature_flag" to "true"))
        )

        val resolved = KenvyResolver.resolve(
            contract = contract,
            platform = "ios",
            localProperties = emptyMap()
        ) { null }

        val src = buildGeneratedSource("com.example", "Kenvy", resolved)
        assertContains(src, "val featureFlag: Boolean = true")
    }

    @Test fun `invalid iOS-resolved typed values fail through the existing masked error path for sensitive properties`() {
        val error = assertFailsWith<GradleException> {
            buildGeneratedSource(
                "com.example",
                "Kenvy",
                listOf(
                    ResolvedKenvyValue(
                        property = KenvyProperty(
                            name = "timeout_ms",
                            type = PropertyType.INT,
                            defaultValue = "5000",
                            sensitive = true
                        ),
                        resolvedValue = "not-an-int",
                        source = ResolutionSource.PLATFORM_OVERRIDE
                    )
                )
            )
        }

        assertContains(error.message.orEmpty(), "timeout_ms")
        assertContains(error.message.orEmpty(), "****")
        assertFalse(error.message.orEmpty().contains("not-an-int"))
    }

    @Test fun `invalid android resolved typed values use masked error path for sensitive properties`() {
        val error = assertFailsWith<GradleException> {
            buildGeneratedSource(
                "com.example",
                "Kenvy",
                listOf(
                    ResolvedKenvyValue(
                        property = KenvyProperty(
                            name = "retry_count",
                            type = PropertyType.INT,
                            defaultValue = "3",
                            sensitive = true
                        ),
                        resolvedValue = "not-an-int",
                        source = ResolutionSource.PLATFORM_OVERRIDE
                    )
                )
            )
        }

        assertContains(error.message.orEmpty(), "retry_count")
        assertContains(error.message.orEmpty(), "****")
        assertFalse(error.message.orEmpty().contains("not-an-int"))
    }

    @Test fun `expect object mode omits initializers while preserving type signatures`() {
        val src = buildGeneratedSource(
            "com.example",
            "Kenvy",
            resolved(KenvyProperty("api_key", PropertyType.STRING, "default")),
            GeneratedDeclarationMode.EXPECT_OBJECT
        )

        assertContains(src, "expect object Kenvy {")
        assertContains(src, "val apiKey: String")
        assertFalse(src.contains("= \"default\""))
    }

    @Test fun `expect object mode emits file-level expect actual warning suppression before package`() {
        val src = buildGeneratedSource(
            "com.example",
            "Kenvy",
            resolved(KenvyProperty("api_key", PropertyType.STRING, "default")),
            GeneratedDeclarationMode.EXPECT_OBJECT
        )

        assertTrue(src.indexOf("@file:Suppress(\"EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING\")") < src.indexOf("package com.example"))
        assertContains(src, "// Auto-generated by Kenvy. Do not edit manually.")
    }

    @Test fun `expect object mode does not require placeholder defaults`() {
        val src = buildGeneratedSource(
            "com.example",
            "Kenvy",
            resolved(KenvyProperty("api_key", PropertyType.STRING, "placeholder")),
            GeneratedDeclarationMode.EXPECT_OBJECT
        )

        assertContains(src, "expect object Kenvy {")
        assertContains(src, "val apiKey: String")
        assertFalse(src.contains("= \"placeholder\""))
    }

    @Test fun `actual object mode emits actual members with literals`() {
        val src = buildGeneratedSource(
            "com.example",
            "Kenvy",
            resolved(KenvyProperty("timeout", PropertyType.LONG, "5000")),
            GeneratedDeclarationMode.ACTUAL_OBJECT
        )

        assertContains(src, "actual object Kenvy {")
        assertContains(src, "actual val timeout: Long = 5000L")
    }

    @Test fun `actual object mode emits file-level expect actual warning suppression before package`() {
        val src = buildGeneratedSource(
            "com.example",
            "Kenvy",
            resolved(KenvyProperty("timeout", PropertyType.LONG, "5000")),
            GeneratedDeclarationMode.ACTUAL_OBJECT
        )

        assertTrue(src.indexOf("@file:Suppress(\"EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING\")") < src.indexOf("package com.example"))
        assertContains(src, "// Auto-generated by Kenvy. Do not edit manually.")
    }

    @Test fun `regular object mode omits expect actual warning suppression`() {
        val src = buildGeneratedSource(
            "com.example",
            "Kenvy",
            resolved(KenvyProperty("timeout", PropertyType.LONG, "5000")),
            GeneratedDeclarationMode.OBJECT
        )

        assertFalse(src.contains("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"))
    }
}
