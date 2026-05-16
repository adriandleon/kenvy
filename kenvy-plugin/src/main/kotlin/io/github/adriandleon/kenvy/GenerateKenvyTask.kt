package io.github.adriandleon.kenvy

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File
import java.util.Locale

@CacheableTask
abstract class GenerateKenvyTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configFile: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val interfaceName: Property<String>

    @get:Input
    @get:Optional
    abstract val platform: Property<String>

    @get:Input
    @get:Optional
    abstract val variant: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localPropertiesFiles: ConfigurableFileCollection

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /** Gradle project path (e.g. `:shared`) for consumer-facing diagnostics. */
    @get:Internal
    abstract val gradleProjectPath: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val gitignoreFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rootGitignoreFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val secretFiles: ConfigurableFileCollection

    @get:Input
    abstract val environmentValues: MapProperty<String, String>

    @get:Input
    abstract val legacyUnprefixedEnvironmentOverrides: Property<Boolean>

    @get:Input
    abstract val cacheGeneratedOutput: Property<Boolean>

    @get:Input
    abstract val generatedPropertyNameStyle: Property<String>

    @get:Input
    abstract val declarationMode: Property<String>

    init {
        environmentValues.convention(emptyMap())
        legacyUnprefixedEnvironmentOverrides.convention(false)
        cacheGeneratedOutput.convention(false)
        generatedPropertyNameStyle.convention(GeneratedPropertyNameStyle.LOWER_CAMEL.value) // standalone default; plugin wiring overrides via set()
        platform.convention("")
        variant.convention("")
        declarationMode.convention(GeneratedDeclarationMode.OBJECT.value)
        projectDirectory.convention(project.layout.projectDirectory)
        rootDirectory.convention(project.layout.projectDirectory)
        gradleProjectPath.convention(project.path)
        gitignoreFiles.from(project.provider { File(projectDirectory.get().asFile, ".gitignore") })
        rootGitignoreFiles.from(project.provider { File(rootDirectory.get().asFile, ".gitignore") })
        secretFiles.from(project.provider {
            configuredSecretFiles(
                configFile = configFile.orNull?.asFile,
                projectDir = projectDirectory.get().asFile
            )
        })
        outputs.doNotCacheIf("generated Kenvy output may contain local or environment secrets") {
            !cacheGeneratedOutput.get()
        }
    }

    @TaskAction
    fun generate() {
        val config = configFile.get().asFile
        val pkg = packageName.get()
        val name = interfaceName.get()
        val output = outputDir.get().asFile
        val targetPlatform = platform.orNull
        val targetVariant = variant.orNull
        val mode = GeneratedDeclarationMode.fromValue(declarationMode.get())
        val propertyNameStyle = GeneratedPropertyNameStyle.fromValue(generatedPropertyNameStyle.get())

        output.deleteRecursively()
        output.mkdirs()

        val contract = KenvyParser.parseContract(
            file = config,
            gradleProjectPath = gradleProjectPath.orNull,
            gradleProjectDir = projectDirectory.get().asFile
        )
        warnAboutMissingGitIgnoreEntries(
            logger = logger,
            projectDir = projectDirectory.get().asFile,
            gitignoreFile = gitignoreFiles.singleFile,
            contract = contract
        )
        warnAboutMissingRootLocalPropertiesGitIgnoreEntry(
            logger = logger,
            projectDir = projectDirectory.get().asFile,
            rootDir = rootDirectory.get().asFile,
            rootGitignoreFile = rootGitignoreFiles.singleFile,
            localPropertiesFiles = localPropertiesFiles.files
        )
        if (contract.properties.isEmpty()) {
            throw GradleException(
                "Kenvy: No properties defined in kenvy.toml. " +
                "Add at least one [properties.name] section."
            )
        }

        validatePackageName(pkg)
        validateObjectName(name)

        val localPropertiesFileList = localPropertiesFiles.files.toList()
        val localProps = KenvyResolver.loadAndMergeLocalProperties(localPropertiesFileList)
        val envMap = environmentValues.get()
        val resolved = KenvyResolver.resolve(
            contract = contract,
            platform = targetPlatform,
            variant = targetVariant,
            localProperties = localProps,
            legacyUnprefixedEnvironmentOverrides = legacyUnprefixedEnvironmentOverrides.get()
        ) { envMap[it] }

        val checkedLocalFiles = buildCheckedLocalFileInfo(
            localPropertiesFileList = localPropertiesFileList,
            projectDir = projectDirectory.get().asFile,
            rootDir = rootDirectory.get().asFile,
            gradleProjectPath = gradleProjectPath.orNull
        )

        val packagePath = pkg.replace('.', '/')
        val packageDir = File(output, packagePath)
        packageDir.mkdirs()

        val objectFile = File(packageDir, "${name}.kt")
        objectFile.writeText(
            buildGeneratedSource(
                pkg = pkg,
                name = name,
                resolvedValues = resolved,
                mode = mode,
                propertyNameStyle = propertyNameStyle,
                platform = targetPlatform,
                variant = targetVariant,
                checkedLocalFiles = checkedLocalFiles
            )
        )
    }
}

