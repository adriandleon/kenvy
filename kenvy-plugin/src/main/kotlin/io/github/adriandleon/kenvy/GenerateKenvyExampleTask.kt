package io.github.adriandleon.kenvy

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

@CacheableTask
abstract class GenerateKenvyExampleTask : DefaultTask() {

    /** Gradle project path (e.g. `:shared`) for consumer-facing diagnostics. */
    @get:Internal
    abstract val gradleProjectPath: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val platform: Property<String>

    @get:Input
    @get:Optional
    abstract val variant: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val gitignoreFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val secretFiles: ConfigurableFileCollection

    init {
        platform.convention("")
        variant.convention("")
        gradleProjectPath.convention(project.path)
        projectDirectory.convention(project.layout.projectDirectory)
        gitignoreFiles.from(project.provider { File(projectDirectory.get().asFile, ".gitignore") })
        secretFiles.from(project.provider {
            configuredSecretFiles(
                configFile = configFile.orNull?.asFile,
                projectDir = projectDirectory.get().asFile
            )
        })
    }

    @TaskAction
    fun generate() {
        val contract = KenvyParser.parseContract(
            file = configFile.get().asFile,
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

        val content = buildLocalPropertiesExample(contract, targetPlatform, targetVariant)

        val output = outputFile.get().asFile
        output.parentFile?.mkdirs()
        output.writeText(content)
    }
}
