package io.github.adriandleon.kenvy

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KenvyExampleFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private val placeholderToml = """
        [properties.api_key]
        type = "String"
        default = "placeholder"
        description = "Backend API key"
        help_url = "https://wiki.internal/api-setup"

        [properties.base_url]
        type = "String"
        default = "https://api.example.com"
    """.trimIndent()

    private val epic1Toml = """
        [properties.api_key]
        type = "String"
        default = "placeholder"

        [properties.retry_count]
        type = "Int"
        default = "3"
    """.trimIndent()

    private fun setup(
        tomlContent: String = placeholderToml,
        localPropertiesContent: String? = null,
        extraConfig: String = "",
        arguments: List<String> = listOf("generateKenvyExample"),
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

    private fun exampleFileContent(): String =
        File(projectDir, "local.properties.example").readText()

    private fun exampleFileExists(): Boolean =
        File(projectDir, "local.properties.example").exists()

    // AC1 — generateKenvyExample creates local.properties.example in project root
    @Test fun `generateKenvyExample creates local properties example in project root`() {
        val result = setup().build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyExample")?.outcome)
        assertTrue(exampleFileExists(), "local.properties.example must be created")
    }

    // AC1 — generateKenvy also produces the example file (via dependsOn)
    @Test fun `generateKenvy produces local properties example`() {
        val result = setup(arguments = listOf("generateKenvy")).buildAndFail()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyExample")?.outcome)
        assertTrue(exampleFileExists(), "local.properties.example must be created when running generateKenvy")
        assertTrue(result.output.contains("Missing values:"), "generateKenvy should now fail for unresolved placeholders")
    }

    // AC2 — placeholder property appears with metadata comments and blank value
    @Test fun `example file contains placeholder property with type and metadata comments`() {
        setup().build()

        val content = exampleFileContent()
        assertTrue(content.contains("# api_key (String)"), "Type comment must be present")
        assertTrue(content.contains("# Backend API key"), "Description must be present")
        assertTrue(content.contains("# Setup: https://wiki.internal/api-setup"), "Help URL must be present")
        assertTrue(content.lines().any { it.trim() == "api_key=" }, "Placeholder entry must be blank assignment")
    }

    // AC3 — non-placeholder with common override is comment-only
    @Test fun `common override value appears as comment not active assignment`() {
        val toml = """
            [properties.base_url]
            type = "String"
            default = "https://api.example.com"

            [overrides.common]
            base_url = "https://api.shared.example.com"
        """.trimIndent()

        setup(tomlContent = toml).build()

        val content = exampleFileContent()
        assertTrue(content.contains("https://api.shared.example.com"), "Effective value must appear in example")
        assertFalse(content.lines().any { it.trim() == "base_url=https://api.shared.example.com" },
            "Non-placeholder must not appear as active assignment")
        assertTrue(content.contains("common_override"), "Source label must appear")
    }

    // AC4 — second no-change build reports UP-TO-DATE
    @Test fun `second no-change build reports UP-TO-DATE for example generation task`() {
        val runner = setup()
        runner.build()
        val result = runner.build()

        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":generateKenvyExample")?.outcome)
    }

    // AC4 — task re-runs when kenvy.toml changes
    @Test fun `example generation task re-runs when kenvy toml changes`() {
        val runner = setup()
        runner.build()

        File(projectDir, "kenvy.toml").writeText("""
            [properties.new_key]
            type = "String"
            default = "placeholder"
        """.trimIndent())

        val result = runner.build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyExample")?.outcome)
        assertTrue(exampleFileContent().contains("new_key="), "Updated example must include new key")
    }

    // AC5 — copying example to local.properties and filling values resolves placeholders
    @Test fun `copying example and filling values resolves placeholders in generateKenvy`() {
        setup().build()
        // Simulate developer copying and filling in the value
        File(projectDir, "local.properties").writeText("api_key=my-real-key\n")

        val result = setup(
            arguments = listOf("generateKenvy"),
            localPropertiesContent = "api_key=my-real-key"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        // Verify the generated Kotlin object contains the filled-in value
        val generatedDir = File(projectDir, "build/generated/kenvy/commonMain/kotlin")
        val generatedFile = generatedDir.walkTopDown().firstOrNull { it.extension == "kt" }
        assertTrue(generatedFile != null && generatedFile.readText().contains("my-real-key"),
            "Generated Kotlin must include the value from local.properties")
    }

    // AC6 — Epic 1 contract with no overrides still generates an example
    @Test fun `Epic 1 style contract with no overrides generates example successfully`() {
        val result = setup(tomlContent = epic1Toml).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyExample")?.outcome)
        val content = exampleFileContent()
        assertTrue(content.contains("api_key="), "Placeholder property must appear in example")
        assertTrue(content.contains("retry_count"), "Non-placeholder property must appear in example")
    }

    // AC6 — environment variables are not written to local.properties.example
    @Test fun `environment variables do not appear in local properties example`() {
        setup(
            environment = mapOf("KENVY_API_KEY" to "env-secret-value")
        ).build()

        val content = exampleFileContent()
        assertFalse(content.contains("env-secret-value"),
            "Environment variable values must not appear in local.properties.example")
        // Placeholder must remain blank
        assertTrue(content.lines().any { it.trim() == "api_key=" },
            "Placeholder entry must remain blank despite env var being set")
    }

    // AC1 — existing mergeKenvy behavior remains unchanged
    @Test fun `mergeKenvy behavior is unchanged after example generation task is added`() {
        setup(
            tomlContent = epic1Toml,
            localPropertiesContent = "api_key=my-secret",
            arguments = listOf("mergeKenvy")
        ).build()

        val localProps = File(projectDir, "local.properties").readText()
        assertTrue(localProps.contains("api_key=my-secret"), "mergeKenvy must not overwrite existing local value")
        assertTrue(localProps.contains("retry_count=3"), "mergeKenvy must add missing contract property")
    }

    // generateKenvy does not mutate local.properties (regression guard)
    @Test fun `generateKenvy does not mutate local properties when example is also generated`() {
        val original = "api_key=my-secret\nCUSTOM_KEY=custom\n"
        setup(
            localPropertiesContent = original,
            arguments = listOf("generateKenvy")
        ).build()

        assertEquals(original, File(projectDir, "local.properties").readText(),
            "local.properties must not be mutated by generateKenvy")
    }
}
