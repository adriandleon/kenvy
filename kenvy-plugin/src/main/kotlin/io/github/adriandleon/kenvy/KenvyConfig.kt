package io.github.adriandleon.kenvy

import com.akuleshov7.ktoml.parsers.TomlParser
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.tree.nodes.*
import com.akuleshov7.ktoml.tree.nodes.pairs.values.*
import com.akuleshov7.ktoml.tree.nodes.pairs.values.TomlBoolean as KTomlBoolean
import org.gradle.api.GradleException
import java.io.File

internal object KenvyParser {

    private const val PROPERTIES_TABLE_PREFIX = "properties."
    private const val OVERRIDES_TABLE_PREFIX = "overrides."
    private const val OVERRIDES_COMMON_KEY = "overrides.common"
    private const val OVERRIDES_RESERVED_PLATFORM_NAME = "common"
    private const val SECURITY_TABLE_KEY = "security"
    private const val SECURITY_SECRET_FILES_KEY = "secret_files"
    private const val DEFAULT_SECRET_FILE = "local.properties"

    fun parse(file: File): List<KenvyProperty> {
        if (!file.exists()) {
            throw GradleException(buildMissingContractMessage(file, gradleProjectPath = null, gradleProjectDir = null))
        }

        val tomlDocument = try {
            TomlParser(TomlInputConfig()).parseString(file.readText())
        } catch (e: Exception) {
            throw GradleException(
                "Kenvy: Failed to parse kenvy.toml at '${file.absolutePath}'. " +
                "Parse error: ${e.message}"
            )
        }

        return extractProperties(tomlDocument, file)
    }

    fun parseContract(
        file: File,
        gradleProjectPath: String? = null,
        gradleProjectDir: File? = null
    ): ParsedKenvyContract {
        if (!file.exists()) {
            throw GradleException(buildMissingContractMessage(file, gradleProjectPath, gradleProjectDir))
        }

        val tomlDocument = try {
            TomlParser(TomlInputConfig()).parseString(file.readText())
        } catch (e: Exception) {
            throw GradleException(
                "Kenvy: Failed to parse kenvy.toml at '${file.absolutePath}'. " +
                "Parse error: ${e.message}"
            )
        }

        val properties = extractProperties(tomlDocument, file)
        val overrides = extractOverrides(tomlDocument, file, properties)
        val security = extractSecurity(tomlDocument, file)
        return ParsedKenvyContract(
            properties = properties,
            commonOverrides = overrides.common,
            platformOverrides = overrides.platform,
            variantOverrides = overrides.variant,
            security = security
        )
    }

    private fun extractProperties(doc: TomlNode, sourceFile: File): List<KenvyProperty> {
        val properties = mutableListOf<KenvyProperty>()

        // Walk all top-level and nested table nodes
        fun walkNodes(nodes: List<TomlNode>) {
            for (node in nodes) {
                if (node is TomlTable) {
                    val key = node.fullTableKey.toString()
                    if (key.startsWith(PROPERTIES_TABLE_PREFIX)) {
                        val propName = key.removePrefix(PROPERTIES_TABLE_PREFIX)
                        if (propName.isNotEmpty() && !propName.contains('.')) {
                            properties.add(extractProperty(propName, node, sourceFile))
                        }
                    }
                }
                walkNodes(node.children)
            }
        }

        walkNodes(doc.children)
        return properties
    }

