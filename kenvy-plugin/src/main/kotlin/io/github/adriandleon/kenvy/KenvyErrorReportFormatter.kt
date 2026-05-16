package io.github.adriandleon.kenvy

import org.gradle.api.GradleException

internal const val KENVY_RESOLUTION_CHAIN =
    "properties.default -> overrides.common -> overrides.<platform> -> " +
        "overrides.<platform>.<variant> -> local.properties:<name> -> " +
        "local.properties:<name>.<platform> -> local.properties:<name>.<platform>.<variant> -> environment:<NAME> (legacy opt-in only) -> environment:KENVY_<NAME>"

internal enum class KenvyConfigurationIssueCategory(val heading: String) {
    MISSING_VALUE("Missing values"),
    TYPE_MISMATCH("Type mismatches"),
    RESOLUTION_CONFLICT("Resolution conflicts")
}

internal data class KenvyConfigurationIssue(
    val category: KenvyConfigurationIssueCategory,
    val summary: String,
    val details: List<String> = emptyList()
) {
    companion object {
        fun missingValue(
            resolvedValue: ResolvedKenvyValue,
            diffSnippet: String,
            checkedLocalFiles: List<LocalPropertiesFileInfo> = emptyList()
        ): KenvyConfigurationIssue =
            KenvyConfigurationIssue(
                category = KenvyConfigurationIssueCategory.MISSING_VALUE,
                summary = "${resolvedValue.property.name} (${resolvedValue.property.type.displayName()})",
                details = buildList {
                    add("Resolution source: ${resolvedValue.source.displayName()}")
                    add("Add to local.properties:")
                    add("  $diffSnippet")
                    resolvedValue.property.helpUrl?.let { add("Setup: $it") }
                    if (checkedLocalFiles.isNotEmpty()) {
                        add("Checked local properties files:")
                        checkedLocalFiles.forEach { info ->
                            val existsLabel = if (info.exists) "" else " (missing)"
                            add("  - ${info.label}: ${info.absolutePath}$existsLabel")
                        }
                        add("Docs: docs/examples.md#multi-module-build-with-shared-module-and-root-localproperties")
                    }
                }
            )

        fun typeMismatch(
            resolvedValue: ResolvedKenvyValue,
            expectedType: String
        ): KenvyConfigurationIssue =
            KenvyConfigurationIssue(
                category = KenvyConfigurationIssueCategory.TYPE_MISMATCH,
                summary = "${resolvedValue.property.name} expected $expectedType but resolved to " +
                    "'${resolvedValue.displayValue()}' from ${resolvedValue.source.displayName()}."
            )

        fun resolutionConflict(
            summary: String,
            details: List<String> = emptyList()
        ): KenvyConfigurationIssue =
            KenvyConfigurationIssue(
                category = KenvyConfigurationIssueCategory.RESOLUTION_CONFLICT,
                summary = summary,
                details = details
            )
    }
}

internal object KenvyErrorReportFormatter {
    fun format(issues: List<KenvyConfigurationIssue>): String {
        val normalizedIssues = issues.filterNotNull()
        require(normalizedIssues.isNotEmpty()) { "Kenvy error report requires at least one issue." }

        return buildString {
            appendLine("Kenvy: Configuration has ${normalizedIssues.size} issue(s).")
            KenvyConfigurationIssueCategory.values().forEach { category ->
                val grouped = normalizedIssues.filter { it.category == category }
                if (grouped.isEmpty()) return@forEach

                appendLine()
                appendLine("${category.heading}:")
                grouped.forEach { issue ->
                    appendLine("  - ${issue.summary}")
                    issue.details.forEach { detail ->
                        appendLine("    $detail")
                    }
                }
            }
        }.trimEnd()
    }
}

internal class KenvyConfigurationConflictException(
    issues: List<KenvyConfigurationIssue>
) : GradleException(KenvyErrorReportFormatter.format(issues)) {
    constructor(issue: KenvyConfigurationIssue) : this(listOf(issue))
}

internal fun ResolutionSource.displayName(): String =
    when (this) {
        ResolutionSource.DEFAULT -> "default"
        ResolutionSource.COMMON_OVERRIDE -> "overrides.common"
        ResolutionSource.PLATFORM_OVERRIDE -> "platform override"
        ResolutionSource.VARIANT_OVERRIDE -> "variant override"
        ResolutionSource.EXTERNAL_PROVIDER -> "external provider"
        ResolutionSource.LOCAL_PROPERTIES -> "local.properties"
        ResolutionSource.ENVIRONMENT -> "environment"
    }

internal fun PropertyType.displayName(): String =
    when (this) {
        PropertyType.STRING -> "String"
        PropertyType.INT -> "Int"
        PropertyType.BOOLEAN -> "Boolean"
        PropertyType.LONG -> "Long"
    }