internal fun buildGeneratedSource(
    pkg: String,
    name: String,
    resolvedValues: List<ResolvedKenvyValue>,
    mode: GeneratedDeclarationMode = GeneratedDeclarationMode.OBJECT,
    propertyNameStyle: GeneratedPropertyNameStyle = GeneratedPropertyNameStyle.LOWER_CAMEL,
    platform: String? = null,
    variant: String? = null,
    checkedLocalFiles: List<LocalPropertiesFileInfo> = emptyList()
): String {
    if (mode.requiresInitializer) {
        val issues = collectConfigurationIssues(
            resolvedValues = resolvedValues,
            platform = platform,
            variant = variant,
            checkedLocalFiles = checkedLocalFiles
        )
        if (issues.isNotEmpty()) {
            throw GradleException(KenvyErrorReportFormatter.format(issues))
        }
    }

    // Pre-scan: collect all generated names and report every collision before writing anything.
    val nameToContracts = linkedMapOf<String, MutableList<String>>()
    for (resolved in resolvedValues) {
        val sanitizedName = resolved.property.toKotlinPropertyName(propertyNameStyle)
        nameToContracts.getOrPut(sanitizedName) { mutableListOf() } += resolved.property.name
    }
    val collisions = nameToContracts.entries.filter { it.value.size > 1 }
    if (collisions.isNotEmpty()) {
        val details = collisions.joinToString("; ") { (generatedName, contractNames) ->
            "generated Kotlin property '$generatedName' from contract properties: ${contractNames.joinToString(", ")}"
        }
        throw GradleException(
            "Kenvy: Multiple properties map to the same $details. " +
            "Rename one of the conflicting kenvy.toml properties or use the compatibility naming style if configured."
        )
    }

    return buildString {
        if (mode.isExpectActualClassifier) {
            appendLine("@file:Suppress(\"EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING\")")
            appendLine()
        }
        appendLine("// Auto-generated by Kenvy. Do not edit manually.")
        appendLine("package $pkg")
        appendLine()
        appendLine("${mode.objectKeyword} $name {")
        for (resolved in resolvedValues) {
            val prop = resolved.property
            val sanitizedName = prop.toKotlinPropertyName(propertyNameStyle)
            if (prop.description != null) {
                val sanitizedDoc = prop.description.replace("*/", "* /")
                appendLine("    /** $sanitizedDoc */")
            }
            val (kotlinType, kotlinLiteral) = prop.toKotlinTypeAndLiteral(resolved.resolvedValue)
            val prefix = mode.memberKeyword
            val initializer = if (mode.requiresInitializer) " = $kotlinLiteral" else ""
            appendLine("    $prefix $sanitizedName: $kotlinType$initializer")
        }
        appendLine("}")
    }
}

