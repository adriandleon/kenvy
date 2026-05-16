package io.github.adriandleon.kenvy

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KenvyParserFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun setupMultiProject(tomlContent: String?, arguments: List<String> = listOf(":shared:generateKenvyExample")): GradleRunner {
        File(projectDir, "settings.gradle.kts").writeText(
            "rootProject.name = \"test-root\"\ninclude(\":shared\")"
        )
        File(projectDir, "build.gradle.kts").writeText("")

        val sharedDir = File(projectDir, "shared").also { it.mkdirs() }
        File(sharedDir, "build.gradle.kts").writeText(
            "plugins { id(\"io.github.adriandleon.kenvy\") }\ngroup = \"com.example.shared\""
        )

        if (tomlContent != null) {
            File(sharedDir, "kenvy.toml").writeText(tomlContent)
        }

        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(arguments)
    }

    private fun setupProject(tomlContent: String?): GradleRunner {
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"test\"")
        File(projectDir, "build.gradle.kts").writeText("""
            plugins {
                id("io.github.adriandleon.kenvy")
            }
            group = "com.example.test"
        """.trimIndent())
        
        if (tomlContent != null) {
            File(projectDir, "kenvy.toml").writeText(tomlContent)
        }
        
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
    }

    @Test
    fun `valid toml with all four types parses without error`() {
        val result = setupProject("""
            [properties.api_key]
            type = "String"
            [properties.count]
            type = "Int"
            default = "5"
            [properties.enabled]
            type = "Boolean"
            [properties.timeout]
            type = "Long"
        """.trimIndent()).withArguments("generateKenvyExample").build()
        
        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `unsupported type produces named build error`() {
        val result = setupProject("""
            [properties.bad_prop]
            type = "Double"
        """.trimIndent()).withArguments("generateKenvyExample").buildAndFail()
        
        assertAll(
            { assertTrue(result.output.contains("bad_prop"), "Error must name the property") },
            { assertTrue(result.output.contains("Double"), "Error must name the invalid type") }
        )
    }

    @Test
    fun `malformed toml produces descriptive build error`() {
        val result = setupProject("[[invalid toml that cannot be parsed")
            .withArguments("generateKenvyExample")
            .buildAndFail()
            
        assertTrue(result.output.contains("kenvy.toml"), "Error must mention kenvy.toml")
    }

    @Test
    fun `missing kenvy_toml produces descriptive build error`() {
        val result = setupProject(null)
            .withArguments("generateKenvyExample")
            .buildAndFail()
            
        assertTrue(result.output.contains("kenvy.toml"), "Error must mention kenvy.toml")
        assertTrue(result.output.contains("Kenvy: kenvy.toml not found"), "Error must come from Kenvy")
        assertFalse(
            result.output.contains("Could not determine the dependencies"),
            "Missing config must not fail during task dependency resolution"
        )
    }

    @Test
    fun `missing kenvy_toml in generateKenvy fails during task execution`() {
        val result = setupProject(null)
            .withArguments("generateKenvy")
            .buildAndFail()

        assertTrue(result.output.contains("Kenvy: kenvy.toml not found"), "Error must come from Kenvy")
        assertFalse(
            result.output.contains("Could not determine the dependencies"),
            "Missing config must not fail during task dependency resolution"
        )
    }

    @Test
    fun `missing kenvy_toml in module build names exact path and gradle project`() {
        val result = setupMultiProject(null, listOf(":shared:generateKenvyExample"))
            .buildAndFail()

        assertAll(
            { assertTrue(result.output.contains("Kenvy: kenvy.toml not found"), "Error must come from Kenvy") },
            { assertTrue(result.output.contains("shared/kenvy.toml") || result.output.contains("shared${File.separator}kenvy.toml"), "Error must name the exact path") },
            { assertTrue(result.output.contains(":shared"), "Error must name the Gradle project") },
            { assertTrue(result.output.contains("relative to the Gradle project/module"), "Error must explain default path resolution") },
            { assertFalse(result.output.contains("Could not determine the dependencies"), "Missing config must not fail during dependency resolution") }
        )
    }

    @Test
    fun `missing kenvy_toml in module generateKenvy names exact path and project`() {
        val result = setupMultiProject(null, listOf(":shared:generateKenvy", "-x", ":shared:generateKenvyExample"))
            .buildAndFail()

        assertAll(
            { assertTrue(result.output.contains("Kenvy: kenvy.toml not found"), "Error must come from Kenvy") },
            { assertTrue(result.output.contains("Execution failed for task ':shared:generateKenvy'"), "Test must exercise GenerateKenvyTask, not generateKenvyExample") },
            { assertTrue(result.output.contains(":shared"), "Error must name the Gradle project") },
            { assertFalse(result.output.contains("Could not determine the dependencies"), "Missing config must not fail during dependency resolution") }
        )
    }

    @Test
    fun `missing kenvy_toml in mergeKenvy names exact path and project`() {
        val result = setupMultiProject(null, listOf(":shared:mergeKenvy"))
            .buildAndFail()

        assertAll(
            { assertTrue(result.output.contains("Kenvy: kenvy.toml not found"), "Error must come from Kenvy") },
            { assertTrue(result.output.contains("Execution failed for task ':shared:mergeKenvy'"), "Test must exercise MergeKenvyTask") },
            { assertTrue(result.output.contains("shared/kenvy.toml") || result.output.contains("shared${File.separator}kenvy.toml"), "Error must name the exact path") },
            { assertTrue(result.output.contains(":shared"), "Error must name the Gradle project") },
            { assertTrue(result.output.contains("relative to the Gradle project/module"), "Error must explain default path resolution") }
        )
    }

    @Test
    fun `missing custom configFile names configured path not root build directory`() {
        val sharedDir = File(projectDir, "shared").also { it.mkdirs() }
        File(projectDir, "settings.gradle.kts").writeText(
            "rootProject.name = \"test-root\"\ninclude(\":shared\")"
        )
        File(projectDir, "build.gradle.kts").writeText("")
        File(sharedDir, "build.gradle.kts").writeText("""
            plugins { id("io.github.adriandleon.kenvy") }
            group = "com.example.shared"
            kenvy {
                configFile.set(layout.projectDirectory.file("custom/kenvy.toml"))
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(":shared:generateKenvyExample")
            .buildAndFail()

        assertAll(
            { assertTrue(result.output.contains("Kenvy: kenvy.toml not found"), "Error must come from Kenvy") },
            { assertTrue(result.output.contains("custom/kenvy.toml") || result.output.contains("custom${File.separator}kenvy.toml"), "Error must name the configured path") },
            { assertFalse(result.output.contains("Create a kenvy.toml file in your project root"), "Custom configFile diagnostics must not use old root-project wording") }
        )
    }
}
