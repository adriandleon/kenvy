package io.github.adriandleon.kenvy

import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class KenvyGitIgnoreFunctionalTest {

    @TempDir lateinit var projectDir: File

    private val sentinel = "SUPER_SECRET_12345_DO_NOT_LOG"

    @Test fun `generateKenvy warns when local properties exists and gitignore lacks local properties`() {
        val result = setup(
            localProperties = "api_key=$sentinel",
            gitignore = "build/"
        ).build()

        assertTrue(
            result.output.contains("local.properties is not in .gitignore - secrets may be committed to version control")
        )
        assertTrue(result.output.contains("suggested entry: local.properties"))
        assertFalse(result.output.contains(sentinel), "Secret contents must not appear in warning output")
    }

    @Test fun `generateKenvy does not warn when local properties is gitignored`() {
        val result = setup(
            localProperties = "api_key=$sentinel",
            gitignore = "local.properties"
        ).build()

        assertFalse(result.output.contains("secrets may be committed to version control"))
        assertFalse(result.output.contains(sentinel), "Secret contents must not appear in warning output")
    }

    @Test fun `generateKenvyExample warns when local properties exists and gitignore lacks local properties`() {
        val result = setup(
            localProperties = "api_key=$sentinel",
            gitignore = "build/",
            arguments = listOf("generateKenvyExample", "--warn")
        ).build()

        assertTrue(
            result.output.contains("local.properties is not in .gitignore - secrets may be committed to version control")
        )
        assertFalse(result.output.contains(sentinel), "Secret contents must not appear in warning output")
    }

    @Test fun `custom config file still verifies project root secret files`() {
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"test\"")
        File(projectDir, "build.gradle.kts").writeText("""
            plugins { id("io.github.adriandleon.kenvy") }
            group = "com.example.test"

            kenvy {
                configFile.set(layout.projectDirectory.file("config/kenvy.toml"))
            }
        """.trimIndent())
        File(projectDir, "config").mkdirs()
        File(projectDir, "config/kenvy.toml").writeText("""
            [properties.api_key]
            type = "String"
            default = "placeholder"
            sensitive = true
        """.trimIndent())
        File(projectDir, ".gitignore").writeText("build/")
        File(projectDir, "local.properties").writeText("api_key=$sentinel")

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generateKenvy", "--warn")
            .build()

        assertTrue(
            result.output.contains("local.properties is not in .gitignore - secrets may be committed to version control")
        )
        assertFalse(result.output.contains(sentinel), "Secret contents must not appear in warning output")
    }

    @Test fun `gitignore changes refresh task-time diagnostics`() {
        val runner = setup(
            localProperties = "api_key=$sentinel",
            gitignore = "build/"
        )

        val firstRun = runner.build()
        File(projectDir, ".gitignore").writeText("local.properties")
        val secondRun = runner.build()

        assertTrue(firstRun.output.contains("local.properties is not in .gitignore"))
        assertFalse(secondRun.output.contains("secrets may be committed to version control"))
    }

    @Test fun `creating configured secret file refreshes task-time diagnostics`() {
        val runner = setup(
            tomlHeader = """
                [security]
                secret_files = ["local.properties", "secrets/dev.properties"]
            """.trimIndent(),
            localProperties = "api_key=$sentinel",
            gitignore = "local.properties"
        )

        val firstRun = runner.build()
        File(projectDir, "secrets").mkdirs()
        File(projectDir, "secrets/dev.properties").writeText("token=$sentinel")
        val secondRun = runner.build()

        assertFalse(firstRun.output.contains("secrets/dev.properties"))
        assertTrue(secondRun.output.contains("secrets/dev.properties"))
        assertFalse(secondRun.output.contains(sentinel), "Secret contents must not appear in warning output")
    }

    @Test fun `configured existing secret files warn when not gitignored`() {
        val result = setup(
            tomlHeader = """
                [security]
                secret_files = ["local.properties", "secrets/dev.properties"]
            """.trimIndent(),
            localProperties = "api_key=$sentinel",
            extraFiles = mapOf("secrets/dev.properties" to "token=$sentinel"),
            gitignore = "local.properties"
        ).build()

        assertTrue(result.output.contains("secrets/dev.properties"))
        assertTrue(result.output.contains("suggested entry: secrets/dev.properties"))
        assertFalse(result.output.contains(sentinel), "Secret contents must not appear in warning output")
    }

    @Test fun `missing configured secret files do not warn`() {
        val result = setup(
            tomlHeader = """
                [security]
                secret_files = ["local.properties", "secrets/dev.properties"]
            """.trimIndent(),
            localProperties = "api_key=$sentinel",
            gitignore = "local.properties"
        ).build()

        assertFalse(result.output.contains("secrets/dev.properties"))
        assertFalse(result.output.contains("secrets may be committed to version control"))
        assertFalse(result.output.contains(sentinel), "Secret contents must not appear in warning output")
    }

    @Test fun `gitignore and secret files are not mutated by verification`() {
        val localProperties = "api_key=$sentinel"
        val gitignore = "build/"
        val result = setup(
            localProperties = localProperties,
            gitignore = gitignore
        ).build()

        assertTrue(result.output.contains("local.properties is not in .gitignore"))
        assertEqualsFile("local.properties", localProperties)
        assertEqualsFile(".gitignore", gitignore)
    }

    private fun setup(
        tomlHeader: String = "",
        localProperties: String? = null,
        extraFiles: Map<String, String> = emptyMap(),
        gitignore: String,
        arguments: List<String> = listOf("generateKenvy", "--warn")
    ): GradleRunner {
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"test\"")
        File(projectDir, "build.gradle.kts").writeText("""
            plugins { id("io.github.adriandleon.kenvy") }
            group = "com.example.test"
        """.trimIndent())
        File(projectDir, "kenvy.toml").writeText("""
            $tomlHeader

            [properties.api_key]
            type = "String"
            default = "placeholder"
            sensitive = true
        """.trimIndent())
        File(projectDir, ".gitignore").writeText(gitignore)
        if (localProperties != null) {
            File(projectDir, "local.properties").writeText(localProperties)
        }
        extraFiles.forEach { (path, content) ->
            File(projectDir, path).apply {
                parentFile?.mkdirs()
                writeText(content)
            }
        }

        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(arguments)
    }

    private fun assertEqualsFile(path: String, expected: String) {
        kotlin.test.assertEquals(expected, File(projectDir, path).readText())
    }
}
