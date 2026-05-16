package io.github.adriandleon.kenvy

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KenvyMetadataFunctionalTest {

    @TempDir lateinit var projectDir: File

    private fun setupProject(tomlContent: String): GradleRunner {
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"test\"")
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins { id("io.github.adriandleon.kenvy") }
            group = "com.example.test"
            """.trimIndent()
        )
        File(projectDir, "kenvy.toml").writeText(tomlContent)
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generateKenvy", "--warn")
    }

    @Test fun `placeholder property with help_url includes URL in task-time error`() {
        val result = setupProject("""
            [properties.api_key]
            type = "String"
            default = "placeholder"
            help_url = "https://wiki.internal/api-setup"
        """.trimIndent()).buildAndFail()
        assertTrue(result.output.contains("api_key"), "Error must name the property")
        assertTrue(result.output.contains("https://wiki.internal/api-setup"), "Error must include help URL")
    }

    @Test fun `placeholder property without help_url emits name only`() {
        val result = setupProject("""
            [properties.secret]
            type = "String"
            default = "placeholder"
        """.trimIndent()).buildAndFail()
        assertTrue(result.output.contains("secret"), "Error must name the property")
        assertFalse(result.output.contains("Setup:"), "No Setup line when help_url is absent")
    }

    @Test fun `non-placeholder property with help_url emits no warning`() {
        val result = setupProject("""
            [properties.base_url]
            type = "String"
            default = "https://api.example.com"
            help_url = "https://docs.example.com"
        """.trimIndent()).build()
        assertFalse(result.output.contains("base_url"), "No warning for property with valid default")
        assertFalse(result.output.contains("docs.example.com"), "Help URL not surfaced for valid defaults")
    }

    @Test fun `property with description only parses without error`() {
        val result = setupProject("""
            [properties.api_key]
            type = "String"
            default = "placeholder"
            description = "The API key for the backend service"
        """.trimIndent()).buildAndFail()
        assertTrue(result.output.contains("api_key"), "Error still emitted for placeholder with description")
        assertFalse(result.output.contains("Setup:"), "Description does not appear in error output")
    }

    @Test fun `placeholder property with empty help_url does not show Setup line`() {
        val result = setupProject("""
            [properties.api_key]
            type = "String"
            default = "placeholder"
            help_url = ""
        """.trimIndent()).buildAndFail()
        assertTrue(result.output.contains("api_key"), "Error must name the property")
        assertFalse(result.output.contains("Setup:"), "Empty help_url must not produce Setup line")
    }

    @Test fun `property with empty description parses without error`() {
        val result = setupProject("""
            [properties.api_key]
            type = "String"
            default = "placeholder"
            description = ""
        """.trimIndent()).buildAndFail()
        assertTrue(result.output.contains("api_key"), "Error still emitted for placeholder")
    }

    @Test fun `non-string help_url value fails with error`() {
        val result = setupProject("""
            [properties.api_key]
            type = "String"
            default = "placeholder"
            help_url = 123
        """.trimIndent()).buildAndFail()
        assertTrue(result.output.contains("has a non-string value"),
            "Should report non-string error")
    }
}