    private fun extractProperty(name: String, table: TomlTable, sourceFile: File): KenvyProperty {
        var typeString: String? = null
        var defaultValue: String? = null
        var description: String? = null
        var helpUrl: String? = null
        var sensitive: Boolean = false

        for (child in table.children) {
            val (key, value) = when (child) {
                is TomlKeyValuePrimitive -> child.key to child.value
                is TomlKeyValueArray -> child.key to child.value
                else -> continue
            }

            // Using last() as per ktoml 0.5.2 recommendation for key parts
            val keyName = try { key.last() } catch (_: NoSuchElementException) { continue }
            when (keyName) {
                "type"        -> typeString   = extractStringValue(value, name, sourceFile)
                "default"     -> defaultValue = extractStringValue(value, name, sourceFile)
                "description" -> description  = extractStringValue(value, name, sourceFile)
                "help_url"    -> helpUrl      = extractStringValue(value, name, sourceFile)
                "sensitive"   -> sensitive    = extractBooleanValue(value, name, sourceFile)
                else -> throw GradleException(
                    "Kenvy: Property '$name' in '${sourceFile.name}' has an unknown field '$keyName'. " +
                    "Check for typos. Supported fields: type, default, description, help_url, sensitive"
                )
            }
        }

        if (typeString == null) {
            throw GradleException(
                "Kenvy: Property '$name' in '${sourceFile.name}' is missing required field 'type'. " +
                "Add: type = \"String\" (or Int, Boolean, Long)"
            )
        }

        val propertyType = PropertyType.fromString(typeString)
            ?: throw GradleException(
                "Kenvy: Property '$name' in '${sourceFile.name}' has unsupported type '$typeString'. " +
                "Supported types: String, Int, Boolean, Long"
            )

        // Normalize metadata: trim whitespace and treat empty/blank as null
        description = description?.trim()?.takeIf { it.isNotEmpty() }
        helpUrl = helpUrl?.trim()?.takeIf { it.isNotEmpty() }

        return KenvyProperty(
            name = name,
            type = propertyType,
            defaultValue = defaultValue,
            description = description,
            helpUrl = helpUrl,
            sensitive = sensitive
        )
    }

    private fun extractStringValue(value: TomlValue, propertyName: String, sourceFile: File): String =
        when (value) {
            is TomlBasicString -> value.content.toString()
            is TomlLiteralString -> value.content.toString()
            else -> throw GradleException(
                "Kenvy: Property '$propertyName' in '${sourceFile.name}' has a non-string value " +
                "(got ${value::class.simpleName}). Use quoted strings: type = \"String\""
            )
        }

    private fun extractBooleanValue(value: TomlValue, propertyName: String, sourceFile: File): Boolean =
        when (value) {
            is KTomlBoolean -> (value.content as? Boolean)
                ?: throw GradleException(
                    "Kenvy: Property '$propertyName' in '${sourceFile.name}' has an invalid value for 'sensitive' " +
                    "(failed to parse boolean content). Use: sensitive = true or sensitive = false"
                )
            else -> throw GradleException(
                "Kenvy: Property '$propertyName' in '${sourceFile.name}' has an invalid value for 'sensitive' " +
                "(got ${value::class.simpleName}). Use lowercase: sensitive = true or sensitive = false"
            )
        }

