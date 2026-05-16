package io.github.adriandleon.kenvy

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KenvyGradleIsolationFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun setupSingleProject(arguments: List<String> = kenvyIsolationArgs) =
        newGradleRunner(projectDir, arguments).also {
            File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"kenvy-isolation-test\"")
            File(projectDir, "build.gradle.kts").writeText(
                """
                plugins { id("io.github.adriandleon.kenvy") }

                group = "com.example.isolation"
                """.trimIndent()
            )
            File(projectDir, "kenvy.toml").writeText(
                """
                [properties.api_url]
                type = "String"
                default = "https://example.com"

                [properties.retry_count]
                type = "Int"
                default = "3"
                """.trimIndent()
            )
        }

    private fun setupIosProject(arguments: List<String>) =
        newGradleRunner(projectDir, arguments).also {
            File(projectDir, "settings.gradle.kts").writeText(
                """
                pluginManagement {
                    repositories {
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }
                dependencyResolutionManagement {
                    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                    repositories {
                        mavenCentral()
                    }
                }
                rootProject.name = "kenvy-ios-isolation-test"
                """.trimIndent()
            )
            File(projectDir, "gradle.properties").writeText(
                """
                org.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m
                """.trimIndent()
            )
            File(projectDir, "build.gradle.kts").writeText(
                """
                plugins {
                    id("org.jetbrains.kotlin.multiplatform") version "2.1.20"
                    id("io.github.adriandleon.kenvy")
                }

                group = "com.example.isolation"

                kotlin {
                    iosArm64()
                    iosSimulatorArm64()
                }
                """.trimIndent()
            )
            File(projectDir, "kenvy.toml").writeText(
                """
                [properties.api_key]
                type = "String"
                default = "ios-value"
                """.trimIndent()
            )

            listOf("iosArm64Main", "iosSimulatorArm64Main").forEach { sourceSetName ->
                val sourceDir = File(projectDir, "src/$sourceSetName/kotlin/com/example/isolation")
                sourceDir.mkdirs()
                File(sourceDir, "UseKenvy.kt").writeText(
                    """
                    package com.example.isolation

                    fun ${sourceSetName.replaceFirstChar { char -> char.lowercaseChar() }}Value(): String = Kenvy.apiKey
                    """.trimIndent()
                )
            }
        }

    @Test
    fun `single project generateKenvy reuses configuration cache under isolated projects`() {
        val runner = setupSingleProject()

        val firstRun = runner.build()
        val secondRun = runner.build()

        assertEquals(TaskOutcome.SUCCESS, firstRun.task(":generateKenvy")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, secondRun.task(":generateKenvy")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, secondRun.task(":generateKenvyExample")?.outcome)
        firstRun.assertConfigurationCacheStored()
        secondRun.assertConfigurationCacheReused()
        firstRun.assertNoIsolationOrConfigurationCacheProblems()
        secondRun.assertNoIsolationOrConfigurationCacheProblems()
    }

    @Test
    fun `20-module verifyKenvy reuses configuration cache and keeps generation tasks up-to-date`() {
        val fixture = KenvyMultiModuleFixture(projectDir)
        fixture.writeProject()
        val runner = fixture.runner(
            listOf(
                "verifyKenvy",
                "--configuration-cache",
                "-Dorg.gradle.unsafe.isolated-projects=true"
            )
        )

        val firstRun = runner.build()
        val secondRun = runner.build()

        firstRun.assertConfigurationCacheStored()
        secondRun.assertConfigurationCacheReused()
        firstRun.assertNoIsolationOrConfigurationCacheProblems()
        secondRun.assertNoIsolationOrConfigurationCacheProblems()
        secondRun.assertAllModuleTasksHaveOutcome(fixture.moduleNames, "generateKenvy", TaskOutcome.UP_TO_DATE)
        secondRun.assertAllModuleTasksHaveOutcome(fixture.moduleNames, "generateKenvyExample", TaskOutcome.UP_TO_DATE)
    }

    @Test
    fun `ios target generation remains configuration-cache safe under isolated projects`() {
        val runner = setupIosProject(
            listOf(
                "generateKenvyIosArm64",
                "generateKenvyIosSimulatorArm64",
                "--configuration-cache",
                "-Dorg.gradle.unsafe.isolated-projects=true"
            )
        )

        val firstRun = runner.build()
        val secondRun = runner.build()

        assertEquals(TaskOutcome.SUCCESS, firstRun.task(":generateKenvyIosArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, firstRun.task(":generateKenvyIosSimulatorArm64")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, secondRun.task(":generateKenvyIosArm64")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, secondRun.task(":generateKenvyIosSimulatorArm64")?.outcome)
        firstRun.assertConfigurationCacheStored()
        secondRun.assertConfigurationCacheReused()
        firstRun.assertNoIsolationOrConfigurationCacheProblems()
        secondRun.assertNoIsolationOrConfigurationCacheProblems()
        listOf("iosArm64Main", "iosSimulatorArm64Main").forEach { sourceSetName ->
            val generated = File(projectDir, "build/generated/kenvy/$sourceSetName/kotlin/com/example/isolation/Kenvy.kt")
            assertTrue(generated.exists(), "Expected generated file for $sourceSetName")
        }
    }
}
