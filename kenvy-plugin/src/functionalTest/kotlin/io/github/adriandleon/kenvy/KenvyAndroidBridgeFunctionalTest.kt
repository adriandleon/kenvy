package io.github.adriandleon.kenvy

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KenvyAndroidBridgeFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun assertNoExpectActualBetaWarning(output: String) {
        assertFalse(output.contains("expect/actual") && output.contains("Beta"),
            "Kenvy generated sources must not emit expect/actual beta warning noise")
        assertFalse(output.contains("expect/actual for classes"),
            "Kenvy generated sources must not emit expect/actual classifier warning (alternate phrasing)")
        assertFalse(output.contains("-Xexpect-actual-classes"), "Kenvy must not require consumer compiler flag suppression")
        assertFalse(output.contains("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"), "Suppression id must not leak into Gradle output")
    }

    private fun setupAndroidProject(
        tomlContent: String,
        androidSourceContent: String,
        extraKenvyConfig: String = "",
        extraKotlinTargets: String = "",
        jvmSourceContent: String? = null,
        environment: Map<String, String>? = null,
        arguments: List<String>
    ): GradleRunner {
        File(projectDir, "settings.gradle.kts").writeText(
            """
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
            rootProject.name = "android-bridge-test"
            """.trimIndent()
        )
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.library") version "8.5.2"
                id("org.jetbrains.kotlin.multiplatform") version "2.1.20"
                id("io.github.adriandleon.kenvy")
            }

            group = "com.example.test"

            kotlin {
                androidTarget()
                $extraKotlinTargets
            }

            android {
                namespace = "com.example.test"
                compileSdk = 34
                defaultConfig {
                    minSdk = 24
                }
            }

            $extraKenvyConfig
            """.trimIndent()
        )
        File(projectDir, "kenvy.toml").writeText(tomlContent)

        val androidSourceDir = File(projectDir, "src/androidMain/kotlin/com/example/test")
        androidSourceDir.mkdirs()
        File(androidSourceDir, "UseAndroidKenvy.kt").writeText(androidSourceContent)
        if (jvmSourceContent != null) {
            val jvmSourceDir = File(projectDir, "src/jvmMain/kotlin/com/example/test")
            jvmSourceDir.mkdirs()
            File(jvmSourceDir, "UseJvmKenvy.kt").writeText(jvmSourceContent)
        }

        val runner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(arguments)
        return if (environment != null) runner.withEnvironment(System.getenv() + environment) else runner
    }

    @Test fun `generateKenvyAndroid writes androidMain source with Android overrides`() {
        val result = setupAndroidProject(
            tomlContent = """
                [properties.api-key]
                type = "String"
                default = "common-default"

                [properties.retry_count]
                type = "Int"
                default = "7"

                [overrides.common]
                api-key = "common-override"

                [overrides.android]
                api-key = "android-override"
            """.trimIndent(),
            androidSourceContent = """
                package com.example.test

                fun readAndroidKenvy(): String = Kenvy.apiKey
            """.trimIndent(),
            arguments = listOf("generateKenvyAndroid")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyAndroid")?.outcome)

        val generated = File(projectDir, "build/generated/kenvy/androidMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(generated.exists())
        val content = generated.readText()
        assertTrue(content.contains("actual object Kenvy"))
        assertTrue(content.contains("val apiKey: String = \"android-override\""))
        assertTrue(content.contains("val retryCount: Int = 7"))
    }

    @Test fun `androidMain compilation consumes generated bridge without manual source set wiring`() {
        val result = setupAndroidProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "common-default"

                [properties.timeout]
                type = "Long"
                default = "5000"

                [overrides.common]
                api_key = "common-fallback"
            """.trimIndent(),
            androidSourceContent = """
                package com.example.test

                fun androidKenvySummary(): String = "${'$'}{AppConfig.apiKey}:${'$'}{AppConfig.timeout}"
            """.trimIndent(),
            extraKenvyConfig = """
                kenvy {
                    interfaceName.set("AppConfig")
                }
            """.trimIndent(),
            arguments = listOf("compileDebugKotlinAndroid")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyAndroid")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileDebugKotlinAndroid")?.outcome)
        assertNoExpectActualBetaWarning(result.output)

        val commonGenerated = File(projectDir, "build/generated/kenvy/commonMain/kotlin/com/example/test/AppConfig.kt")
        assertTrue(commonGenerated.exists())
        assertTrue(commonGenerated.readText().contains("expect object AppConfig"))

        val generated = File(projectDir, "build/generated/kenvy/androidMain/kotlin/com/example/test/AppConfig.kt")
        assertTrue(generated.exists())
        val content = generated.readText()
        assertTrue(content.contains("actual object AppConfig"))
        assertTrue(content.contains("val apiKey: String = \"common-fallback\""))
        assertTrue(content.contains("val timeout: Long = 5000L"))

        val buildScript = File(projectDir, "build.gradle.kts").readText()
        assertFalse(buildScript.contains("sourceSets"))
    }

    @Test fun `mixed Android and JVM project generates actuals for every target`() {
        val result = setupAndroidProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"

                [overrides.android]
                api_key = "android-value"

                [overrides.jvm]
                api_key = "jvm-value"
            """.trimIndent(),
            androidSourceContent = """
                package com.example.test

                fun androidValue(): String = Kenvy.apiKey
            """.trimIndent(),
            extraKotlinTargets = "jvm()",
            jvmSourceContent = """
                package com.example.test

                fun jvmValue(): String = Kenvy.apiKey
            """.trimIndent(),
            arguments = listOf("compileKotlinJvm", "compileDebugKotlinAndroid")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyAndroid")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyJvm")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinJvm")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileDebugKotlinAndroid")?.outcome)
        assertNoExpectActualBetaWarning(result.output)

        val commonGenerated = File(projectDir, "build/generated/kenvy/commonMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(commonGenerated.readText().contains("expect object Kenvy"))

        val androidGenerated = File(projectDir, "build/generated/kenvy/androidMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(androidGenerated.readText().contains("actual val apiKey: String = \"android-value\""))

        val jvmGenerated = File(projectDir, "build/generated/kenvy/jvmMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(jvmGenerated.exists())
        assertTrue(jvmGenerated.readText().contains("actual object Kenvy"))
        assertTrue(jvmGenerated.readText().contains("actual val apiKey: String = \"jvm-value\""))
    }

    @Test fun `android compilation uses requested AGP variant override`() {
        val result = setupAndroidProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "common-default"

                [overrides.android]
                api_key = "android-default"

                [overrides.android.debug]
                api_key = "android-debug"
            """.trimIndent(),
            androidSourceContent = """
                package com.example.test

                fun androidValue(): String = Kenvy.apiKey
            """.trimIndent(),
            arguments = listOf("compileDebugKotlinAndroid")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyAndroid")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileDebugKotlinAndroid")?.outcome)
        assertNoExpectActualBetaWarning(result.output)

        val generated = File(projectDir, "build/generated/kenvy/androidMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(generated.readText().contains("actual val apiKey: String = \"android-debug\""))
    }

    @Test fun `android debug and release tasks keep one property name while resolving scoped local values`() {
        File(projectDir, "local.properties").writeText(
            """
            api_key=generic-local
            api_key.android=android-local
            api_key.android.debug=android-debug-local
            api_key.android.release=android-release-local
            """.trimIndent() + "\n"
        )

        val debugResult = setupAndroidProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"

                [overrides.android]
                api_key = "android-default"

                [overrides.android.debug]
                api_key = "android-debug"

                [overrides.android.release]
                api_key = "android-release"
            """.trimIndent(),
            androidSourceContent = """
                package com.example.test

                fun androidValue(): String = Kenvy.apiKey
            """.trimIndent(),
            arguments = listOf("compileDebugKotlinAndroid")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, debugResult.task(":generateKenvyAndroid")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, debugResult.task(":compileDebugKotlinAndroid")?.outcome)
        assertNoExpectActualBetaWarning(debugResult.output)

        val generated = File(projectDir, "build/generated/kenvy/androidMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(generated.readText().contains("actual val apiKey: String = \"android-debug-local\""))

        val releaseResult = setupAndroidProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"

                [overrides.android]
                api_key = "android-default"

                [overrides.android.debug]
                api_key = "android-debug"

                [overrides.android.release]
                api_key = "android-release"
            """.trimIndent(),
            androidSourceContent = """
                package com.example.test

                fun androidValue(): String = Kenvy.apiKey
            """.trimIndent(),
            arguments = listOf("assembleRelease")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, releaseResult.task(":generateKenvyAndroid")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, releaseResult.task(":assembleRelease")?.outcome)
        assertNoExpectActualBetaWarning(releaseResult.output)
        assertTrue(generated.readText().contains("actual val apiKey: String = \"android-release-local\""))
        assertTrue(generated.readText().contains("actual object Kenvy"))
    }

    @Test fun `android debug resolves from KENVY_API_KEY_ANDROID_DEBUG env`() {
        val result = setupAndroidProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"
            """.trimIndent(),
            androidSourceContent = """
                package com.example.test
                fun androidValue(): String = Kenvy.apiKey
            """.trimIndent(),
            environment = mapOf(
                "KENVY_API_KEY" to "generic-value",
                "KENVY_API_KEY_ANDROID" to "android-value",
                "KENVY_API_KEY_ANDROID_DEBUG" to "android-debug-env-value"
            ),
            arguments = listOf("compileDebugKotlinAndroid")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyAndroid")?.outcome)

        val generated = File(projectDir, "build/generated/kenvy/androidMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(generated.readText().contains("actual val apiKey: String = \"android-debug-env-value\""))
    }

    @Test fun `android release resolves from KENVY_API_KEY_ANDROID_RELEASE env`() {
        val result = setupAndroidProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"
            """.trimIndent(),
            androidSourceContent = """
                package com.example.test
                fun androidValue(): String = Kenvy.apiKey
            """.trimIndent(),
            environment = mapOf(
                "KENVY_API_KEY" to "generic-value",
                "KENVY_API_KEY_ANDROID_RELEASE" to "android-release-env-value"
            ),
            arguments = listOf("assembleRelease")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyAndroid")?.outcome)

        val generated = File(projectDir, "build/generated/kenvy/androidMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(generated.readText().contains("actual val apiKey: String = \"android-release-env-value\""))
    }

    @Test fun `android scoped env beats scoped local properties`() {
        File(projectDir, "local.properties").writeText(
            """
            api_key=local-generic
            api_key.android=local-android
            api_key.android.debug=local-android-debug
            """.trimIndent() + "\n"
        )

        val result = setupAndroidProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"
            """.trimIndent(),
            androidSourceContent = """
                package com.example.test
                fun androidValue(): String = Kenvy.apiKey
            """.trimIndent(),
            environment = mapOf("KENVY_API_KEY_ANDROID_DEBUG" to "android-debug-env-wins"),
            arguments = listOf("compileDebugKotlinAndroid")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyAndroid")?.outcome)

        val generated = File(projectDir, "build/generated/kenvy/androidMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(generated.readText().contains("actual val apiKey: String = \"android-debug-env-wins\""))
    }

    @Test fun `generic KENVY_API_KEY fallback when no android scoped env matches`() {
        val result = setupAndroidProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"
            """.trimIndent(),
            androidSourceContent = """
                package com.example.test
                fun androidValue(): String = Kenvy.apiKey
            """.trimIndent(),
            environment = mapOf("KENVY_API_KEY" to "generic-env-value"),
            arguments = listOf("compileDebugKotlinAndroid")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyAndroid")?.outcome)

        val generated = File(projectDir, "build/generated/kenvy/androidMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(generated.readText().contains("actual val apiKey: String = \"generic-env-value\""))
    }
}
