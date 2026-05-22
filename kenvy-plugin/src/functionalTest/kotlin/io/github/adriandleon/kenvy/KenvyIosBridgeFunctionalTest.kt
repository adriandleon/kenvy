package io.github.adriandleon.kenvy

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KenvyIosBridgeFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun setupIosProject(
        tomlContent: String,
        iosSourceContent: String,
        extraKenvyConfig: String = "",
        extraKotlinTargets: String = "",
        environment: Map<String, String>? = null,
        arguments: List<String>
    ): GradleRunner {
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
            rootProject.name = "ios-bridge-test"
            """.trimIndent()
        )
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "2.1.20"
                id("io.github.adriandleon.kenvy")
            }

            group = "com.example.test"

            kotlin {
                iosArm64()
                iosSimulatorArm64()
                $extraKotlinTargets
            }

            $extraKenvyConfig
            """.trimIndent()
        )
        File(projectDir, "kenvy.toml").writeText(tomlContent)

        listOf("iosArm64Main", "iosSimulatorArm64Main").forEach { sourceSetName ->
            val iosSourceDir = File(projectDir, "src/$sourceSetName/kotlin/com/example/test")
            iosSourceDir.mkdirs()
            File(iosSourceDir, "UseIosKenvy.kt").writeText(iosSourceContent)
        }

        val runner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(arguments)
        return if (environment != null) runner.withEnvironment(kenvyScopedTestEnvironment(environment)) else runner
    }

    @Test fun `generateKenvyIos writes shared iosMain source when iosMain source set exists`() {
        val result = setupIosProject(
            tomlContent = """
                [properties.api-key]
                type = "String"
                default = "common-default"

                [properties.retry_count]
                type = "Int"
                default = "7"

                [overrides.common]
                api-key = "common-override"

                [overrides.ios]
                api-key = "ios-override"
            """.trimIndent(),
            iosSourceContent = """
                package com.example.test

                fun readIosKenvy(): String = Kenvy.apiKey
            """.trimIndent(),
            extraKotlinTargets = """
                sourceSets {
                    val iosMain by creating {
                        dependsOn(commonMain.get())
                    }
                    val iosArm64Main by getting {
                        dependsOn(iosMain)
                    }
                    val iosSimulatorArm64Main by getting {
                        dependsOn(iosMain)
                    }
                }
            """.trimIndent(),
            arguments = listOf("generateKenvyIos")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyIos")?.outcome)

        val generated = File(projectDir, "build/generated/kenvy/iosMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(generated.exists())
        val content = generated.readText()
        assertTrue(content.contains("actual object Kenvy"))
        assertTrue(content.contains("val apiKey: String = \"ios-override\""))
        assertTrue(content.contains("val retryCount: Int = 7"))
    }

    @Test fun `iosMain compilation consumes generated bridge without manual source set wiring`() {
        val result = setupIosProject(
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
            iosSourceContent = """
                package com.example.test

                fun iosKenvySummary(): String = "${'$'}{AppConfig.apiKey}:${'$'}{AppConfig.timeout}"
            """.trimIndent(),
            extraKenvyConfig = """
                kenvy {
                    interfaceName.set("AppConfig")
                }
            """.trimIndent(),
            arguments = listOf("compileKotlinIosArm64", "compileKotlinIosSimulatorArm64")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyIosArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyIosSimulatorArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinIosArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinIosSimulatorArm64")?.outcome)

        val commonGenerated = File(projectDir, "build/generated/kenvy/commonMain/kotlin/com/example/test/AppConfig.kt")
        assertTrue(commonGenerated.exists())
        assertTrue(commonGenerated.readText().contains("expect object AppConfig"))

        listOf("iosArm64Main", "iosSimulatorArm64Main").forEach { sourceSetName ->
            val generated = File(projectDir, "build/generated/kenvy/$sourceSetName/kotlin/com/example/test/AppConfig.kt")
            assertTrue(generated.exists())
            val content = generated.readText()
            assertTrue(content.contains("actual object AppConfig"))
            assertTrue(content.contains("val apiKey: String = \"common-fallback\""))
            assertTrue(content.contains("val timeout: Long = 5000L"))
        }

        val buildScript = File(projectDir, "build.gradle.kts").readText()
        assertFalse(buildScript.contains("sourceSets"))
    }

    @Test fun `overrides dot ios is used for every declared iOS architecture target`() {
        val result = setupIosProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "common-default"

                [overrides.ios]
                api_key = "ios-value"
            """.trimIndent(),
            iosSourceContent = """
                package com.example.test

                fun iosValue(): String = Kenvy.apiKey
            """.trimIndent(),
            arguments = listOf("compileKotlinIosArm64", "compileKotlinIosSimulatorArm64")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyIosArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyIosSimulatorArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinIosArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinIosSimulatorArm64")?.outcome)

        listOf("iosArm64Main", "iosSimulatorArm64Main").forEach { sourceSetName ->
            val generated = File(projectDir, "build/generated/kenvy/$sourceSetName/kotlin/com/example/test/Kenvy.kt")
            assertTrue(generated.exists())
            assertTrue(generated.readText().contains("actual val apiKey: String = \"ios-value\""))
        }
    }

    @Test fun `mixed Android iOS and JVM project generates actuals for every target`() {
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
            rootProject.name = "mixed-bridge-test"
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
                iosArm64()
                iosSimulatorArm64()
                jvm()
            }

            android {
                namespace = "com.example.test"
                compileSdk = 34
                defaultConfig {
                    minSdk = 24
                }
            }
            """.trimIndent()
        )
        File(projectDir, "kenvy.toml").writeText(
            """
            [properties.api_key]
            type = "String"
            default = "common-default"

            [overrides.android]
            api_key = "android-value"

            [overrides.ios]
            api_key = "ios-value"

            [overrides.jvm]
            api_key = "jvm-value"
            """.trimIndent()
        )

        val androidSourceDir = File(projectDir, "src/androidMain/kotlin/com/example/test")
        androidSourceDir.mkdirs()
        File(androidSourceDir, "UseAndroidKenvy.kt").writeText(
            """
            package com.example.test
            fun androidValue(): String = Kenvy.apiKey
            """.trimIndent()
        )

        val iosSourceDir = File(projectDir, "src/iosMain/kotlin/com/example/test")
        iosSourceDir.mkdirs()
        File(iosSourceDir, "UseIosKenvy.kt").writeText(
            """
            package com.example.test
            fun iosValue(): String = Kenvy.apiKey
            """.trimIndent()
        )

        val jvmSourceDir = File(projectDir, "src/jvmMain/kotlin/com/example/test")
        jvmSourceDir.mkdirs()
        File(jvmSourceDir, "UseJvmKenvy.kt").writeText(
            """
            package com.example.test
            fun jvmValue(): String = Kenvy.apiKey
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("compileKotlinJvm", "compileKotlinIosArm64", "compileDebugKotlinAndroid")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyIosArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyAndroid")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyJvm")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinJvm")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinIosArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileDebugKotlinAndroid")?.outcome)

        val commonGenerated = File(projectDir, "build/generated/kenvy/commonMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(commonGenerated.readText().contains("expect object Kenvy"))

        val iosGenerated = File(projectDir, "build/generated/kenvy/iosArm64Main/kotlin/com/example/test/Kenvy.kt")
        assertTrue(iosGenerated.exists())
        assertTrue(iosGenerated.readText().contains("actual object Kenvy"))
        assertTrue(iosGenerated.readText().contains("actual val apiKey: String = \"ios-value\""))

        val androidGenerated = File(projectDir, "build/generated/kenvy/androidMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(androidGenerated.readText().contains("actual val apiKey: String = \"android-value\""))

        val jvmGenerated = File(projectDir, "build/generated/kenvy/jvmMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(jvmGenerated.readText().contains("actual val apiKey: String = \"jvm-value\""))
    }

    @Test fun `custom named iOS target still uses canonical ios bridge`() {
        val result = setupIosProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "common-default"

                [overrides.ios]
                api_key = "ios-value"
            """.trimIndent(),
            iosSourceContent = """
                package com.example.test

                fun iosValue(): String = Kenvy.apiKey
            """.trimIndent(),
            extraKotlinTargets = "",
            arguments = listOf("compileKotlinDevice")
        ).also {
            File(projectDir, "build.gradle.kts").writeText(
                File(projectDir, "build.gradle.kts").readText()
                    .replace("iosArm64()", "iosArm64(\"device\")")
                    .replace("iosSimulatorArm64()", "")
            )
            val deviceSourceDir = File(projectDir, "src/deviceMain/kotlin/com/example/test")
            deviceSourceDir.mkdirs()
            File(deviceSourceDir, "UseDeviceKenvy.kt").writeText(
                """
                package com.example.test

                fun deviceValue(): String = Kenvy.apiKey
                """.trimIndent()
            )
        }.build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyDevice")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinDevice")?.outcome)

        val generated = File(projectDir, "build/generated/kenvy/deviceMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(generated.exists())
        assertTrue(generated.readText().contains("actual val apiKey: String = \"ios-value\""))
    }

    @Test fun `iOS bridge uses explicit debug and release variants for scoped local values`() {
        File(projectDir, "local.properties").writeText(
            """
            api_key=generic-local
            api_key.ios=ios-local
            api_key.ios.debug=ios-debug-local
            api_key.ios.release=ios-release-local
            """.trimIndent() + "\n"
        )

        val debugResult = setupIosProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"

                [overrides.ios]
                api_key = "ios-default"

                [overrides.ios.debug]
                api_key = "ios-debug"

                [overrides.ios.release]
                api_key = "ios-release"
            """.trimIndent(),
            iosSourceContent = """
                package com.example.test

                fun iosValue(): String = Kenvy.apiKey
            """.trimIndent(),
            extraKenvyConfig = """
                kenvy {
                    variant.set("debug")
                }
            """.trimIndent(),
            arguments = listOf("compileKotlinIosArm64", "compileKotlinIosSimulatorArm64")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, debugResult.task(":generateKenvyIosArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, debugResult.task(":generateKenvyIosSimulatorArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, debugResult.task(":compileKotlinIosArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, debugResult.task(":compileKotlinIosSimulatorArm64")?.outcome)

        listOf("iosArm64Main", "iosSimulatorArm64Main").forEach { sourceSetName ->
            val generated = File(projectDir, "build/generated/kenvy/$sourceSetName/kotlin/com/example/test/Kenvy.kt")
            assertTrue(generated.exists())
            assertTrue(generated.readText().contains("actual val apiKey: String = \"ios-debug-local\""))
        }

        val releaseResult = setupIosProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"

                [overrides.ios]
                api_key = "ios-default"

                [overrides.ios.debug]
                api_key = "ios-debug"

                [overrides.ios.release]
                api_key = "ios-release"
            """.trimIndent(),
            iosSourceContent = """
                package com.example.test

                fun iosValue(): String = Kenvy.apiKey
            """.trimIndent(),
            extraKenvyConfig = """
                kenvy {
                    variant.set("release")
                }
            """.trimIndent(),
            arguments = listOf("compileKotlinIosArm64", "compileKotlinIosSimulatorArm64")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, releaseResult.task(":generateKenvyIosArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, releaseResult.task(":generateKenvyIosSimulatorArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, releaseResult.task(":compileKotlinIosArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, releaseResult.task(":compileKotlinIosSimulatorArm64")?.outcome)

        listOf("iosArm64Main", "iosSimulatorArm64Main").forEach { sourceSetName ->
            val generated = File(projectDir, "build/generated/kenvy/$sourceSetName/kotlin/com/example/test/Kenvy.kt")
            assertTrue(generated.exists())
            assertTrue(generated.readText().contains("actual val apiKey: String = \"ios-release-local\""))
        }
    }

    @Test fun `iOS debug generation suggests scoped local key when scoped value is missing`() {
        File(projectDir, "local.properties").writeText(
            """
            api_key.android.debug=android-debug-local
            api_key.android.release=android-release-local
            api_key.ios.release=ios-release-local
            """.trimIndent() + "\n"
        )

        val result = setupIosProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                sensitive = true
                description = "Platform API key"
            """.trimIndent(),
            iosSourceContent = """
                package com.example.test

                fun iosValue(): String = Kenvy.apiKey
            """.trimIndent(),
            extraKenvyConfig = """
                kenvy {
                    variant.set("debug")
                }
            """.trimIndent(),
            arguments = listOf("compileKotlinIosArm64")
        ).buildAndFail()

        assertTrue(result.output.contains("Missing values:"))
        assertTrue(result.output.contains("+ api_key.ios.debug=<YOUR_VALUE>"))
        assertFalse(result.output.contains("+ api_key=<YOUR_VALUE>"))
    }

    @Test fun `non iOS target with ios prefix keeps its own platform bridge`() {
        val result = setupIosProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "common-default"

                [overrides.ios]
                api_key = "ios-value"

                [overrides.iosServer]
                api_key = "server-value"
            """.trimIndent(),
            iosSourceContent = """
                package com.example.test

                fun iosValue(): String = Kenvy.apiKey
            """.trimIndent(),
            extraKotlinTargets = """jvm("iosServer")""",
            arguments = listOf("compileKotlinIosServer")
        ).also {
            val jvmSourceDir = File(projectDir, "src/iosServerMain/kotlin/com/example/test")
            jvmSourceDir.mkdirs()
            File(jvmSourceDir, "UseServerKenvy.kt").writeText(
                """
                package com.example.test

                fun serverValue(): String = Kenvy.apiKey
                """.trimIndent()
            )
        }.build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyIosServer")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinIosServer")?.outcome)

        val generated = File(projectDir, "build/generated/kenvy/iosServerMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(generated.exists())
        assertTrue(generated.readText().contains("actual val apiKey: String = \"server-value\""))
    }

    @Test fun `mixed Android iOS and JVM project keeps shared API names with scoped local secrets`() {
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
            rootProject.name = "mixed-local-bridge-test"
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
                iosArm64()
                iosSimulatorArm64()
                jvm()
            }

            android {
                namespace = "com.example.test"
                compileSdk = 34
                defaultConfig {
                    minSdk = 24
                }
            }
            """.trimIndent()
        )
        File(projectDir, "kenvy.toml").writeText(
            """
            [properties.api_key]
            type = "String"
            default = "common-default"
            """.trimIndent()
        )
        File(projectDir, "local.properties").writeText(
            """
            api_key.android.debug=android-debug-local
            api_key.ios=ios-local
            api_key.jvm=jvm-local
            """.trimIndent() + "\n"
        )

        val androidSourceDir = File(projectDir, "src/androidMain/kotlin/com/example/test")
        androidSourceDir.mkdirs()
        File(androidSourceDir, "UseAndroidKenvy.kt").writeText(
            """
            package com.example.test
            fun androidValue(): String = Kenvy.apiKey
            """.trimIndent()
        )

        val iosSourceDir = File(projectDir, "src/iosMain/kotlin/com/example/test")
        iosSourceDir.mkdirs()
        File(iosSourceDir, "UseIosKenvy.kt").writeText(
            """
            package com.example.test
            fun iosValue(): String = Kenvy.apiKey
            """.trimIndent()
        )

        val jvmSourceDir = File(projectDir, "src/jvmMain/kotlin/com/example/test")
        jvmSourceDir.mkdirs()
        File(jvmSourceDir, "UseJvmKenvy.kt").writeText(
            """
            package com.example.test
            fun jvmValue(): String = Kenvy.apiKey
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("compileKotlinJvm", "compileKotlinIosArm64", "compileDebugKotlinAndroid")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyIosArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyAndroid")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyJvm")?.outcome)

        val commonGenerated = File(projectDir, "build/generated/kenvy/commonMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(commonGenerated.readText().contains("expect object Kenvy"))
        assertTrue(commonGenerated.readText().contains("val apiKey: String"))

        val iosGenerated = File(projectDir, "build/generated/kenvy/iosArm64Main/kotlin/com/example/test/Kenvy.kt")
        assertTrue(iosGenerated.readText().contains("actual val apiKey: String = \"ios-local\""))

        val androidGenerated = File(projectDir, "build/generated/kenvy/androidMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(androidGenerated.readText().contains("actual val apiKey: String = \"android-debug-local\""))

        val jvmGenerated = File(projectDir, "build/generated/kenvy/jvmMain/kotlin/com/example/test/Kenvy.kt")
        assertTrue(jvmGenerated.readText().contains("actual val apiKey: String = \"jvm-local\""))
    }

    @Test fun `generated iOS bridge is included in Kotlin Native framework`() {
        val result = setupIosProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "common-default"

                [overrides.ios]
                api_key = "ios-value"
            """.trimIndent(),
            iosSourceContent = """
                package com.example.test

                fun iosValue(): String = Kenvy.apiKey
            """.trimIndent(),
            extraKotlinTargets = """
                targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
                    binaries.framework {
                        baseName = "Shared"
                    }
                }
            """.trimIndent(),
            arguments = listOf("linkDebugFrameworkIosArm64")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyIosArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":linkDebugFrameworkIosArm64")?.outcome)

        val headers = File(projectDir, "build/bin/iosArm64/debugFramework/Shared.framework/Headers/Shared.h")
        assertTrue(headers.exists())
        val content = headers.readText()
        assertTrue(content.contains("Kenvy"))
        assertTrue(content.contains("apiKey"))
    }

    @Test fun `iOS debug resolves from KENVY_API_KEY_IOS_DEBUG env`() {
        val result = setupIosProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"
            """.trimIndent(),
            iosSourceContent = """
                package com.example.test
                fun iosValue(): String = Kenvy.apiKey
            """.trimIndent(),
            extraKenvyConfig = """
                kenvy {
                    variant.set("debug")
                }
            """.trimIndent(),
            environment = mapOf(
                "KENVY_API_KEY" to "generic-value",
                "KENVY_API_KEY_IOS" to "ios-value",
                "KENVY_API_KEY_IOS_DEBUG" to "ios-debug-env-value"
            ),
            arguments = listOf("compileKotlinIosArm64", "compileKotlinIosSimulatorArm64")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyIosArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyIosSimulatorArm64")?.outcome)

        listOf("iosArm64Main", "iosSimulatorArm64Main").forEach { sourceSetName ->
            val generated = File(projectDir, "build/generated/kenvy/$sourceSetName/kotlin/com/example/test/Kenvy.kt")
            assertTrue(generated.readText().contains("actual val apiKey: String = \"ios-debug-env-value\""))
        }
    }

    @Test fun `iOS release resolves from KENVY_API_KEY_IOS_RELEASE env`() {
        val result = setupIosProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"
            """.trimIndent(),
            iosSourceContent = """
                package com.example.test
                fun iosValue(): String = Kenvy.apiKey
            """.trimIndent(),
            extraKenvyConfig = """
                kenvy {
                    variant.set("release")
                }
            """.trimIndent(),
            environment = mapOf(
                "KENVY_API_KEY" to "generic-value",
                "KENVY_API_KEY_IOS_RELEASE" to "ios-release-env-value"
            ),
            arguments = listOf("compileKotlinIosArm64", "compileKotlinIosSimulatorArm64")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyIosArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyIosSimulatorArm64")?.outcome)

        listOf("iosArm64Main", "iosSimulatorArm64Main").forEach { sourceSetName ->
            val generated = File(projectDir, "build/generated/kenvy/$sourceSetName/kotlin/com/example/test/Kenvy.kt")
            assertTrue(generated.readText().contains("actual val apiKey: String = \"ios-release-env-value\""))
        }
    }

    @Test fun `generic KENVY_API_KEY fallback when no iOS scoped env matches`() {
        val result = setupIosProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"
            """.trimIndent(),
            iosSourceContent = """
                package com.example.test
                fun iosValue(): String = Kenvy.apiKey
            """.trimIndent(),
            extraKenvyConfig = """
                kenvy {
                    variant.set("debug")
                }
            """.trimIndent(),
            environment = mapOf("KENVY_API_KEY" to "generic-env-fallback"),
            arguments = listOf("compileKotlinIosArm64")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyIosArm64")?.outcome)

        val generated = File(projectDir, "build/generated/kenvy/iosArm64Main/kotlin/com/example/test/Kenvy.kt")
        assertTrue(generated.readText().contains("actual val apiKey: String = \"generic-env-fallback\""))
    }

    @Test fun `iOS scoped env beats scoped local properties`() {
        File(projectDir, "local.properties").writeText(
            """
            api_key=local-generic
            api_key.ios=local-ios
            api_key.ios.debug=local-ios-debug
            """.trimIndent() + "\n"
        )

        val result = setupIosProject(
            tomlContent = """
                [properties.api_key]
                type = "String"
                default = "placeholder"
            """.trimIndent(),
            iosSourceContent = """
                package com.example.test
                fun iosValue(): String = Kenvy.apiKey
            """.trimIndent(),
            extraKenvyConfig = """
                kenvy {
                    variant.set("debug")
                }
            """.trimIndent(),
            environment = mapOf("KENVY_API_KEY_IOS_DEBUG" to "ios-debug-env-wins"),
            arguments = listOf("compileKotlinIosArm64")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyIosArm64")?.outcome)

        val generated = File(projectDir, "build/generated/kenvy/iosArm64Main/kotlin/com/example/test/Kenvy.kt")
        assertTrue(generated.readText().contains("actual val apiKey: String = \"ios-debug-env-wins\""))
    }
}