    private fun extractOverrides(
        doc: TomlNode,
        sourceFile: File,
        knownProperties: List<KenvyProperty>
    ): ExtractedOverrides {
        val commonOverrides = mutableMapOf<String, String>()
        val platformOverrides = mutableMapOf<String, MutableMap<String, String>>()
        val variantOverrides = mutableMapOf<String, MutableMap<String, MutableMap<String, String>>>()
        val knownNames = knownProperties.map { it.name }.toSet()
        val conflictIssues = mutableListOf<KenvyConfigurationIssue>()

        fun walkNodes(nodes: List<TomlNode>) {
            for (node in nodes) {
                if (node is TomlTable) {
                    val tableKey = node.fullTableKey.toString()
                    if (tableKey.startsWith(OVERRIDES_TABLE_PREFIX)) {
                        val tablePath = tableKey.split('.')
                        when {
                            tablePath.size > 3 ->
                                throw malformedOverrideTable(sourceFile, tableKey)

                            tablePath.size == 2 && tableKey == OVERRIDES_COMMON_KEY ->
                                readOverrideEntries(
                                    node = node,
                                    sourceFile = sourceFile,
                                    tableKey = tableKey,
                                    knownNames = knownNames,
                                    conflictIssues = conflictIssues
                                ).forEach { (name, value) -> commonOverrides[name] = value }

                            tablePath.size == 2 -> {
                                val platform = tablePath[1].trim()
                                if (platform.isBlank() || platform == OVERRIDES_RESERVED_PLATFORM_NAME) {
                                    throw malformedOverrideTable(sourceFile, tableKey)
                                }
                                val target = platformOverrides.getOrPut(platform) { linkedMapOf() }
                                readOverrideEntries(
                                    node = node,
                                    sourceFile = sourceFile,
                                    tableKey = tableKey,
                                    knownNames = knownNames,
                                    conflictIssues = conflictIssues
                                ).forEach { (name, value) -> target[name] = value }
                            }

                            tablePath.size == 3 -> {
                                if (node.isSynthetic && tableLooksLikeMisplacedOverride(node, knownNames)) {
                                    throw malformedOverrideTable(sourceFile, tableKey)
                                }
                                val platform = tablePath[1].trim()
                                val variant = tablePath[2].trim()
                                if (
                                    platform.isBlank() ||
                                    variant.isBlank() ||
                                    platform == OVERRIDES_RESERVED_PLATFORM_NAME ||
                                    platform == "variants"
                                ) {
                                    throw malformedOverrideTable(sourceFile, tableKey)
                                }
                                val platformVariants = variantOverrides.getOrPut(platform) { linkedMapOf() }
                                val target = platformVariants.getOrPut(variant) { linkedMapOf() }
                                readOverrideEntries(
                                    node = node,
                                    sourceFile = sourceFile,
                                    tableKey = tableKey,
                                    knownNames = knownNames,
                                    conflictIssues = conflictIssues
                                ).forEach { (name, value) -> target[name] = value }
                            }
                        }
                    } else if (
                        !tableKey.startsWith(PROPERTIES_TABLE_PREFIX) &&
                        tableLooksLikeMisplacedOverride(node, knownNames)
                    ) {
                        throw malformedOverrideTable(sourceFile, tableKey)
                    }
                }
                walkNodes(node.children)
            }
        }

        walkNodes(doc.children)
        if (conflictIssues.isNotEmpty()) {
            throw KenvyConfigurationConflictException(conflictIssues)
        }
        return ExtractedOverrides(
            common = commonOverrides,
            platform = platformOverrides,
            variant = variantOverrides
        )
    }

    private fun readOverrideEntries(
        node: TomlTable,
        sourceFile: File,
        tableKey: String,
        knownNames: Set<String>,
        conflictIssues: MutableList<KenvyConfigurationIssue>
    ): Map<String, String> {
        val overrides = linkedMapOf<String, String>()
        for (child in node.children) {
            val (childKey, childValue) = when (child) {
                is TomlKeyValuePrimitive -> child.key to child.value
                else -> continue
            }
            val overrideKey = try { childKey.last() } catch (_: NoSuchElementException) { continue }
            if (childKey.toString() != overrideKey) {
                conflictIssues +=
                    KenvyConfigurationIssue.resolutionConflict(
                        summary = "[$tableKey] key '${childKey}' in '${sourceFile.name}' must be a direct " +
                            "[properties.<name>] key, not a nested or dotted key.",
                        details = listOf("Resolution chain: $KENVY_RESOLUTION_CHAIN")
                    )
                continue
            }
            if (overrideKey !in knownNames) {
                conflictIssues +=
                    KenvyConfigurationIssue.resolutionConflict(
                        summary = "[$tableKey] key '$overrideKey' in '${sourceFile.name}' " +
                            "does not match any declared [properties.<name>] entry.",
                        details = listOf("Resolution chain: $KENVY_RESOLUTION_CHAIN")
                    )
                continue
            }
            overrides[overrideKey] = extractStringValue(childValue, overrideKey, sourceFile)
        }
        return overrides
    }

    private fun tableLooksLikeMisplacedOverride(node: TomlTable, knownNames: Set<String>): Boolean =
        node.children.any { child ->
            child is TomlKeyValuePrimitive &&
                try {
                    child.key.last() in knownNames
                } catch (_: NoSuchElementException) {
                    false
                }
        }

    private fun malformedOverrideTable(sourceFile: File, tableKey: String): GradleException =
        KenvyConfigurationConflictException(
            KenvyConfigurationIssue.resolutionConflict(
                summary = "Invalid override table [$tableKey] in '${sourceFile.name}'. " +
                    "Supported override tables are [overrides.common], [overrides.<platform>], and [overrides.<platform>.<variant>].",
                details = listOf("Resolution chain: $KENVY_RESOLUTION_CHAIN")
            )
        )

