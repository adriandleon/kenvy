package io.github.adriandleon.kenvy

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal val kenvyIsolationArgs = listOf(
    "generateKenvy",
    "--configuration-cache",
    "-Dorg.gradle.unsafe.isolated-projects=true"
)

internal fun newGradleRunner(projectDir: File, arguments: List<String>): GradleRunner =
    GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(arguments)

internal fun kenvyScopedTestEnvironment(environment: Map<String, String>): Map<String, String> =
    System.getenv().filterKeys { name -> !name.startsWith("KENVY_") } + environment

internal class KenvyMultiModuleFixture(private val projectDir: File) {
    val moduleNames: List<String> = (1..20).map { "module%02d".format(it) }

    fun writeProject() {
        File(projectDir, "settings.gradle.kts").writeText(
            buildString {
                appendLine("rootProject.name = \"kenvy-performance-test\"")
                moduleNames.forEach { moduleName ->
                    appendLine("include(\":$moduleName\")")
                }
            }
        )
        File(projectDir, "gradle.properties").writeText(
            """
            org.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m
            """.trimIndent()
        )

        File(projectDir, "build.gradle.kts").writeText(
            buildString {
                appendLine("tasks.register(\"verifyKenvy\") {")
                appendLine("    dependsOn(")
                moduleNames.forEachIndexed { index, moduleName ->
                    val suffix = if (index == moduleNames.lastIndex) "" else ","
                    appendLine("        \":$moduleName:generateKenvy\"$suffix")
                }
                appendLine("    )")
                appendLine("}")
            }
        )

        moduleNames.forEachIndexed { index, moduleName ->
            val moduleDir = File(projectDir, moduleName).apply { mkdirs() }
            File(moduleDir, "build.gradle.kts").writeText(
                """
                plugins { id("io.github.adriandleon.kenvy") }

                group = "com.example.performance"

                tasks.register("compileMarker")
                tasks.register("testMarker")
                """.trimIndent()
            )
            File(moduleDir, "kenvy.toml").writeText(
                """
                [properties.service_url]
                type = "String"
                default = "https://example.com/${moduleName}"

                [properties.retry_count]
                type = "Int"
                default = "${index + 1}"
                """.trimIndent()
            )
        }
    }

    fun runner(arguments: List<String> = listOf("verifyKenvy")): GradleRunner =
        newGradleRunner(projectDir, arguments)
}

internal fun BuildResult.assertAllModuleTasksHaveOutcome(
    moduleNames: List<String>,
    taskName: String,
    expected: TaskOutcome
) {
    moduleNames.forEach { moduleName ->
        assertEquals(expected, task(":$moduleName:$taskName")?.outcome, "Unexpected outcome for :$moduleName:$taskName")
    }
}

internal fun BuildResult.assertNoModuleTaskWasScheduled(moduleNames: List<String>, taskName: String) {
    moduleNames.forEach { moduleName ->
        assertNull(task(":$moduleName:$taskName"), "Unexpected task in graph: :$moduleName:$taskName")
    }
}

internal fun BuildResult.assertConfigurationCacheStored() {
    assertTrue(output.contains("Configuration cache entry stored."), "Expected configuration cache storage evidence.\n$output")
}

internal fun BuildResult.assertConfigurationCacheReused() {
    assertTrue(output.contains("Configuration cache entry reused."), "Expected configuration cache reuse evidence.\n$output")
}

internal fun BuildResult.assertNoIsolationOrConfigurationCacheProblems() {
    val normalizedOutput = output.lowercase()
    assertFalse(
        normalizedOutput.contains("configuration cache problems found in this build"),
        "Build reported configuration cache problems.\n$output"
    )
    assertFalse(
        normalizedOutput.contains("isolated project violation"),
        "Build reported isolated project violations.\n$output"
    )
    assertFalse(
        normalizedOutput.contains("is not supported with the configuration cache"),
        "Build used configuration-cache-incompatible logic.\n$output"
    )
}

internal fun BuildResult.assertNoExpectActualBetaWarning() {
    assertFalse(
        output.contains("expect/actual classes are in Beta") ||
            output.contains("expect/actual for classes"),
        "Kenvy generated sources must not emit expect/actual beta/classifier warning noise.\n$output"
    )
    assertFalse(
        output.contains("-Xexpect-actual-classes"),
        "Kenvy must not require consumer compiler flag suppression.\n$output"
    )
    assertFalse(
        output.contains("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"),
        "Suppression id must not leak into Gradle output.\n$output"
    )
}