internal fun collectConfigurationIssues(
    resolvedValues: List<ResolvedKenvyValue>,
    platform: String? = null,
    variant: String? = null,
    checkedLocalFiles: List<LocalPropertiesFileInfo> = emptyList()
): List<KenvyConfigurationIssue> {
    val issues = mutableListOf<KenvyConfigurationIssue>()

    resolvedValues
        .filter { it.isMissingPlaceholderValue() }
        .mapTo(issues) { resolvedValue ->
            KenvyConfigurationIssue.missingValue(
                resolvedValue = resolvedValue,
                diffSnippet = "+ ${resolvedValue.property.localPropertiesSuggestionKey(platform, variant)}=<YOUR_VALUE>",
                checkedLocalFiles = checkedLocalFiles
            )
        }

    resolvedValues
        .mapNotNull { resolvedValue ->
            if (resolvedValue.isMissingPlaceholderValue()) {
                return@mapNotNull null
            }
            val expectedType = resolvedValue.property.expectedTypeForMismatch(resolvedValue.resolvedValue)
                ?: return@mapNotNull null
            KenvyConfigurationIssue.typeMismatch(resolvedValue, expectedType)
        }
        .mapTo(issues) { it }

    return issues
}

private fun ResolvedKenvyValue.isMissingPlaceholderValue(): Boolean =
    property.isPlaceholder && PlaceholderDetector.isPlaceholderValue(resolvedValue)

private fun KenvyProperty.localPropertiesSuggestionKey(platform: String?, variant: String?): String =
    buildString {
        append(name)
        platform.trimmedOrNull()?.let { normalizedPlatform ->
            append(".")
            append(normalizedPlatform)
            variant.trimmedOrNull()?.let { normalizedVariant ->
                append(".")
                append(normalizedVariant)
            }
        }
    }

private fun String?.trimmedOrNull(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }

internal enum class GeneratedDeclarationMode(val value: String, val objectKeyword: String, val memberKeyword: String, val requiresInitializer: Boolean) {
    OBJECT("object", "object", "val", true),
    EXPECT_OBJECT("expect-object", "expect object", "val", false),
    ACTUAL_OBJECT("actual-object", "actual object", "actual val", true);

    val isExpectActualClassifier: Boolean
        get() = this == EXPECT_OBJECT || this == ACTUAL_OBJECT

    companion object {
        fun fromValue(value: String): GeneratedDeclarationMode =
            values().firstOrNull { it.value == value }
                ?: throw GradleException("Kenvy: Unsupported declarationMode '$value'.")
    }
}

internal enum class GeneratedPropertyNameStyle(val value: String) {
    LOWER_CAMEL("lower-camel"),
    PRESERVE("preserve");

    companion object {
        fun fromValue(value: String): GeneratedPropertyNameStyle =
            values().firstOrNull { it.value == value }
                ?: throw GradleException(
                    "Kenvy: Unsupported generatedPropertyNameStyle '$value'. " +
                    "Use '${LOWER_CAMEL.value}' or '${PRESERVE.value}'."
                )
    }
}

private fun KenvyProperty.toKotlinTypeAndLiteral(resolvedValue: String?): Pair<String, String> {
    return when (type) {
        PropertyType.STRING -> {
            val value = (resolvedValue ?: "")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("$", "\\$")
            "String" to "\"$value\""
        }
        PropertyType.INT -> {
            val value = resolvedValue?.toIntOrNull() ?: 0
            "Int" to "$value"
        }
        PropertyType.BOOLEAN -> {
            val normalized = resolvedValue?.lowercase()
            val value = when (normalized) {
                "true" -> true
                "false" -> false
                null -> false
                else -> false
            }
            "Boolean" to "$value"
        }
        PropertyType.LONG -> {
            val value = resolvedValue?.toLongOrNull() ?: 0L
            "Long" to "${value}L"
        }
    }
}

private fun KenvyProperty.expectedTypeForMismatch(resolvedValue: String?): String? =
    when (type) {
        PropertyType.STRING -> null
        PropertyType.INT -> if (resolvedValue == null || resolvedValue.toIntOrNull() != null) null else "Int"
        PropertyType.BOOLEAN -> when (resolvedValue?.lowercase()) {
            "true", "false", null -> null
            else -> "Boolean"
        }
        PropertyType.LONG -> if (resolvedValue == null || resolvedValue.toLongOrNull() != null) null else "Long"
    }

private val kotlinKeywords = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
    "if", "in", "interface", "is", "null", "object", "package", "return",
    "super", "this", "throw", "true", "try", "typealias", "typeof", "val",
    "var", "when", "while"
)