    private fun extractSecurity(doc: TomlNode, sourceFile: File): KenvySecuritySettings {
        var configuredSecretFiles: List<String>? = null

        fun walkNodes(nodes: List<TomlNode>) {
            for (node in nodes) {
                if (node is TomlTable && node.fullTableKey.toString() == SECURITY_TABLE_KEY) {
                    for (child in node.children) {
                        val (key, value) = when (child) {
                            is TomlKeyValueArray -> child.key to child.value
                            is TomlKeyValuePrimitive -> child.key to child.value
                            else -> continue
                        }
                        val keyName = try { key.last() } catch (_: NoSuchElementException) { continue }
                        if (keyName != SECURITY_SECRET_FILES_KEY) {
                            throw GradleException(
                                "Kenvy: [security] in '${sourceFile.name}' has an unknown field '$keyName'. " +
                                    "Supported fields: secret_files"
                            )
                        }
                        if (configuredSecretFiles != null) {
                            throw GradleException(
                                "Kenvy: [security].secret_files in '${sourceFile.name}' is declared more than once."
                            )
                        }
                        configuredSecretFiles = extractSecretFileArray(value, sourceFile)
                    }
                }
                walkNodes(node.children)
            }
        }

        walkNodes(doc.children)
        val files = linkedSetOf(DEFAULT_SECRET_FILE)
        configuredSecretFiles?.forEach { files.add(it) }
        return KenvySecuritySettings(secretFiles = files.toList())
    }

    private fun extractSecretFileArray(value: TomlValue, sourceFile: File): List<String> {
        if (value !is TomlArray) {
            throw GradleException(
                "Kenvy: [security].secret_files in '${sourceFile.name}' must be an array of quoted strings."
            )
        }
        @Suppress("UNCHECKED_CAST")
        return (value.content as List<Any>).mapIndexed { index, item ->
            val rawPath = when (item) {
                is TomlBasicString -> item.content.toString()
                is TomlLiteralString -> item.content.toString()
                else -> throw GradleException(
                    "Kenvy: [security].secret_files[$index] in '${sourceFile.name}' has a non-string value " +
                        "(got ${item::class.simpleName}). Use quoted project-relative paths."
                )
            }
            normalizeSecretFilePath(rawPath, sourceFile, index)
        }
    }

    private fun normalizeSecretFilePath(rawPath: String, sourceFile: File, index: Int): String {
        val normalized = rawPath.trim().replace('\\', '/').trim('/')
        if (normalized.isBlank()) {
            throw GradleException(
                "Kenvy: [security].secret_files[$index] in '${sourceFile.name}' must not be blank."
            )
        }
        if (File(rawPath).isAbsolute || rawPath.startsWith("/") || rawPath.startsWith("\\")) {
            throw GradleException(
                "Kenvy: [security].secret_files[$index] in '${sourceFile.name}' must be a project-relative path."
            )
        }
        if (normalized.split('/').any { it == ".." }) {
            throw GradleException(
                "Kenvy: [security].secret_files[$index] in '${sourceFile.name}' must not contain '..' traversal."
            )
        }
        return normalized
    }

    private fun buildMissingContractMessage(
        file: File,
        gradleProjectPath: String?,
        gradleProjectDir: File?
    ): String = buildString {
        appendLine("Kenvy: kenvy.toml not found.")
        appendLine("Expected config file: ${file.absolutePath}")
        if (gradleProjectPath != null) {
            appendLine("Gradle project: $gradleProjectPath")
        }
        if (gradleProjectDir != null) {
            appendLine("Project directory: ${gradleProjectDir.absolutePath}")
        }
        val hasContext = gradleProjectPath != null || gradleProjectDir != null
        if (hasContext) {
            appendLine("By default, Kenvy resolves kenvy.toml relative to the Gradle project/module where io.github.adriandleon.kenvy is applied.")
            append("If your contract lives elsewhere, configure kenvy { configFile.set(...) }.")
        } else {
            append("Create a kenvy.toml file in your project root to define configuration properties.")
        }
    }

    private data class ExtractedOverrides(
        val common: Map<String, String>,
        val platform: Map<String, Map<String, String>>,
        val variant: Map<String, Map<String, Map<String, String>>>
    )
}
