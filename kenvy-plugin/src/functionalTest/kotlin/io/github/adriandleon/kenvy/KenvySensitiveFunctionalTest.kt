package io.github.adriandleon.kenvy

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class KenvySensitiveFunctionalTest {

    @TempDir lateinit var projectDir: File

    private val sentinel = "SUPER_SECRET_12345_DO_NOT_LOG"

    private fun setup(
        toml: String,
        localProperties: String? = null,
        arguments: List<String>,
        environment: Map<String, String>? = null
    ): GradleRunner {
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"test\"")
        File(projectDir, "build.gradle.kts").writeText("""
            plugins { id("io.github.adriandleon.kenvy") }
            group = "com.example.test"
        """.trimIndent())
        File(projectDir, "kenvy.toml").writeText(toml)
        if (localProperties != null) {
            File(projectDir, "local.properties").writeText(localProperties)
        }
        val runner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(arguments)
        return if (environment != null) runner.withEnvironment(environment) else runner
    }

    // AC1 + AC2: generateKenvy --info with sensitive local.properties value does not leak secret
    @Test fun `generateKenvy --info does not log sensitive local properties value`() {
        val toml = """
            [properties.api_key]
            type = "String"
            default = "placeholder"
            sensitive = true
        """.trimIndent()
        val result = setup(
            toml = toml,
            localProperties = "api_key=$sentinel",
            arguments = listOf("generateKenvy", "--info")
        ).build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertFalse(result.output.contains(sentinel),
            "Sensitive local.properties value must not appear in --info output")
    }

    // AC2: generateKenvy --debug with sensitive env value does not leak secret
    @Test fun `generateKenvy --debug does not log sensitive environment value`() {
        val toml = """
            [properties.api_key]
            type = "String"
            default = "placeholder"
            sensitive = true
        """.trimIndent()
        val result = setup(
            toml = toml,
            arguments = listOf("generateKenvy", "--debug"),
            environment = mapOf("KENVY_API_KEY" to sentinel)
        ).build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertFalse(result.output.contains(sentinel),
            "Sensitive environment value must not appear in --debug output")
    }

    // AC3: non-sensitive output still appears when expected
    @Test fun `non-sensitive properties do not get masked in output`() {
        val toml = """
            [properties.base_url]
            type = "String"
            default = "https://api.example.com"
            sensitive = false

            [properties.api_key]
            type = "String"
            default = "placeholder"
            sensitive = true
        """.trimIndent()
        val result = setup(
            toml = toml,
            localProperties = "api_key=resolved-secret",
            arguments = listOf("generateKenvy", "--info")
        ).build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        // Generated Kotlin file contains raw values (by design)
        val generated = File(projectDir, "build/generated/kenvy/commonMain/kotlin/com/example/test/Kenvy.kt").readText()
        assertTrue(generated.contains("https://api.example.com"), "Non-sensitive value must appear in generated output")
    }

    // AC4: unresolved sensitive placeholder failure shows name and help URL but not the raw default value
    @Test fun `unresolved sensitive placeholder failure does not expose raw default value`() {
        // Use "replace_me" — a valid placeholder sentinel that does not appear in the standard error text
        val toml = """
            [properties.api_key]
            type = "String"
            default = "replace_me"
            sensitive = true
            help_url = "https://wiki.internal/api-setup"
        """.trimIndent()
        val result = setup(
            toml = toml,
            arguments = listOf("generateKenvy", "--warn")
        ).buildAndFail()
        assertTrue(result.output.contains("api_key"), "Failure must include property name")
        assertTrue(result.output.contains("https://wiki.internal/api-setup"), "Failure must include help URL")
        assertFalse(result.output.contains("replace_me"),
            "Raw default value must not appear in failure output for sensitive property")
    }

    // AC5: example generation masks sensitive shared override value
    @Test fun `generateKenvyExample masks sensitive common override value`() {
        val toml = """
            [properties.api_key]
            type = "String"
            default = "placeholder"
            sensitive = true

            [overrides.common]
            api_key = "$sentinel"
        """.trimIndent()
        val result = setup(
            toml = toml,
            arguments = listOf("generateKenvyExample", "--warn")
        ).build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyExample")?.outcome)
        val exampleContent = File(projectDir, "local.properties.example").readText()
        assertFalse(exampleContent.contains(sentinel),
            "Sensitive shared override must be masked in example file")
        assertTrue(exampleContent.contains("****"),
            "Example must show **** placeholder for sensitive shared override")
    }

    // AC5: non-sensitive common override still appears in example
    @Test fun `generateKenvyExample keeps non-sensitive shared override visible`() {
        val toml = """
            [properties.base_url]
            type = "String"
            default = "https://default.example.com"
            sensitive = false

            [overrides.common]
            base_url = "https://shared.example.com"
        """.trimIndent()
        val result = setup(
            toml = toml,
            arguments = listOf("generateKenvyExample", "--warn")
        ).build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyExample")?.outcome)
        val exampleContent = File(projectDir, "local.properties.example").readText()
        assertTrue(exampleContent.contains("https://shared.example.com"),
            "Non-sensitive shared override must remain visible in example file")
    }

    // AC6: mergeKenvy force overwrite warning does not expose sensitive values
    @Test fun `mergeKenvy force overwrite warning does not include sensitive values`() {
        val toml = """
            [properties.api_key]
            type = "String"
            default = "placeholder"
            sensitive = true

            [overrides.common]
            api_key = "$sentinel"
        """.trimIndent()
        val extraConfig = """
            tasks.named<io.github.adriandleon.kenvy.MergeKenvyTask>("mergeKenvy") {
                forceKeys.set(setOf("api_key"))
            }
        """.trimIndent()
        val result = setup(
            toml = toml,
            localProperties = "api_key=old-value",
            arguments = listOf("mergeKenvy", "--warn", "--rerun-tasks")
        ).also {
            // Overwrite build.gradle.kts to add forceKeys task config
            File(projectDir, "build.gradle.kts").writeText("""
                plugins { id("io.github.adriandleon.kenvy") }
                group = "com.example.test"
                $extraConfig
            """.trimIndent())
        }.let {
            GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("mergeKenvy", "--warn", "--rerun-tasks")
                .build()
        }
        assertFalse(result.output.contains(sentinel),
            "Sensitive value must not appear in mergeKenvy force-overwrite warning")
        assertTrue(result.output.contains("api_key"),
            "Property name must appear in force-overwrite warning")
    }

    // AC6: generateKenvy still produces correct Kotlin constants for sensitive props
    @Test fun `generateKenvy still generates correct Kotlin constant for sensitive property`() {
        val toml = """
            [properties.api_key]
            type = "String"
            default = "placeholder"
            sensitive = true
        """.trimIndent()
        val result = setup(
            toml = toml,
            localProperties = "api_key=$sentinel",
            arguments = listOf("generateKenvy")
        ).build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        val generated = File(projectDir, "build/generated/kenvy/commonMain/kotlin/com/example/test/Kenvy.kt").readText()
        assertTrue(generated.contains(sentinel),
            "Generated Kotlin constant must contain real value (not masked) — this is the build artifact")
    }
}
