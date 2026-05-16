package io.github.adriandleon.kenvy

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KenvyActionableErrorFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun setupProject(
        tomlContent: String,
        localPropertiesContent: String? = null,
        arguments: List<String> = listOf("generateKenvy")
    ): GradleRunner {
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"test\"")
        File(projectDir, "build.gradle.kts").writeText("""
            plugins { id("io.github.adriandleon.kenvy") }
            group = "com.example.test"
        """.trimIndent())
        File(projectDir, "kenvy.toml").writeText(tomlContent)
        if (localPropertiesContent != null) {
            File(projectDir, "local.properties").writeText(localPropertiesContent)
        }
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(arguments)
    }

    @Test fun `generateKenvy fails with grouped diff snippets for unresolved placeholders`() {
        val result = setupProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"
                help_url = "https://wiki.internal/api-setup"

                [properties.retry_count]
                type = "Int"
                default = "<YOUR_VALUE>"
            """.trimIndent()
        ).buildAndFail()

        assertTrue(result.output.contains("Kenvy: Configuration has 2 issue(s)."))
        assertTrue(result.output.contains("Missing values:"))
        assertTrue(result.output.contains("+ api_key=<YOUR_VALUE>"))
        assertTrue(result.output.contains("+ retry_count=<YOUR_VALUE>"))
        assertTrue(result.output.contains("Setup: https://wiki.internal/api-setup"))
    }

    @Test fun `generateKenvy emits no actionable report heading for clean resolved build`() {
        val result = setupProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"
            """.trimIndent(),
            localPropertiesContent = "api_key=live-value"
        ).build()

        assertFalse(result.output.contains("Kenvy: Configuration has"))
        assertFalse(result.output.contains("Missing values:"))
        assertFalse(result.output.contains("Type mismatches:"))
        assertFalse(result.output.contains("Resolution conflicts:"))
    }
}
