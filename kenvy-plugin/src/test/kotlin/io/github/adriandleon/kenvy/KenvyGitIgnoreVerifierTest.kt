package io.github.adriandleon.kenvy

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.gradle.api.GradleException

class KenvyGitIgnoreVerifierTest {

    @Test fun `parsed contract defaults security settings to local properties`() {
        val contract = KenvyParser.parseContract(tempToml("""
            [properties.api_key]
            type = "String"
            default = "placeholder"
        """.trimIndent()))

        assertEquals(listOf("local.properties"), contract.security.secretFiles)
    }

    @Test fun `parsed contract includes configured secret files and keeps local properties`() {
        val contract = KenvyParser.parseContract(tempToml("""
            [security]
            secret_files = ["secrets/dev.properties", "local.properties"]

            [properties.api_key]
            type = "String"
            default = "placeholder"
        """.trimIndent()))

        assertEquals(listOf("local.properties", "secrets/dev.properties"), contract.security.secretFiles)
    }

    @Test fun `parser rejects blank configured secret file path`() {
        val ex = assertFailsWith<GradleException> {
            KenvyParser.parseContract(tempToml("""
                [security]
                secret_files = [""]

                [properties.api_key]
                type = "String"
                default = "placeholder"
            """.trimIndent()))
        }

        assertTrue(ex.message?.contains("secret_files") == true)
        assertTrue(ex.message?.contains("blank") == true)
    }

    @Test fun `parser rejects absolute configured secret file path`() {
        val ex = assertFailsWith<GradleException> {
            KenvyParser.parseContract(tempToml("""
                [security]
                secret_files = ["/tmp/secret.properties"]

                [properties.api_key]
                type = "String"
                default = "placeholder"
            """.trimIndent()))
        }

        assertTrue(ex.message?.contains("project-relative") == true)
    }

    @Test fun `parser rejects parent traversal configured secret file path`() {
        val ex = assertFailsWith<GradleException> {
            KenvyParser.parseContract(tempToml("""
                [security]
                secret_files = ["../secret.properties"]

                [properties.api_key]
                type = "String"
                default = "placeholder"
            """.trimIndent()))
        }

        assertTrue(ex.message?.contains("..") == true)
    }

    @Test fun `parser rejects non-string configured secret file path`() {
        val ex = assertFailsWith<GradleException> {
            KenvyParser.parseContract(tempToml("""
                [security]
                secret_files = [42]

                [properties.api_key]
                type = "String"
                default = "placeholder"
            """.trimIndent()))
        }

        assertTrue(ex.message?.contains("non-string") == true)
    }

    @Test fun `matching gitignore entries suppress missing results`() {
        val projectDir = tempProject(
            secretFiles = listOf("local.properties", "secrets/dev.properties"),
            gitignore = """
                /local.properties
                secrets/
            """.trimIndent()
        )

        val result = KenvyGitIgnoreVerifier.verify(
            projectDir = projectDir,
            secretFilePaths = listOf("local.properties", "secrets/dev.properties"),
            gitignoreFile = File(projectDir, ".gitignore")
        )

        assertEquals(emptyList(), result.missingExclusions)
    }

    @Test fun `missing gitignore entries produce suggested entries`() {
        val projectDir = tempProject(
            secretFiles = listOf("local.properties", "secrets/dev.properties"),
            gitignore = "build/"
        )

        val result = KenvyGitIgnoreVerifier.verify(
            projectDir = projectDir,
            secretFilePaths = listOf("local.properties", "secrets/dev.properties"),
            gitignoreFile = File(projectDir, ".gitignore")
        )

        assertEquals(
            listOf(
                KenvyMissingGitIgnoreExclusion("local.properties", "local.properties"),
                KenvyMissingGitIgnoreExclusion("secrets/dev.properties", "secrets/dev.properties")
            ),
            result.missingExclusions
        )
    }

    @Test fun `missing configured secret file does not produce warning noise`() {
        val projectDir = tempProject(secretFiles = listOf("local.properties"), gitignore = "")

        val result = KenvyGitIgnoreVerifier.verify(
            projectDir = projectDir,
            secretFilePaths = listOf("local.properties", "secrets/dev.properties"),
            gitignoreFile = File(projectDir, ".gitignore")
        )

        assertEquals(listOf(KenvyMissingGitIgnoreExclusion("local.properties", "local.properties")), result.missingExclusions)
    }

    @Test fun `later negated gitignore entry makes path not ignored`() {
        val projectDir = tempProject(secretFiles = listOf("local.properties"), gitignore = """
            local.properties
            !local.properties
        """.trimIndent())

        val result = KenvyGitIgnoreVerifier.verify(
            projectDir = projectDir,
            secretFilePaths = listOf("local.properties"),
            gitignoreFile = File(projectDir, ".gitignore")
        )

        assertEquals(listOf(KenvyMissingGitIgnoreExclusion("local.properties", "local.properties")), result.missingExclusions)
    }

    private fun tempToml(content: String): File = File.createTempFile("kenvy-test", ".toml").apply {
        writeText(content)
        deleteOnExit()
    }

    private fun tempProject(secretFiles: List<String>, gitignore: String): File {
        val projectDir = createTempDirectory(prefix = "kenvy-gitignore-test").toFile()
        File(projectDir, ".gitignore").writeText(gitignore)
        secretFiles.forEach { path ->
            File(projectDir, path).apply {
                parentFile?.mkdirs()
                writeText("SUPER_SECRET_12345_DO_NOT_LOG")
            }
        }
        projectDir.deleteOnExit()
        return projectDir
    }
}