private val kotlinIdentifierRegex = Regex("[A-Za-z_]\\w*")
private val kotlinPackageRegex = Regex("[A-Za-z_]\\w*(\\.[A-Za-z_]\\w*)*")

private fun validatePackageName(pkg: String) {
    if (!kotlinPackageRegex.matches(pkg) || pkg.split('.').any { it in kotlinKeywords }) {
        throw GradleException(
            "Kenvy: packageName '$pkg' is not a valid Kotlin package name. " +
            "Configure kenvy { packageName.set(\"com.example\") }."
        )
    }
}

private fun validateObjectName(name: String) {
    if (!kotlinIdentifierRegex.matches(name) || name in kotlinKeywords) {
        throw GradleException(
            "Kenvy: interfaceName '$name' is not a valid Kotlin object name. " +
            "Use a simple Kotlin identifier such as 'Kenvy' or 'AppConfig'."
        )
    }
}

private fun KenvyProperty.toKotlinPropertyName(style: GeneratedPropertyNameStyle): String {
    val baseName = when (style) {
        GeneratedPropertyNameStyle.LOWER_CAMEL -> name.toLowerCamelGeneratedName()
        GeneratedPropertyNameStyle.PRESERVE -> name
            .trim()
            .replace(Regex("[^A-Za-z0-9_]"), "_")
    }

    val sanitized = baseName
        .let { if (it.firstOrNull()?.isDigit() == true) "_$it" else it }
        .let { s ->
            // Also catch all-caps variants (e.g. VAL -> vAL) that lower-case to a keyword.
            val lower = s.lowercase(Locale.ROOT)
            if (s in kotlinKeywords || lower in kotlinKeywords) "${lower}_" else s
        }

    if (!kotlinIdentifierRegex.matches(sanitized)) {
        throw GradleException(
            "Kenvy: Property '$name' cannot be mapped to a valid Kotlin property name."
        )
    }

    return sanitized
}

/** File info entry for consumer-facing diagnostics — not printed as values, only paths. */
internal data class LocalPropertiesFileInfo(
    val label: String,
    val absolutePath: String,
    val exists: Boolean
)

internal fun buildCheckedLocalFileInfo(
    localPropertiesFileList: List<File>,
    projectDir: File,
    rootDir: File,
    gradleProjectPath: String?
): List<LocalPropertiesFileInfo> {
    val rootCanonical = runCatching { rootDir.canonicalFile }.getOrDefault(rootDir.absoluteFile)
    val projectCanonical = runCatching { projectDir.canonicalFile }.getOrDefault(projectDir.absoluteFile)
    val isModule = rootCanonical != projectCanonical

    val rootLocalPropsCanonical = File(rootCanonical, "local.properties").let {
        runCatching { it.canonicalFile }.getOrDefault(it.absoluteFile)
    }
    val moduleLocalPropsCanonical = File(projectCanonical, "local.properties").let {
        runCatching { it.canonicalFile }.getOrDefault(it.absoluteFile)
    }

    return localPropertiesFileList.map { file ->
        val fileCanonical = runCatching { file.canonicalFile }.getOrDefault(file.absoluteFile)
        val label = when {
            fileCanonical == rootLocalPropsCanonical -> "root"
            fileCanonical == moduleLocalPropsCanonical && isModule -> "module ${gradleProjectPath ?: projectCanonical.name}"
            else -> "custom"
        }
        LocalPropertiesFileInfo(
            label = label,
            absolutePath = fileCanonical.absolutePath,
            exists = file.exists()
        )
    }
}

private fun String.toLowerCamelGeneratedName(): String {
    val trimmed = trim()
    val segments = trimmed.split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }

    return if (segments.size > 1 || trimmed.any { !it.isLetterOrDigit() }) {
        segments.mapIndexed { index, segment ->
            val lower = segment.lowercase(Locale.ROOT)
            if (index == 0) lower else lower.replaceFirstChar { char -> char.titlecase(Locale.ROOT) }
        }.joinToString(separator = "")
    } else {
        trimmed.replaceFirstChar { char -> char.lowercase(Locale.ROOT) }
    }
}
