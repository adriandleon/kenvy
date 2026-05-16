package io.github.adriandleon.kenvy

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KenvyLocalPropertiesFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private val sharedToml = """
        [properties.api_key]
        type = "String"
        default = "placeholder"
        description = "Backend API key"

        [properties.retry_count]
        type = "Int"
        default = "3"
    """.trimIndent()

    // ── Multi-project helpers ────────────────────────────────────────────────

    private fun setupMultiProject(
        rootLocalPropertiesContent: String? = null,
        sharedLocalPropertiesContent: String? = null,
        extraSharedConfig: String = "",
        arguments: List<String> = listOf(":shared:generateKenvy"),
        environment: Map<String, String>? = null
    ): GradleRunner {
        File(projectDir, "settings.gradle.kts").writeText(
            "rootProject.name = \"test-root\"\ninclude(\":shared\")"
        )
        File(projectDir, "build.gradle.kts").writeText("")

        val sharedDir = File(projectDir, "shared").also { it.mkdirs() }
        File(sharedDir, "build.gradle.kts").writeText(
            "plugins { id(\"io.github.adriandleon.kenvy\") }\ngroup = \"com.example.shared\"\n$extraSharedConfig"
        )
        File(sharedDir, "kenvy.toml").writeText(sharedToml)

        rootLocalPropertiesContent?.let { File(projectDir, "local.properties").writeText(it) }
        sharedLocalPropertiesContent?.let { File(sharedDir, "local.properties").writeText(it) }

        val runner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(arguments)
        return if (environment != null) runner.withEnvironment(environment) else runner
    }

    private fun sharedGeneratedContent(): String =
        File(projectDir, "shared/build/generated/kenvy/commonMain/kotlin/com/example/shared/Kenvy.kt").readText()

    // ── Single-project helpers ───────────────────────────────────────────────

    private fun setupSingleProject(
        localPropertiesContent: String? = null,
        extraConfig: String = "",
        arguments: List<String> = listOf("generateKenvy"),
        environment: Map<String, String>? = null
    ): GradleRunner {
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"test\"")
        File(projectDir, "build.gradle.kts").writeText(
            "plugins { id(\"io.github.adriandleon.kenvy\") }\ngroup = \"com.example.test\"\n$extraConfig"
        )
        File(projectDir, "kenvy.toml").writeText(sharedToml)
        localPropertiesContent?.let { File(projectDir, "local.properties").writeText(it) }
        val runner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(arguments)
        return if (environment != null) runner.withEnvironment(environment) else runner
    }

    private fun singleGeneratedContent(): String =
        File(projectDir, "build/generated/kenvy/commonMain/kotlin/com/example/test/Kenvy.kt").readText()

    // ── AC1: Root local.properties is visible from module-applied Kenvy ──────

    @Test fun `module task resolves value from root local properties when no shared local properties exists`() {
        val result = setupMultiProject(
            rootLocalPropertiesContent = "api_key=root-secret"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":shared:generateKenvy")?.outcome)
        assertTrue(sharedGeneratedContent().contains("val apiKey: String = \"root-secret\""))
    }

    @Test fun `module task warns when root local properties is not gitignored`() {
        val result = setupMultiProject(
            rootLocalPropertiesContent = "api_key=root-secret"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":shared:generateKenvy")?.outcome)
        assertTrue(result.output.contains("root local.properties"), "Warning must distinguish root local.properties path")
        assertTrue(result.output.contains("is not in .gitignore"))
        assertTrue(result.output.contains("suggested entry: local.properties"))
    }

    @Test fun `module task succeeds with root local properties providing all required values`() {
        val result = setupMultiProject(
            rootLocalPropertiesContent = "api_key=root-api-key\nretry_count=5"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":shared:generateKenvy")?.outcome)
        val content = sharedGeneratedContent()
        assertTrue(content.contains("val apiKey: String = \"root-api-key\""))
        assertTrue(content.contains("val retryCount: Int = 5"))
    }

    // ── AC2: Module local.properties has explicit precedence over root ────────

    @Test fun `module local properties overrides root local properties for the same key`() {
        val result = setupMultiProject(
            rootLocalPropertiesContent = "api_key=root-secret",
            sharedLocalPropertiesContent = "api_key=module-secret"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":shared:generateKenvy")?.outcome)
        assertTrue(sharedGeneratedContent().contains("val apiKey: String = \"module-secret\""))
    }

    @Test fun `root-only keys are visible when module overrides only some keys`() {
        val result = setupMultiProject(
            rootLocalPropertiesContent = "api_key=root-secret\nretry_count=7",
            sharedLocalPropertiesContent = "api_key=module-secret"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":shared:generateKenvy")?.outcome)
        val content = sharedGeneratedContent()
        assertTrue(content.contains("val apiKey: String = \"module-secret\""))
        assertTrue(content.contains("val retryCount: Int = 7"))
    }

    @Test fun `scoped root key is used when module defines only the base key`() {
        // Root has api_key.android.debug; module has only api_key.
        // After merging, merged map has both. When platform=android variant=debug,
        // the scoped key api_key.android.debug wins over the base api_key from module.
        val result = setupMultiProject(
            rootLocalPropertiesContent = "api_key.android.debug=root-android-debug",
            sharedLocalPropertiesContent = "api_key=module-base",
            extraSharedConfig = "kenvy {\n    platform.set(\"android\")\n    variant.set(\"debug\")\n}"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":shared:generateKenvy")?.outcome)
        assertTrue(sharedGeneratedContent().contains("val apiKey: String = \"root-android-debug\""))
    }

    @Test fun `module scoped key overrides root scoped key for the same scope`() {
        // Both root and module define api_key.android.debug; module wins.
        val result = setupMultiProject(
            rootLocalPropertiesContent = "api_key.android.debug=root-android-debug",
            sharedLocalPropertiesContent = "api_key.android.debug=module-android-debug",
            extraSharedConfig = "kenvy {\n    platform.set(\"android\")\n    variant.set(\"debug\")\n}"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":shared:generateKenvy")?.outcome)
        assertTrue(sharedGeneratedContent().contains("val apiKey: String = \"module-android-debug\""))
    }

    // ── AC3: Custom local properties file DSL ──────────────────────────────

    @Test fun `extension DSL from() appends custom file so its value wins over empty defaults`() {
        val customDir = File(projectDir, "config").also { it.mkdirs() }
        File(customDir, "secrets.properties").writeText("api_key=custom-secret")

        val result = setupMultiProject(
            extraSharedConfig = "kenvy {\n    localPropertiesFiles.from(layout.projectDirectory.file(\"../config/secrets.properties\"))\n}"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":shared:generateKenvy")?.outcome)
        assertTrue(sharedGeneratedContent().contains("val apiKey: String = \"custom-secret\""))
    }

    @Test fun `extension DSL setFrom() replaces defaults so root local properties is not consulted`() {
        val customDir = File(projectDir, "config").also { it.mkdirs() }
        File(customDir, "secrets.properties").writeText("api_key=setfrom-secret")
        // Root local.properties has a value that should NOT be used after setFrom
        File(projectDir, "local.properties").writeText("api_key=root-should-not-win")

        val result = setupMultiProject(
            extraSharedConfig = "kenvy {\n    localPropertiesFiles.setFrom(layout.projectDirectory.file(\"../config/secrets.properties\"))\n}"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":shared:generateKenvy")?.outcome)
        assertTrue(sharedGeneratedContent().contains("val apiKey: String = \"setfrom-secret\""))
    }

    // ── Single-project: no duplicate lookup noise ────────────────────────────

    @Test fun `single-project build resolves from root local properties`() {
        val result = setupSingleProject(
            localPropertiesContent = "api_key=single-project-secret"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKenvy")?.outcome)
        assertTrue(singleGeneratedContent().contains("val apiKey: String = \"single-project-secret\""))
    }

    // ── AC2: Missing value diagnostics list checked local properties files ─────

    @Test fun `unresolved placeholder in multi-project build lists both root and module local files`() {
        val result = setupMultiProject(
            // No local properties supplied — api_key remains a placeholder
        ).buildAndFail()

        assertTrue(result.output.contains("Checked local properties files:"), "Diagnostics must list checked files")
        assertTrue(result.output.contains("root"), "Diagnostics must identify root local.properties")
        assertTrue(result.output.contains("local.properties"), "Diagnostics must mention local.properties")
        assertTrue(result.output.contains("(missing)"), "Diagnostics must note files that are missing")
        assertTrue(result.output.contains("Docs:"), "Diagnostics must include docs pointer")
    }

    @Test fun `unresolved placeholder in single-project build labels default local properties as root`() {
        val result = setupSingleProject(
            // No local properties supplied — api_key remains a placeholder
        ).buildAndFail()

        assertTrue(result.output.contains("Checked local properties files:"), "Diagnostics must list checked files")
        assertTrue(result.output.contains("- root:"), "Single-project default local.properties should be labeled root")
        assertFalse(result.output.contains("- custom:"), "Default single-project local.properties should not be labeled custom")
    }

    @Test fun `unresolved placeholder with setFrom custom file lists only custom file`() {
        val customDir = File(projectDir, "config").also { it.mkdirs() }
        // custom file exists but does not have api_key — still a missing value
        File(customDir, "secrets.properties").writeText("retry_count=3")

        val result = setupMultiProject(
            extraSharedConfig = "kenvy {\n    localPropertiesFiles.setFrom(layout.projectDirectory.file(\"../config/secrets.properties\"))\n}"
        ).buildAndFail()

        assertTrue(result.output.contains("Checked local properties files:"), "Diagnostics must list checked files")
        assertTrue(result.output.contains("secrets.properties"), "Diagnostics must name the custom file")
        // When setFrom replaces defaults, root and module local.properties are not listed
        assertFalse(result.output.contains("- root:"), "Root local.properties should not be listed when setFrom replaces defaults")
        assertFalse(result.output.contains("- module"), "Module local.properties should not be listed when setFrom replaces defaults")
    }

    // ── Regression: KENVY_ env beats both root and module local values ────────

    @Test fun `KENVY_ environment variable beats root local properties`() {
        val result = setupMultiProject(
            rootLocalPropertiesContent = "api_key=root-secret",
            environment = mapOf("KENVY_API_KEY" to "env-wins")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":shared:generateKenvy")?.outcome)
        assertTrue(sharedGeneratedContent().contains("val apiKey: String = \"env-wins\""))
    }

    @Test fun `KENVY_ environment variable beats both root and module local properties`() {
        val result = setupMultiProject(
            rootLocalPropertiesContent = "api_key=root-secret",
            sharedLocalPropertiesContent = "api_key=module-secret",
            environment = mapOf("KENVY_API_KEY" to "env-wins")
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":shared:generateKenvy")?.outcome)
        assertTrue(sharedGeneratedContent().contains("val apiKey: String = \"env-wins\""))
    }
}
