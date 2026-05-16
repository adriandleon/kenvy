package io.github.adriandleon.kenvy

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KenvyResolutionFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private val tomlWithApiKey = """
        [properties.api_key]
        type = "String"
        default = "placeholder"
        description = "Backend API key"

        [properties.retry_count]
        type = "Int"
        default = "3"
    """.trimIndent()

    private fun setupProject(
        tomlContent: String = tomlWithApiKey,
        localPropertiesContent: String? = null,
        extraConfig: String = "",
        arguments: List<String> = listOf("generateKenvy"),
        environment: Map<String, String>? = null
    ): GradleRunner {
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"test\"")
        File(projectDir, "build.gradle.kts").writeText("""
            plugins { id("io.github.adriandleon.kenvy") }
            group = "com.example.test"
            $extraConfig
        """.trimIndent())
        File(projectDir, "kenvy.toml").writeText(tomlContent)
        if (localPropertiesContent != null) {
            File(projectDir, "local.properties").writeText(localPropertiesContent)
        }
        val runner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(arguments)
        return if (environment != null) runner.withEnvironment(environment) else runner
    }

    private fun generatedContent(): String =
        File(projectDir, "build/generated/kenvy/commonMain/kotlin/com/example/test/Kenvy.kt").readText()

    @Test fun `generateKenvy uses value from local properties`() {
        val result = setupProject(localPropertiesContent = "api_key=local-secret-key").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertTrue(generatedContent().contains("val apiKey: String = \"local-secret-key\""))
    }

    @Test fun `generateKenvy uses environment value when set`() {
        val result = setupProject(
            environment = mapOf("KENVY_API_KEY" to "env-secret-key")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertTrue(generatedContent().contains("val apiKey: String = \"env-secret-key\""))
    }

    @Test fun `environment value wins when both local properties and env are set`() {
        val result = setupProject(
            localPropertiesContent = "api_key=local-value",
            environment = mapOf("KENVY_API_KEY" to "env-wins")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertTrue(generatedContent().contains("val apiKey: String = \"env-wins\""))
    }

    @Test fun `blank environment value does not suppress missing value failure`() {
        val result = setupProject(
            environment = mapOf("KENVY_API_KEY" to ""),
            arguments = listOf("generateKenvy", "--warn")
        ).buildAndFail()

        assertTrue(result.output.contains("Missing values:"))
        assertTrue(result.output.contains("+ api_key=<YOUR_VALUE>"))
    }

    @Test fun `local placeholder sentinel value fails with missing value report`() {
        val result = setupProject(
            localPropertiesContent = "api_key=placeholder",
            arguments = listOf("generateKenvy", "--warn")
        ).buildAndFail()

        assertTrue(result.output.contains("Missing values:"))
        assertTrue(result.output.contains("+ api_key=<YOUR_VALUE>"))
    }

    @Test fun `invalid local Int value fails clearly`() {
        val result = setupProject(
            tomlContent = """
                [properties.retry_count]
                type = "Int"
                default = "3"
            """.trimIndent(),
            localPropertiesContent = "retry_count=abc"
        ).buildAndFail()

        assertTrue(result.output.contains("retry_count"))
        assertTrue(result.output.contains("Type mismatches:"))
        assertTrue(result.output.contains("expected Int"))
    }

    @Test fun `invalid environment Boolean value fails clearly`() {
        val result = setupProject(
            tomlContent = """
                [properties.enabled]
                type = "Boolean"
                default = "true"
            """.trimIndent(),
            environment = mapOf("KENVY_ENABLED" to "treu")
        ).buildAndFail()

        assertTrue(result.output.contains("enabled"))
        assertTrue(result.output.contains("Type mismatches:"))
        assertTrue(result.output.contains("expected Boolean"))
    }

    @Test fun `unknown overrides common key fails clearly`() {
        val tomlWithBadOverride = """
            [properties.api_key]
            type = "String"
            default = "placeholder"

            [overrides.common]
            unknown_prop = "value"
        """.trimIndent()
        val result = setupProject(tomlContent = tomlWithBadOverride).buildAndFail()
        assertTrue(result.output.contains("unknown_prop"))
        assertTrue(result.output.contains("overrides.common"))
        assertTrue(result.output.contains("does not match any declared"))
        assertTrue(result.output.contains("Resolution conflicts:"))
        assertTrue(result.output.contains("Resolution chain:"))
    }

    @Test fun `multiple unknown override keys are reported together`() {
        val tomlWithBadOverrides = """
            [properties.api_key]
            type = "String"
            default = "configured-value"

            [overrides.common]
            unknown_prop = "value"
            another_unknown = "value"
        """.trimIndent()
        val result = setupProject(tomlContent = tomlWithBadOverrides).buildAndFail()

        assertTrue(result.output.contains("Kenvy: Configuration has 2 issue(s)."))
        assertTrue(result.output.contains("unknown_prop"))
        assertTrue(result.output.contains("another_unknown"))
        assertTrue(result.output.contains("Resolution conflicts:"))
        assertTrue(result.output.contains("Resolution chain:"))
    }

    @Test fun `existing Epic 1-style kenvy toml without overrides still passes`() {
        val result = setupProject(
            tomlContent = """
                [properties.base_url]
                type = "String"
                default = "https://api.example.com"

                [properties.port]
                type = "Int"
                default = "8080"
            """.trimIndent()
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        val content = generatedContent()
        assertTrue(content.contains("val baseUrl: String = \"https://api.example.com\""))
        assertTrue(content.contains("val port: Int = 8080"))
    }

    @Test fun `common override value is used when no local or env value exists`() {
        val tomlWithCommonOverride = """
            [properties.api_key]
            type = "String"
            default = "placeholder"

            [overrides.common]
            api_key = "common-override-value"
        """.trimIndent()
        val result = setupProject(tomlContent = tomlWithCommonOverride).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertTrue(generatedContent().contains("val apiKey: String = \"common-override-value\""))
    }

    @Test fun `platform override is used for matching platform resolution`() {
        val result = setupProject(
            tomlContent = """
                [properties.base_url]
                type = "String"
                default = "https://default.example.com"

                [overrides.common]
                base_url = "https://common.example.com"

                [overrides.android]
                base_url = "https://android.example.com"
            """.trimIndent(),
            extraConfig = """
                kenvy {
                    platform.set("android")
                }
            """.trimIndent()
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertTrue(generatedContent().contains("val baseUrl: String = \"https://android.example.com\""))
    }

    @Test fun `variant override is used for matching platform and variant resolution`() {
        val result = setupProject(
            tomlContent = """
                [properties.base_url]
                type = "String"
                default = "https://default.example.com"

                [overrides.common]
                base_url = "https://common.example.com"

                [overrides.android]
                base_url = "https://android.example.com"

                [overrides.android.debug]
                base_url = "https://android-debug.example.com"
            """.trimIndent(),
            extraConfig = """
                kenvy {
                    platform.set("android")
                    variant.set("debug")
                }
            """.trimIndent()
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertTrue(generatedContent().contains("val baseUrl: String = \"https://android-debug.example.com\""))
    }

    @Test fun `local properties beat variant overrides`() {
        val result = setupProject(
            tomlContent = """
                [properties.base_url]
                type = "String"
                default = "https://default.example.com"

                [overrides.common]
                base_url = "https://common.example.com"

                [overrides.android]
                base_url = "https://android.example.com"

                [overrides.android.debug]
                base_url = "https://android-debug.example.com"
            """.trimIndent(),
            localPropertiesContent = "base_url=https://local.example.com",
            extraConfig = """
                kenvy {
                    platform.set("android")
                    variant.set("debug")
                }
            """.trimIndent()
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertTrue(generatedContent().contains("val baseUrl: String = \"https://local.example.com\""))
    }

    @Test fun `environment beats variant overrides`() {
        val result = setupProject(
            tomlContent = """
                [properties.base_url]
                type = "String"
                default = "https://default.example.com"

                [overrides.common]
                base_url = "https://common.example.com"

                [overrides.android]
                base_url = "https://android.example.com"

                [overrides.android.debug]
                base_url = "https://android-debug.example.com"
            """.trimIndent(),
            extraConfig = """
                kenvy {
                    platform.set("android")
                    variant.set("debug")
                }
            """.trimIndent(),
            environment = mapOf("KENVY_BASE_URL" to "https://env.example.com")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertTrue(generatedContent().contains("val baseUrl: String = \"https://env.example.com\""))
    }

    @Test fun `unknown platform override key fails clearly`() {
        val result = setupProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"

                [overrides.android]
                unknown_prop = "value"
            """.trimIndent()
        ).buildAndFail()

        assertTrue(result.output.contains("unknown_prop"))
        assertTrue(result.output.contains("overrides.android"))
        assertTrue(result.output.contains("does not match any declared"))
        assertTrue(result.output.contains("Resolution conflicts:"))
        assertTrue(result.output.contains("Resolution chain:"))
    }

    @Test fun `unknown variant override key fails clearly`() {
        val result = setupProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"

                [overrides.android.debug]
                unknown_prop = "value"
            """.trimIndent()
        ).buildAndFail()

        assertTrue(result.output.contains("unknown_prop"))
        assertTrue(result.output.contains("overrides.android.debug"))
        assertTrue(result.output.contains("does not match any declared"))
        assertTrue(result.output.contains("Resolution conflicts:"))
        assertTrue(result.output.contains("Resolution chain:"))
    }

    @Test fun `malformed deep override table fails clearly`() {
        val result = setupProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"

                [overrides.android.debug.extra]
                api_key = "value"
            """.trimIndent()
        ).buildAndFail()

        assertTrue(result.output.contains("overrides.android.debug.extra"))
        assertTrue(result.output.contains("Invalid override table"))
        assertTrue(result.output.contains("Resolution conflicts:"))
        assertTrue(result.output.contains("Resolution chain:"))
    }

    @Test fun `unsupported variant syntax fails clearly`() {
        val result = setupProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"

                [overrides.variants.debug]
                api_key = "value"
            """.trimIndent()
        ).buildAndFail()

        assertTrue(result.output.contains("overrides.variants.debug"))
        assertTrue(result.output.contains("Invalid override table"))
    }

    @Test fun `top level variant table fails clearly`() {
        val result = setupProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"

                [debug]
                api_key = "value"
            """.trimIndent()
        ).buildAndFail()

        assertTrue(result.output.contains("debug"))
        assertTrue(result.output.contains("Invalid override table"))
    }

    @Test fun `top level platform variant table fails clearly`() {
        val result = setupProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"

                [android.debug]
                api_key = "value"
            """.trimIndent()
        ).buildAndFail()

        assertTrue(result.output.contains("android.debug"))
        assertTrue(result.output.contains("Invalid override table"))
    }

    @Test fun `dotted override key fails clearly`() {
        val result = setupProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"

                [overrides.android]
                nested.api_key = "value"
            """.trimIndent()
        ).buildAndFail()

        assertTrue(result.output.contains("overrides.android.nested"))
        assertTrue(result.output.contains("Invalid override table"))
    }

    @Test fun `invalid typed variant override value fails clearly`() {
        val result = setupProject(
            tomlContent = """
                [properties.retry_count]
                type = "Int"
                default = "3"

                [overrides.android.debug]
                retry_count = "abc"
            """.trimIndent(),
            extraConfig = """
                kenvy {
                    platform.set("android")
                    variant.set("debug")
                }
            """.trimIndent()
        ).buildAndFail()

        assertTrue(result.output.contains("retry_count"))
        assertTrue(result.output.contains("Type mismatches:"))
        assertTrue(result.output.contains("expected Int"))
    }

    @Test fun `second build is UP-TO-DATE when kenvy toml and local properties are unchanged`() {
        val runner = setupProject(localPropertiesContent = "api_key=stable-value")
        runner.build()
        val result = runner.build()
        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":generateKenvy")?.outcome)
    }

    @Test fun `local properties changes invalidate generated output`() {
        val runner = setupProject(localPropertiesContent = "api_key=first-value")
        runner.build()

        File(projectDir, "local.properties").writeText("api_key=second-value")
        val result = runner.build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertTrue(generatedContent().contains("val apiKey: String = \"second-value\""))
    }

    @Test fun `local properties created after configuration cache build is used`() {
        val runner = setupProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "bootstrap-default"
            """.trimIndent(),
            arguments = listOf("generateKenvy", "--configuration-cache")
        )
        runner.build()

        File(projectDir, "local.properties").writeText("api_key=late-local-value")
        val result = runner.build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertTrue(generatedContent().contains("val apiKey: String = \"late-local-value\""))
    }

    @Test fun `safe environment name collision fails clearly`() {
        val result = setupProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "a"

                [properties.api-key]
                type = "String"
                default = "b"
            """.trimIndent()
        ).buildAndFail()

        assertTrue(result.output.contains("same environment variable"))
        assertTrue(result.output.contains("KENVY_API_KEY"))
    }

    @Test fun `unprefixed environment variable is ignored by default`() {
        val result = setupProject(
            environment = mapOf("API_KEY" to "ignored-env-value")
        ).buildAndFail()

        assertTrue(result.output.contains("Missing values:"))
        assertTrue(result.output.contains("+ api_key=<YOUR_VALUE>"))
    }

    @Test fun `legacy opt in consumes unprefixed environment variable intentionally`() {
        val result = setupProject(
            extraConfig = """
                kenvy {
                    legacyUnprefixedEnvironmentOverrides.set(true)
                }
            """.trimIndent(),
            environment = mapOf("API_KEY" to "legacy-env-value")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertTrue(generatedContent().contains("val apiKey: String = \"legacy-env-value\""))
    }

    @Test fun `task level legacy opt in consumes unprefixed environment variable intentionally`() {
        val result = setupProject(
            extraConfig = """
                tasks.named<io.github.adriandleon.kenvy.GenerateKenvyTask>("generateKenvy") {
                    legacyUnprefixedEnvironmentOverrides.set(true)
                }
            """.trimIndent(),
            environment = mapOf("API_KEY" to "task-legacy-env-value")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertTrue(generatedContent().contains("val apiKey: String = \"task-legacy-env-value\""))
    }

    @Test fun `safe prefixed environment variable wins over legacy value when opt in is enabled`() {
        val result = setupProject(
            extraConfig = """
                kenvy {
                    legacyUnprefixedEnvironmentOverrides.set(true)
                }
            """.trimIndent(),
            environment = mapOf(
                "API_KEY" to "legacy-env-value",
                "KENVY_API_KEY" to "safe-env-value"
            )
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertTrue(generatedContent().contains("val apiKey: String = \"safe-env-value\""))
    }

    @Test fun `known build system collision fails with actionable diagnostic`() {
        val result = setupProject(
            tomlContent = """
                [properties.platform_name]
                type = "String"
                default = "placeholder"
            """.trimIndent(),
            environment = mapOf("PLATFORM_NAME" to "iphonesimulator"),
            arguments = listOf("generateKenvy", "--warn")
        ).buildAndFail()

        assertTrue(result.output.contains("Resolution conflicts:"))
        assertTrue(result.output.contains("platform_name"))
        assertTrue(result.output.contains("PLATFORM_NAME"))
        assertTrue(result.output.contains("KENVY_PLATFORM_NAME"))
        assertTrue(result.output.contains("legacyUnprefixedEnvironmentOverrides"))
    }

    @Test fun `known build system collision does not fail when local properties resolve value`() {
        val result = setupProject(
            tomlContent = """
                [properties.platform_name]
                type = "String"
                default = "placeholder"
            """.trimIndent(),
            localPropertiesContent = "platform_name=local-ios",
            environment = mapOf("PLATFORM_NAME" to "iphonesimulator")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertTrue(generatedContent().contains("val platformName: String = \"local-ios\""))
    }

    @Test fun `safe prefixed environment variable wins over known build system collision`() {
        val result = setupProject(
            tomlContent = """
                [properties.platform_name]
                type = "String"
                default = "placeholder"
            """.trimIndent(),
            environment = mapOf(
                "PLATFORM_NAME" to "iphonesimulator",
                "KENVY_PLATFORM_NAME" to "consumer-ios"
            )
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertTrue(generatedContent().contains("val platformName: String = \"consumer-ios\""))
    }

    @Test fun `direct GenerateKenvyTask users get default empty environment values`() {
        val result = setupProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "direct-default"
            """.trimIndent(),
            extraConfig = """
                tasks.register<io.github.adriandleon.kenvy.GenerateKenvyTask>("manualGenerate") {
                    configFile.set(layout.projectDirectory.file("kenvy.toml"))
                    packageName.set("com.example.test")
                    interfaceName.set("ManualKenvy")
                    platform.set("android")
                    variant.set("debug")
                    outputDir.set(layout.buildDirectory.dir("manual-generated"))
                    localPropertiesFiles.from(layout.projectDirectory.file("local.properties"))
                }
            """.trimIndent(),
            arguments = listOf("manualGenerate")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":manualGenerate")?.outcome)
        val content = File(projectDir, "build/manual-generated/com/example/test/ManualKenvy.kt").readText()
        assertTrue(content.contains("val apiKey: String = \"direct-default\""))
    }

    @Test fun `second build is UP-TO-DATE when platform and variant inputs are unchanged`() {
        val runner = setupProject(
            tomlContent = """
                [properties.base_url]
                type = "String"
                default = "https://default.example.com"

                [overrides.android.debug]
                base_url = "https://android-debug.example.com"
            """.trimIndent(),
            extraConfig = """
                kenvy {
                    platform.set("android")
                    variant.set("debug")
                }
            """.trimIndent()
        )
        runner.build()
        val result = runner.build()

        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":generateKenvy")?.outcome)
    }
}
