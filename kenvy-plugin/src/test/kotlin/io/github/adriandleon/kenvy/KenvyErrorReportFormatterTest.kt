package io.github.adriandleon.kenvy

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class KenvyErrorReportFormatterTest {

    @Test fun `missing value with checked files lists file paths and docs pointer`() {
        val report = KenvyErrorReportFormatter.format(
            listOf(
                KenvyConfigurationIssue.missingValue(
                    resolvedValue = ResolvedKenvyValue(
                        property = KenvyProperty(name = "api_key", type = PropertyType.STRING, defaultValue = null),
                        resolvedValue = null,
                        source = ResolutionSource.DEFAULT
                    ),
                    diffSnippet = "+ api_key=<YOUR_VALUE>",
                    checkedLocalFiles = listOf(
                        LocalPropertiesFileInfo(label = "root", absolutePath = "/project/local.properties", exists = false),
                        LocalPropertiesFileInfo(label = "module :shared", absolutePath = "/project/shared/local.properties", exists = false)
                    )
                )
            )
        )

        assertContains(report, "Checked local properties files:")
        assertContains(report, "- root: /project/local.properties (missing)")
        assertContains(report, "- module :shared: /project/shared/local.properties (missing)")
        assertContains(report, "Docs: docs/examples.md#multi-module-build-with-shared-module-and-root-localproperties")
    }

    @Test fun `missing value with existing checked file shows no missing label`() {
        val report = KenvyErrorReportFormatter.format(
            listOf(
                KenvyConfigurationIssue.missingValue(
                    resolvedValue = ResolvedKenvyValue(
                        property = KenvyProperty(name = "api_key", type = PropertyType.STRING, defaultValue = "placeholder"),
                        resolvedValue = "placeholder",
                        source = ResolutionSource.LOCAL_PROPERTIES
                    ),
                    diffSnippet = "+ api_key=<YOUR_VALUE>",
                    checkedLocalFiles = listOf(
                        LocalPropertiesFileInfo(label = "root", absolutePath = "/project/local.properties", exists = true)
                    )
                )
            )
        )

        assertContains(report, "- root: /project/local.properties")
        assertFalse(report.contains("/project/local.properties (missing)"))
    }

    @Test fun `missing value without checked files omits file list and docs pointer`() {
        val report = KenvyErrorReportFormatter.format(
            listOf(
                KenvyConfigurationIssue.missingValue(
                    resolvedValue = ResolvedKenvyValue(
                        property = KenvyProperty(name = "api_key", type = PropertyType.STRING, defaultValue = null),
                        resolvedValue = null,
                        source = ResolutionSource.DEFAULT
                    ),
                    diffSnippet = "+ api_key=<YOUR_VALUE>"
                )
            )
        )

        assertFalse(report.contains("Checked local properties files:"))
        assertFalse(report.contains("Docs:"))
    }

    @Test fun `sensitive property values are not printed in checked files section`() {
        val report = KenvyErrorReportFormatter.format(
            listOf(
                KenvyConfigurationIssue.missingValue(
                    resolvedValue = ResolvedKenvyValue(
                        property = KenvyProperty(name = "secret_key", type = PropertyType.STRING, defaultValue = "placeholder", sensitive = true),
                        resolvedValue = "placeholder",
                        source = ResolutionSource.LOCAL_PROPERTIES
                    ),
                    diffSnippet = "+ secret_key=<YOUR_VALUE>",
                    checkedLocalFiles = listOf(
                        LocalPropertiesFileInfo(label = "root", absolutePath = "/project/local.properties", exists = true)
                    )
                )
            )
        )

        assertContains(report, "Checked local properties files:")
        assertFalse(report.contains("placeholder"))
    }

    @Test fun `formatter groups issues with diff snippets help urls and masked values`() {
        val report = KenvyErrorReportFormatter.format(
            listOf(
                KenvyConfigurationIssue.missingValue(
                    resolvedValue = ResolvedKenvyValue(
                        property = KenvyProperty(
                            name = "api_key",
                            type = PropertyType.STRING,
                            defaultValue = "placeholder",
                            helpUrl = "https://wiki.internal/api-setup"
                        ),
                        resolvedValue = "placeholder",
                        source = ResolutionSource.DEFAULT
                    ),
                    diffSnippet = "+ api_key=<YOUR_VALUE>"
                ),
                KenvyConfigurationIssue.typeMismatch(
                    resolvedValue = ResolvedKenvyValue(
                        property = KenvyProperty(
                            name = "secret_count",
                            type = PropertyType.INT,
                            defaultValue = "1",
                            sensitive = true
                        ),
                        resolvedValue = "super-secret",
                        source = ResolutionSource.LOCAL_PROPERTIES
                    ),
                    expectedType = "Int"
                ),
                KenvyConfigurationIssue.resolutionConflict(
                    summary = "[overrides.android.debug] key 'api-key' does not match a declared property.",
                    details = listOf(
                        "Resolution chain: properties.default -> overrides.common -> overrides.<platform> -> overrides.<platform>.<variant> -> local.properties:<name> -> local.properties:<name>.<platform> -> local.properties:<name>.<platform>.<variant> -> environment:<NAME> (legacy opt-in only) -> environment:KENVY_<NAME>"
                    )
                )
            )
        )

        assertContains(report, "Kenvy: Configuration has 3 issue(s).")
        assertContains(report, "Missing values:")
        assertContains(report, "Type mismatches:")
        assertContains(report, "Resolution conflicts:")
        assertContains(report, "+ api_key=<YOUR_VALUE>")
        assertContains(report, "Setup: https://wiki.internal/api-setup")
        assertContains(report, "resolved to '****' from local.properties")
        assertContains(report, "Resolution chain: properties.default -> overrides.common")
        assertFalse(report.contains("super-secret"))
    }
}
