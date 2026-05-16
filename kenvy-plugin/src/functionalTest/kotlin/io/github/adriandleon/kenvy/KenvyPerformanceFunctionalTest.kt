package io.github.adriandleon.kenvy

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

class KenvyPerformanceFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private val fixture by lazy { KenvyMultiModuleFixture(projectDir) }
    private val moduleNames: List<String> get() = fixture.moduleNames

    private fun setupMultiModuleProject(arguments: List<String> = listOf("verifyKenvy")) =
        fixture.apply { writeProject() }.runner(arguments)

    @Test
    fun `multi-module no-change build keeps generate tasks up-to-date`() {
        val runner = setupMultiModuleProject()

        runner.build()
        val secondRun = runner.build()

        secondRun.assertAllModuleTasksHaveOutcome(moduleNames, "generateKenvy", TaskOutcome.UP_TO_DATE)
        secondRun.assertAllModuleTasksHaveOutcome(moduleNames, "generateKenvyExample", TaskOutcome.UP_TO_DATE)
        secondRun.assertNoModuleTaskWasScheduled(moduleNames, "compileMarker")
        secondRun.assertNoModuleTaskWasScheduled(moduleNames, "testMarker")
    }

    @Test
    fun `single module contract change only re-executes affected generation tasks`() {
        val runner = setupMultiModuleProject(arguments = listOf("verifyKenvy", "--info"))

        runner.build()

        File(projectDir, "module07/kenvy.toml").appendText(
            """

            [properties.new_flag]
            type = "Boolean"
            default = "true"
            """.trimIndent()
        )

        val changedRun = runner.build()

        assertEquals(TaskOutcome.SUCCESS, changedRun.task(":module07:generateKenvyExample")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, changedRun.task(":module07:generateKenvy")?.outcome)

        moduleNames
            .filterNot { it == "module07" }
            .forEach { moduleName ->
                assertEquals(TaskOutcome.UP_TO_DATE, changedRun.task(":$moduleName:generateKenvyExample")?.outcome)
                assertEquals(TaskOutcome.UP_TO_DATE, changedRun.task(":$moduleName:generateKenvy")?.outcome)
            }

        changedRun.assertNoModuleTaskWasScheduled(moduleNames, "compileMarker")
        changedRun.assertNoModuleTaskWasScheduled(moduleNames, "testMarker")
    }
}
