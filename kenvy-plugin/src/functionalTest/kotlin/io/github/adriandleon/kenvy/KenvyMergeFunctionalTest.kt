package io.github.adriandleon.kenvy

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KenvyMergeFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private val baseToml = """
        [properties.api_key]
        type = "String"
        default = "placeholder"

        [properties.retry_count]
        type = "Int"
        default = "3"
    """.trimIndent()

    private val tomlWithCommon = """
        [properties.api_key]
        type = "String"
        default = "placeholder"

        [properties.retry_count]
        type = "Int"
        default = "3"

        [overrides.common]
        api_key = "common-key"
    """.trimIndent()

    private fun setup(
        tomlContent: String = baseToml,
        localPropertiesContent: String? = null,
        extraConfig: String = "",
        arguments: List<String> = listOf("mergeKenvy"),
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

    private fun localPropertiesContent(): String =
        File(projectDir, "local.properties").readText()

    private fun localPropertiesExists(): Boolean =
        File(projectDir, "local.properties").exists()

    // AC1, AC5 — generateKenvy does not mutate local.properties
    @Test fun `generateKenvy does not modify local properties`() {
        val original = "api_key=my-secret\nCUSTOM_KEY=custom-value\n"
        val result = setup(
            localPropertiesContent = original,
            arguments = listOf("generateKenvy")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertEquals(original, File(projectDir, "local.properties").readText())
    }

    @Test fun `generateKenvy does not delete local-only keys from local properties`() {
        setup(
            localPropertiesContent = "api_key=secret\nMY_TOKEN=token-value\n",
            arguments = listOf("generateKenvy")
        ).build()

        val content = localPropertiesContent()
        assertTrue(content.contains("MY_TOKEN=token-value"))
        assertTrue(content.contains("api_key=secret"))
    }

    // AC1, AC5 — mergeKenvy preserves local-only keys
    @Test fun `mergeKenvy preserves local-only keys not in contract`() {
        setup(
            localPropertiesContent = "api_key=secret\nCUSTOM_TOKEN=my-token\n"
        ).build()

        val content = localPropertiesContent()
        assertTrue(content.contains("CUSTOM_TOKEN=my-token"), "Expected CUSTOM_TOKEN to be preserved")
        assertTrue(content.contains("api_key=secret"), "Expected api_key to remain")
    }

    @Test fun `mergeKenvy preserves scoped local-only keys not in contract`() {
        setup(
            localPropertiesContent = "api_key=secret\napi_key.android.debug=android-debug-local\napi_key.ios.release=ios-release-local\n"
        ).build()

        val content = localPropertiesContent()
        assertTrue(content.contains("api_key=secret"))
        assertTrue(content.contains("api_key.android.debug=android-debug-local"))
        assertTrue(content.contains("api_key.ios.release=ios-release-local"))
    }

    // AC2 — new contract property with default added when absent locally
    @Test fun `mergeKenvy adds new contract property when absent from local properties`() {
        setup(
            localPropertiesContent = "api_key=my-secret"
        ).build()

        val content = localPropertiesContent()
        assertTrue(content.contains("retry_count=3"), "Expected new property retry_count=3 to be added")
        assertTrue(content.contains("api_key=my-secret"), "Expected existing api_key to remain")
    }

    @Test fun `mergeKenvy adds contract default when local properties does not exist`() {
        setup().build()

        assertTrue(localPropertiesExists())
        val content = localPropertiesContent()
        assertTrue(content.contains("api_key=placeholder"))
        assertTrue(content.contains("retry_count=3"))
    }

    // AC3 — local values win conflicts by default
    @Test fun `mergeKenvy local value beats contract default`() {
        setup(
            localPropertiesContent = "api_key=my-local-value\nretry_count=99"
        ).build()

        val content = localPropertiesContent()
        assertTrue(content.contains("api_key=my-local-value"))
        assertTrue(content.contains("retry_count=99"))
    }

    @Test fun `mergeKenvy local value beats common override`() {
        setup(
            tomlContent = tomlWithCommon,
            localPropertiesContent = "api_key=my-local-value"
        ).build()

        val content = localPropertiesContent()
        assertTrue(content.contains("api_key=my-local-value"), "Local value should win over common override")
        assertFalse(content.contains("common-key"), "Common override value must not appear")
    }

    @Test fun `mergeKenvy local value beats platform override`() {
        val tomlWithPlatform = """
            [properties.base_url]
            type = "String"
            default = "https://default.example.com"

            [overrides.android]
            base_url = "https://android.example.com"
        """.trimIndent()

        setup(
            tomlContent = tomlWithPlatform,
            localPropertiesContent = "base_url=https://local.example.com",
            extraConfig = """kenvy { platform.set("android") }"""
        ).build()

        val content = localPropertiesContent()
        assertTrue(content.contains("base_url=https://local.example.com"))
        assertFalse(content.contains("https://android.example.com"))
    }

    // AC4 — force behavior via task configuration
    @Test fun `mergeKenvy force overwrites only specified key`() {
        setup(
            localPropertiesContent = "api_key=old-local\nretry_count=99",
            extraConfig = """
                tasks.named<io.github.adriandleon.kenvy.MergeKenvyTask>("mergeKenvy") {
                    forceKeys.set(setOf("api_key"))
                }
            """.trimIndent()
        ).build()

        val content = localPropertiesContent()
        assertTrue(content.contains("api_key=placeholder"), "Force should overwrite api_key to contract default")
        assertTrue(content.contains("retry_count=99"), "retry_count must not be overwritten by force")
    }

    @Test fun `mergeKenvy force leaves local-only keys untouched`() {
        setup(
            localPropertiesContent = "api_key=old-local\nMY_CUSTOM_KEY=keep-me",
            extraConfig = """
                tasks.named<io.github.adriandleon.kenvy.MergeKenvyTask>("mergeKenvy") {
                    forceKeys.set(setOf("api_key"))
                }
            """.trimIndent()
        ).build()

        val content = localPropertiesContent()
        assertTrue(content.contains("MY_CUSTOM_KEY=keep-me"), "Local-only key must survive force merge")
    }

    @Test fun `mergeKenvy force emits warning with property name but not value`() {
        val result = setup(
            localPropertiesContent = "api_key=super-secret-value",
            extraConfig = """
                tasks.named<io.github.adriandleon.kenvy.MergeKenvyTask>("mergeKenvy") {
                    forceKeys.set(setOf("api_key"))
                }
            """.trimIndent(),
            arguments = listOf("mergeKenvy", "--warn")
        ).build()

        assertTrue(result.output.contains("api_key"), "Warning should include property name")
        assertTrue(result.output.contains("source: default"), "Warning should include replacement provenance")
        assertFalse(result.output.contains("super-secret-value"), "Warning must not expose local value")
    }

    @Test fun `mergeKenvy unknown force key fails clearly`() {
        val result = setup(
            extraConfig = """
                tasks.named<io.github.adriandleon.kenvy.MergeKenvyTask>("mergeKenvy") {
                    forceKeys.set(setOf("unknown_property"))
                }
            """.trimIndent()
        ).buildAndFail()

        assertTrue(result.output.contains("unknown_property"))
        assertTrue(result.output.contains("not declared in the contract"))
    }

    // AC5 — environment values do not appear in merged local.properties
    @Test fun `mergeKenvy does not persist environment variable values into local properties`() {
        setup(
            environment = mapOf("KENVY_API_KEY" to "env-secret-value")
        ).build()

        val content = localPropertiesContent()
        assertFalse(content.contains("env-secret-value"), "Environment value must not be persisted to local.properties")
        assertTrue(content.contains("api_key=placeholder"), "Contract default should be used instead of env value")
    }

    // UP-TO-DATE — second no-change build
    @Test fun `mergeKenvy second build is UP-TO-DATE when nothing changes`() {
        val runner = setup(
            localPropertiesContent = "api_key=my-key\nretry_count=5"
        )
        runner.build()
        val result = runner.build()
        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":mergeKenvy")?.outcome)
    }

    // Comment and order preservation
    @Test fun `mergeKenvy preserves comment lines in local properties`() {
        setup(
            localPropertiesContent = "# My local config\napi_key=my-key\n# End of file\n"
        ).build()

        val content = localPropertiesContent()
        assertTrue(content.contains("# My local config"), "Comment lines must be preserved")
        assertTrue(content.contains("# End of file"), "Trailing comment must be preserved")
        assertTrue(content.contains("api_key=my-key"), "Property value must be preserved")
    }

    @Test fun `mergeKenvy re-runs when contract changes`() {
        val runner = setup(localPropertiesContent = "api_key=my-key")
        runner.build()

        File(projectDir, "kenvy.toml").writeText("""
            [properties.api_key]
            type = "String"
            default = "new-placeholder"

            [properties.timeout]
            type = "Int"
            default = "30"
        """.trimIndent())

        val result = runner.build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":mergeKenvy")?.outcome)
        assertTrue(localPropertiesContent().contains("timeout=30"))
    }

    @Test fun `mergeKenvy preserves escaped local property values when rewriting`() {
        setup(
            localPropertiesContent = "api_key=abc\\\\\\\\def\nCUSTOM_TOKEN=one\\\\:two\n"
        ).build()

        val content = localPropertiesContent()
        assertTrue(content.contains("api_key=abc\\\\\\\\def"), "Backslash escape must survive rewrite")
        assertTrue(content.contains("CUSTOM_TOKEN=one\\\\:two"), "Escaped colon must survive rewrite")
    }
}
