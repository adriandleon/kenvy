package io.github.adriandleon.kenvy

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import java.io.File

abstract class MergeKenvyTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configFile: RegularFileProperty

    /**
     * The local.properties file to read from and merge into.
     * Declared @Internal so it is not double-counted alongside outputLocalPropertiesFile;
     * Gradle tracks changes to the output file itself for UP-TO-DATE checking.
     */
    @get:Internal
    abstract val localPropertiesFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val platform: Property<String>

    @get:Input
    @get:Optional
    abstract val variant: Property<String>

    @get:Input
    abstract val forceKeys: SetProperty<String>

    /**
     * The file to write merged properties to — typically the same path as localPropertiesFile.
     * Declaring it as @OutputFile lets Gradle detect external modifications for UP-TO-DATE checks.
     */
    @get:OutputFile
    abstract val outputLocalPropertiesFile: RegularFileProperty

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    /** Gradle project path (e.g. `:shared`) for consumer-facing diagnostics. */
    @get:Internal
    abstract val gradleProjectPath: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val gitignoreFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val secretFiles: ConfigurableFileCollection

    init {
        forceKeys.convention(emptySet())
        platform.convention("")
        variant.convention("")
        gradleProjectPath.convention(project.path)
        projectDirectory.convention(project.layout.projectDirectory)
        gitignoreFiles.from(project.provider { File(projectDirectory.get().asFile, ".gitignore") })
        secretFiles.from(project.provider {
            configuredSecretFiles(
                configFile = configFile.orNull?.asFile,
                projectDir = projectDirectory.get().asFile,
                excludedFiles = setOfNotNull(outputLocalPropertiesFile.orNull?.asFile)
            )
        })
    }

    @TaskAction
    fun merge() {
        val contractFile = configFile.get().asFile
        val localFile = localPropertiesFile.asFile.orNull?.takeIf { it.exists() }
        val existingProps = if (localFile != null) {
            KenvyResolver.loadLocalProperties(localFile)
        } else {
            emptyMap()
        }

        val contract = KenvyParser.parseContract(
            file = contractFile,
            gradleProjectPath = gradleProjectPath.orNull,
            gradleProjectDir = projectDirectory.get().asFile
        )
        warnAboutMissingGitIgnoreEntries(
            logger = logger,
            projectDir = projectDirectory.get().asFile,
            gitignoreFile = gitignoreFiles.singleFile,
            contract = contract
        )
        val targetPlatform = platform.orNull?.takeIf { it.isNotBlank() }
        val targetVariant = variant.orNull?.takeIf { it.isNotBlank() }
        val keys = forceKeys.get()

        val result = KenvyMerger.mergeLocalProperties(
            contract = contract,
            platform = targetPlatform,
            variant = targetVariant,
            existingLocalProperties = existingProps,
            forceKeys = keys
        )

        if (result.overwrittenKeys.isNotEmpty()) {
            val names = result.overwrittenKeys.sorted().joinToString(", ") { key ->
                val source = result.overwrittenSources[key]?.name?.lowercase() ?: "contract"
                "$key (source: $source)"
            }
            logger.warn("Kenvy: Force-overwriting local values for properties: $names")
        }

        val outputFile = outputLocalPropertiesFile.get().asFile
        writePropertiesPreservingComments(outputFile, result.mergedProperties, localFile)
    }

    private fun writePropertiesPreservingComments(
        outputFile: File,
        mergedProperties: Map<String, String>,
        sourceFile: File?
    ) {
        outputFile.parentFile?.mkdirs()

        if (sourceFile == null || !sourceFile.exists()) {
            val lines = mergedProperties.entries.map { (k, v) ->
                "${KenvyPropertiesFormat.escapePropertyKey(k)}=${KenvyPropertiesFormat.escapePropertyValue(v)}"
            }
            outputFile.writeText(lines.joinToString("\n") + if (lines.isNotEmpty()) "\n" else "")
            return
        }

        val lines = sourceFile.readLines()
        val remaining = mergedProperties.toMutableMap()
        val outputLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith('#') || trimmed.startsWith('!') || trimmed.isEmpty()) {
                outputLines.add(line)
                continue
            }
            val sepIdx = findSeparatorIndex(trimmed)
            if (sepIdx < 0) {
                outputLines.add(line)
                continue
            }
            val key = unescapePropertyToken(trimmed.substring(0, sepIdx).trim())
            if (remaining.containsKey(key)) {
                outputLines.add(
                    "${KenvyPropertiesFormat.escapePropertyKey(key)}=" +
                        KenvyPropertiesFormat.escapePropertyValue(remaining.remove(key).orEmpty())
                )
            }
            // Keys absent from remaining were removed from the contract — skip them.
        }

        for ((k, v) in remaining) {
            outputLines.add("${KenvyPropertiesFormat.escapePropertyKey(k)}=${KenvyPropertiesFormat.escapePropertyValue(v)}")
        }

        outputFile.writeText(outputLines.joinToString("\n") + "\n")
    }

    private fun findSeparatorIndex(line: String): Int {
        var escaped = false
        for (index in line.indices) {
            val char = line[index]
            if (escaped) {
                escaped = false
                continue
            }
            when (char) {
                '\\' -> escaped = true
                '=', ':' -> return index
                ' ', '\t', '\u000C' -> return index
            }
        }
        return -1
    }

    private fun unescapePropertyToken(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '\\' || index == value.lastIndex) {
                append(char)
                index++
                continue
            }
            val escaped = value[++index]
            append(
                when (escaped) {
                    't' -> '\t'
                    'n' -> '\n'
                    'r' -> '\r'
                    'f' -> '\u000C'
                    else -> escaped
                }
            )
            index++
        }
    }
}
