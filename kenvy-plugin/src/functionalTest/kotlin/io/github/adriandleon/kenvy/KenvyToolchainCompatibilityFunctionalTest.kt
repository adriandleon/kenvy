package io.github.adriandleon.kenvy

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "KENVY_COMPATIBILITY_STACK", matches = ".+",
    disabledReason = "Run via scripts/compatibility-matrix.sh (requires KENVY_COMPATIBILITY_STACK)")
class KenvyToolchainCompatibilityFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    @Test fun `baseline Android KMP stack compiles generated common and Android sources`() {
        assumeCompatibilityStack("android-baseline")
        val fixture = KenvyCompatibilityFixture(projectDir)
        fixture.writeAndroidProject(
            kotlinVersion = env("KOTLIN_VERSION", "2.1.20"),
            agpVersion = env("AGP_VERSION", "8.5.2"),
            mode = AndroidFixtureMode.LegacyAndroidLibrary,
            javaTarget = env("JAVA_TOOLCHAIN_VERSION", "17")
        )

        val result = fixture.runner(listOf("compileDebugKotlinAndroid")).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyAndroid")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileDebugKotlinAndroid")?.outcome)
        result.assertNoExpectActualBetaWarning()
        assertGeneratedActualBridge("androidMain", "android-value")
        assertGeneratedCommonBridge()
        assertSecretSafeOutput(result.output)
    }

    @Test fun `modern Android KMP stack compiles generated common and Android sources`() {
        assumeCompatibilityStack("android-modern")
        val fixture = KenvyCompatibilityFixture(projectDir)
        fixture.writeAndroidProject(
            kotlinVersion = env("KOTLIN_VERSION", "2.3.20"),
            agpVersion = env("AGP_VERSION", "9.2.0"),
            mode = AndroidFixtureMode.AndroidKmpLibrary,
            javaTarget = env("JAVA_TOOLCHAIN_VERSION", "17")
        )

        val result = fixture.runner(listOf("assemble")).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyAndroid")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":assemble")?.outcome)
        result.assertNoExpectActualBetaWarning()
        assertGeneratedActualBridge("androidMain", "android-value")
        assertGeneratedCommonBridge()
        assertSecretSafeOutput(result.output)
    }

    @Test fun `modern iOS stack compiles generated common and iOS sources`() {
        assumeCompatibilityStack("ios-modern")
        val fixture = KenvyCompatibilityFixture(projectDir)
        fixture.writeIosProject(kotlinVersion = env("KOTLIN_VERSION", "2.3.20"))

        val result = fixture.runner(listOf("compileKotlinIosArm64", "compileKotlinIosSimulatorArm64")).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyIosArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvyIosSimulatorArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinIosArm64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinIosSimulatorArm64")?.outcome)
        result.assertNoExpectActualBetaWarning()
        assertGeneratedActualBridge("iosArm64Main", "ios-value")
        assertGeneratedActualBridge("iosSimulatorArm64Main", "ios-value")
        assertGeneratedCommonBridge()
        assertSecretSafeOutput(result.output)
    }

    @Test fun `modern JVM stack compiles generated common source with Java 21 toolchain`() {
        assumeCompatibilityStack("jvm-modern")
        val fixture = KenvyCompatibilityFixture(projectDir)
        fixture.writeJvmToolchainProject(
            kotlinVersion = env("KOTLIN_VERSION", "2.3.20"),
            javaToolchain = env("JAVA_TOOLCHAIN_VERSION", "21")
        )

        val result = fixture.runner(listOf("compileKotlinJvm")).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinJvm")?.outcome)
        result.assertNoExpectActualBetaWarning()
        assertGeneratedCommonObject()
        assertSecretSafeOutput(result.output)
    }

    private fun assumeCompatibilityStack(expected: String) {
        assumeTrue(System.getenv("KENVY_COMPATIBILITY_STACK") == expected) {
            "Set KENVY_COMPATIBILITY_STACK=$expected to run this compatibility scenario."
        }
    }

    private fun env(name: String, defaultValue: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() } ?: defaultValue

    private fun assertGeneratedActualBridge(sourceSetName: String, platformValue: String) {
        val generated = File(projectDir, "build/generated/kenvy/$sourceSetName/kotlin/com/example/compat/AppConfig.kt")
        assertTrue(generated.exists(), "Expected generated source at ${generated.path}")
        val content = generated.readText()
        assertTrue(content.contains("internal actual object AppConfig"), "Expected actual declaration in:\n$content")
        assertTrue(content.contains("actual val apiKey: String = \"$platformValue\""))
        assertTrue(content.contains("val timeout: Long = 5000L"))
    }

    private fun assertGeneratedCommonBridge() {
        val generated = File(projectDir, "build/generated/kenvy/commonMain/kotlin/com/example/compat/AppConfig.kt")
        assertTrue(generated.exists(), "Expected generated source at ${generated.path}")
        val content = generated.readText()
        assertTrue(content.contains("internal expect object AppConfig"), "Expected common declaration in:\n$content")
        assertTrue(content.contains("val apiKey: String"))
        assertTrue(content.contains("val timeout: Long"))
    }

    private fun assertGeneratedCommonObject() {
        val generated = File(projectDir, "build/generated/kenvy/commonMain/kotlin/com/example/compat/AppConfig.kt")
        assertTrue(generated.exists(), "Expected generated source at ${generated.path}")
        val content = generated.readText()
        assertTrue(content.contains("internal object AppConfig"), "Expected common object in:\n$content")
        assertTrue(content.contains("val apiKey: String = \"common-value\""))
        assertTrue(content.contains("val timeout: Long = 5000L"))
    }

    private fun assertSecretSafeOutput(output: String) {
        assertFalse(output.contains("local.properties"), "Compatibility failures must not dump local.properties paths or contents.")
        assertFalse(output.contains("android-value"), "Compatibility output must not print resolved Android values.")
        assertFalse(output.contains("ios-value"), "Compatibility output must not print resolved iOS values.")
        assertFalse(output.contains("jvm-value"), "Compatibility output must not print resolved JVM values.")
    }
}
