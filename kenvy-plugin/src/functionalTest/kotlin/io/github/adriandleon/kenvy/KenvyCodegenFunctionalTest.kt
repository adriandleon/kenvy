package io.github.adriandleon.kenvy

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KenvyCodegenFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun setupProject(
        tomlContent: String,
        extraConfig: String = "",
        arguments: List<String> = listOf("generateKenvy")
    ): GradleRunner {
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"test\"")
        File(projectDir, "build.gradle.kts").writeText("""
            plugins { id("io.github.adriandleon.kenvy") }
            group = "com.example.test"
            $extraConfig
        """.trimIndent())
        File(projectDir, "kenvy.toml").writeText(tomlContent)
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(arguments)
    }

    private fun setupKmpProject(): GradleRunner {
        File(projectDir, "settings.gradle.kts").writeText("""
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "test"
        """.trimIndent())
        File(projectDir, "build.gradle.kts").writeText("""
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "2.1.20"
                id("io.github.adriandleon.kenvy")
            }

            group = "com.example.test"

            kotlin {
                jvm()
            }
        """.trimIndent())
        File(projectDir, "kenvy.toml").writeText("""
            [properties.api_key]
            type = "String"
            default = "compiled-value"
        """.trimIndent())

        val sourceDir = File(projectDir, "src/commonMain/kotlin/com/example/test")
        sourceDir.mkdirs()
        File(sourceDir, "UseKenvy.kt").writeText("""
            package com.example.test

            fun useKenvy(): String = Kenvy.apiKey
        """.trimIndent())

        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("compileKotlinJvm")
    }

    @Test fun `generateKenvy task succeeds and produces Kenvy object`() {
        val result = setupProject("""
            [properties.api_key]
            type = "String"
            default = "configured-value"

            [properties.retry_count]
            type = "Int"
            default = "3"
        """.trimIndent()).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        val generated = File(projectDir, "build/generated/kenvy/commonMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(generated.exists())
        val content = generated.readText()
        assertTrue(content.contains("object Kenvy"))
        assertTrue(content.contains("val apiKey: String"))
        assertTrue(content.contains("val retryCount: Int = 3"))
    }

    @Test fun `generateKenvy is UP-TO-DATE on second run without changes`() {
        val runner = setupProject("""
            [properties.base_url]
            type = "String"
            default = "https://api.example.com"
        """.trimIndent())
        runner.build()
        val result = runner.build()
        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":generateKenvy")?.outcome)
    }

    @Test fun `generateKenvy no-provider path remains successful when internal provider test hook is absent`() {
        val runner = setupProject("""
            [properties.api_key]
            type = "String"
            default = "configured-value"
        """.trimIndent())

        val first = runner.build()
        val second = runner.build()

        assertEquals(TaskOutcome.SUCCESS, first.task(":generateKenvy")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateKenvy")?.outcome)
        assertFalse(first.output.contains("External provider"))
        assertFalse(second.output.contains("External provider"))
    }

    @Test fun `generateKenvy fails with clear error when no properties defined`() {
        val result = setupProject("# no properties here").buildAndFail()
        assertTrue(result.output.contains("No properties defined in kenvy.toml"))
    }

    @Test fun `generateKenvy removes stale output before failing on empty TOML`() {
        val runner = setupProject("""
            [properties.api_key]
            type = "String"
            default = "configured-value"
        """.trimIndent())
        runner.build()
        val generated = File(projectDir, "build/generated/kenvy/commonMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(generated.exists())

        File(projectDir, "kenvy.toml").writeText("# no properties here")
        val result = runner.buildAndFail()

        assertTrue(result.output.contains("No properties defined in kenvy.toml"))
        assertTrue(!generated.exists(), "Stale generated file must be removed after failed generation")
    }

    @Test fun `custom interfaceName produces correct object name and filename`() {
        val result = setupProject(
            tomlContent = """
                [properties.x]
                type = "String"
                default = "y"
            """.trimIndent(),
            extraConfig = """
                kenvy { interfaceName.set("AppConfig") }
            """
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        val generated = File(projectDir, "build/generated/kenvy/commonMain/kotlin/com/example/test/AppConfig.kt")
        assertTrue(generated.exists())
        assertTrue(generated.readText().contains("object AppConfig"))
    }

    @Test fun `invalid packageName fails before writing generated source`() {
        val result = setupProject(
            tomlContent = """
                [properties.x]
                type = "String"
                default = "y"
            """.trimIndent(),
            extraConfig = """
                kenvy { packageName.set("1bad.package") }
            """
        ).buildAndFail()

        assertTrue(result.output.contains("packageName"))
        assertTrue(result.output.contains("valid Kotlin package name"))
    }

    @Test fun `invalid interfaceName fails before writing generated source`() {
        val result = setupProject(
            tomlContent = """
                [properties.x]
                type = "String"
                default = "y"
            """.trimIndent(),
            extraConfig = """
                kenvy { interfaceName.set("class") }
            """
        ).buildAndFail()

        assertTrue(result.output.contains("interfaceName"))
        assertTrue(result.output.contains("valid Kotlin object name"))
    }

    @Test fun `all four property types are generated correctly`() {
        val result = setupProject("""
            [properties.name]
            type = "String"
            default = "kenvy"

            [properties.port]
            type = "Int"
            default = "8080"

            [properties.debug]
            type = "Boolean"
            default = "false"

            [properties.timeout]
            type = "Long"
            default = "30000"
        """.trimIndent()).build()

        val content = File(projectDir, "build/generated/kenvy/commonMain/kotlin/com/example/test/Kenvy.kt").readText()
        assertTrue(content.contains("val name: String = \"kenvy\""))
        assertTrue(content.contains("val port: Int = 8080"))
        assertTrue(content.contains("val debug: Boolean = false"))
        assertTrue(content.contains("val timeout: Long = 30000L"))
    }

    @Test fun `KMP compile can access generated Kenvy from commonMain`() {
        val result = setupKmpProject().build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinJvm")?.outcome)
    }

    @Test fun `generateKenvy fails before writing ambiguous lower camel source`() {
        val runner = setupProject(
            """
            [properties.app_platform_name]
            type = "String"
            default = "a"

            [properties.appPlatformName]
            type = "String"
            default = "b"
            """.trimIndent()
        )

        val result = runner.buildAndFail()
        val generated = File(projectDir, "build/generated/kenvy/commonMain/kotlin/com/example/test/Kenvy.kt")

        assertTrue(result.output.contains("generated Kotlin property 'appPlatformName'"))
        assertTrue(result.output.contains("app_platform_name, appPlatformName"))
        assertTrue(result.output.contains("compatibility naming style"))
        assertFalse(generated.exists())
    }

    @Test fun `preserve naming style keeps legacy generated property names`() {
        val result = setupProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "configured-value"
            """.trimIndent(),
            extraConfig = """
                kenvy {
                    generatedPropertyNameStyle.set("preserve")
                }
            """.trimIndent()
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        val generated = File(projectDir, "build/generated/kenvy/commonMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(generated.readText().contains("val api_key: String = \"configured-value\""))
    }

}
