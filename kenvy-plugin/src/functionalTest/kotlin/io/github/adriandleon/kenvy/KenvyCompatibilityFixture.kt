package io.github.adriandleon.kenvy

import org.gradle.testkit.runner.GradleRunner
import java.io.File

internal enum class AndroidFixtureMode {
    LegacyAndroidLibrary,
    AndroidKmpLibrary
}

internal class KenvyCompatibilityFixture(private val projectDir: File) {
    fun writeAndroidProject(
        kotlinVersion: String,
        agpVersion: String,
        mode: AndroidFixtureMode,
        generatedVisibility: String = "internal",
        javaTarget: String = "17"
    ) {
        writeSettings(includeGoogle = true, rootName = "android-compatibility-test")
        File(projectDir, "build.gradle.kts").writeText(
            when (mode) {
                AndroidFixtureMode.LegacyAndroidLibrary -> legacyAndroidBuildScript(
                    kotlinVersion = kotlinVersion,
                    agpVersion = agpVersion,
                    generatedVisibility = generatedVisibility,
                    javaTarget = javaTarget
                )
                AndroidFixtureMode.AndroidKmpLibrary -> androidKmpBuildScript(
                    kotlinVersion = kotlinVersion,
                    agpVersion = agpVersion,
                    generatedVisibility = generatedVisibility,
                    javaTarget = javaTarget
                )
            }
        )
        writeContract()
        writeConsumerSource("src/androidMain/kotlin/com/example/compat/UseAndroidKenvy.kt")
    }

    fun writeIosProject(kotlinVersion: String, generatedVisibility: String = "internal") {
        writeSettings(includeGoogle = false, rootName = "ios-compatibility-test")
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion"
                id("io.github.adriandleon.kenvy")
            }

            group = "com.example.compat"

            kotlin {
                iosArm64()
                iosSimulatorArm64()
            }

            kenvy {
                interfaceName.set("AppConfig")
                generatedVisibility.set("$generatedVisibility")
            }
            """.trimIndent()
        )
        writeContract()
        listOf("iosArm64Main", "iosSimulatorArm64Main").forEach { sourceSetName ->
            writeConsumerSource("src/$sourceSetName/kotlin/com/example/compat/UseIosKenvy.kt")
        }
    }

    fun writeJvmToolchainProject(kotlinVersion: String, javaToolchain: String) {
        writeSettings(includeGoogle = false, rootName = "jvm-toolchain-compatibility-test")
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion"
                id("io.github.adriandleon.kenvy")
            }

            group = "com.example.compat"

            kotlin {
                jvm()
                jvmToolchain($javaToolchain)
            }

            kenvy {
                interfaceName.set("AppConfig")
                generatedVisibility.set("internal")
            }
            """.trimIndent()
        )
        writeContract()
        writeConsumerSource("src/jvmMain/kotlin/com/example/compat/UseJvmKenvy.kt")
    }

    fun runner(arguments: List<String>): GradleRunner =
        newGradleRunner(projectDir, arguments)
            .withEnvironment(kenvyScopedTestEnvironment(emptyMap()))

    private fun writeSettings(includeGoogle: Boolean, rootName: String) {
        val googleRepository = if (includeGoogle) "google()" else ""
        File(projectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    $googleRepository
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    $googleRepository
                    mavenCentral()
                }
            }
            rootProject.name = "$rootName"
            """.trimIndent()
        )
    }

    private fun legacyAndroidBuildScript(
        kotlinVersion: String,
        agpVersion: String,
        generatedVisibility: String,
        javaTarget: String
    ): String =
        """
        plugins {
            id("com.android.library") version "$agpVersion"
            id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion"
            id("io.github.adriandleon.kenvy")
        }

        group = "com.example.compat"

        kotlin {
            androidTarget {
                compilations.all {
                    compilerOptions.configure {
                        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_${javaTarget})
                    }
                }
            }
        }

        android {
            namespace = "com.example.compat"
            compileSdk = 34
            defaultConfig {
                minSdk = 24
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_${javaTarget}
                targetCompatibility = JavaVersion.VERSION_${javaTarget}
            }
        }

        kenvy {
            interfaceName.set("AppConfig")
            generatedVisibility.set("$generatedVisibility")
        }
        """.trimIndent()

    private fun androidKmpBuildScript(
        kotlinVersion: String,
        agpVersion: String,
        generatedVisibility: String,
        javaTarget: String
    ): String =
        """
        plugins {
            id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion"
            id("com.android.kotlin.multiplatform.library") version "$agpVersion"
            id("io.github.adriandleon.kenvy")
        }

        group = "com.example.compat"

        kotlin {
            android {
                namespace = "com.example.compat"
                compileSdk = 34
                minSdk = 24
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_${javaTarget})
                }
            }
        }

        kenvy {
            interfaceName.set("AppConfig")
            generatedVisibility.set("$generatedVisibility")
        }
        """.trimIndent()

    private fun writeContract() {
        File(projectDir, "kenvy.toml").writeText(
            """
            [properties.api_key]
            type = "String"
            default = "common-default"

            [properties.timeout]
            type = "Long"
            default = "5000"

            [overrides.common]
            api_key = "common-value"

            [overrides.android]
            api_key = "android-value"

            [overrides.ios]
            api_key = "ios-value"

            [overrides.jvm]
            api_key = "jvm-value"
            """.trimIndent()
        )
    }

    private fun writeConsumerSource(path: String) {
        val sourceFile = File(projectDir, path)
        sourceFile.parentFile.mkdirs()
        sourceFile.writeText(
            """
            package com.example.compat

            internal fun readConfig(): String = "${'$'}{AppConfig.apiKey}:${'$'}{AppConfig.timeout}"
            """.trimIndent()
        )
    }
}
