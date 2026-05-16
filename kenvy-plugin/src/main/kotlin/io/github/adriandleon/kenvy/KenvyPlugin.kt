package io.github.adriandleon.kenvy

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.create

class KenvyPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // 1. Register the extension for plugin configuration DSL (kenvy { ... })
        val extension = target.extensions.create<KenvyExtension>("kenvy")

        // 2. Default config file location: kenvy.toml in project root
        extension.configFile.convention(
            target.layout.projectDirectory.file("kenvy.toml")
        )

        // 2b. Default local properties: root first, then module (deduplicated for root-applied builds).
        // Use target.rootDir (a plain File, not cross-project layout access) to stay isolated-projects safe.
        val rootLocalPropsFile = java.io.File(target.rootDir, "local.properties")
        val moduleLocalPropsFile = target.layout.projectDirectory.file("local.properties")
        if (target.rootDir == target.projectDir) {
            extension.localPropertiesFiles.from(rootLocalPropsFile)
        } else {
            extension.localPropertiesFiles.from(rootLocalPropsFile, moduleLocalPropsFile)
        }

        // 3. Extension defaults
        extension.interfaceName.convention("Kenvy")
        extension.packageName.convention(target.providers.provider { target.group.toString() })
        extension.cacheGeneratedOutput.convention(false)
        extension.legacyUnprefixedEnvironmentOverrides.convention(false)
        extension.generatedPropertyNameStyle.convention(GeneratedPropertyNameStyle.LOWER_CAMEL.value)
        extension.platform.convention("")
        extension.variant.convention("")

        // 4. Register merge task
        val localPropertiesFileRef = target.layout.projectDirectory.file("local.properties")
        val mergeTask = target.tasks.register("mergeKenvy", MergeKenvyTask::class.java)
        mergeTask.configure {
            group = "kenvy"
            description = "Merges contract defaults into local.properties without overwriting existing local values"

            configFile.set(extension.configFile)
            platform.set(extension.platform)
            variant.set(extension.variant)
            localPropertiesFile.set(localPropertiesFileRef)
            outputLocalPropertiesFile.set(localPropertiesFileRef)
            projectDirectory.set(target.layout.projectDirectory)
            gradleProjectPath.set(target.path)
        }

        // 5a. Register example generation task
        val exampleOutputFile = target.layout.projectDirectory.file("local.properties.example")
        val generateExampleTask = target.tasks.register("generateKenvyExample", GenerateKenvyExampleTask::class.java)
        generateExampleTask.configure {
            group = "kenvy"
            description = "Generates local.properties.example from kenvy.toml contract for developer onboarding"

            configFile.set(extension.configFile)
            platform.set(extension.platform)
            variant.set(extension.variant)
            outputFile.set(exampleOutputFile)
            projectDirectory.set(target.layout.projectDirectory)
            gradleProjectPath.set(target.path)
        }

        // 5b. Register code generation task
        val generateTask = target.registerGenerateKenvyTask(
            taskName = "generateKenvy",
            description = "Generates type-safe Kotlin configuration from kenvy.toml",
            extension = extension,
            platformProvider = extension.platform,
            variantProvider = extension.variant,
            outputDirPath = "generated/kenvy/commonMain/kotlin",
            declarationModeProvider = target.providers.provider { GeneratedDeclarationMode.OBJECT.value }
        )

        // 5c. generateKenvy also produces the example file
        generateTask.configure {
            dependsOn(generateExampleTask)
        }

        // 6. Wire generated output to KMP commonMain source set (when KMP plugin is present)
        target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            target.configureKmpGeneratedSource("commonMain", generateTask.flatMap { it.outputDir })
            target.afterEvaluate {
                val productionTargets = target.kmpProductionTargets()
                val hasAndroid = target.hasKmpSourceSet("androidMain")
                val iosTargets = productionTargets.filter { it.isIos }
                val hasIos = iosTargets.isNotEmpty()
                if (!hasAndroid && !hasIos) return@afterEvaluate

                generateTask.configure {
                    declarationMode.set(GeneratedDeclarationMode.EXPECT_OBJECT.value)
                }

                // Register actuals for non-iOS production targets (Android, JVM, etc.)
                productionTargets
                    .filterNot { it.sourceSetName == "commonMain" }
                    .filterNot { it.isIos }
                    .map { it.sourceSetName }
                    .plus("androidMain")
                    .distinct()
                    .filter { target.hasKmpSourceSet(it) }
                    .forEach { sourceSetName ->
                        val targetName = sourceSetName.removeSuffix("Main")
                        val actualGenerateTask = target.registerGenerateKenvyTask(
                            taskName = "generateKenvy${targetName.toTaskNameSuffix()}",
                            description = "Generates ${targetName}-specific Kotlin configuration from kenvy.toml",
                            extension = extension,
                            platformProvider = target.providers.provider { targetName },
                            variantProvider = target.variantProviderFor(targetName, extension),
                            outputDirPath = "generated/kenvy/$sourceSetName/kotlin",
                            declarationModeProvider = target.providers.provider { GeneratedDeclarationMode.ACTUAL_OBJECT.value }
                        )
                        target.configureKmpGeneratedSource(sourceSetName, actualGenerateTask.flatMap { it.outputDir })
                    }

                // Register iOS actuals using the canonical Kenvy platform ("ios") regardless of
                // architecture target names.
                if (hasIos) {
                    if (target.hasKmpSourceSet("iosMain")) {
                        val iosActualTask = target.registerGenerateKenvyTask(
                            taskName = "generateKenvyIos",
                            description = "Generates iOS-specific Kotlin configuration from kenvy.toml",
                            extension = extension,
                            platformProvider = target.providers.provider { "ios" },
                            variantProvider = extension.variant,
                            outputDirPath = "generated/kenvy/iosMain/kotlin",
                            declarationModeProvider = target.providers.provider { GeneratedDeclarationMode.ACTUAL_OBJECT.value }
                        )
                        target.configureKmpGeneratedSource("iosMain", iosActualTask.flatMap { it.outputDir })
                    } else {
                        iosTargets
                            .map { it.sourceSetName }
                            .distinct()
                            .filter { target.hasKmpSourceSet(it) }
                            .forEach { sourceSetName ->
                                val targetName = sourceSetName.removeSuffix("Main")
                                val iosActualTask = target.registerGenerateKenvyTask(
                                    taskName = "generateKenvy${targetName.toTaskNameSuffix()}",
                                    description = "Generates ${targetName}-specific iOS Kotlin configuration from kenvy.toml",
                                    extension = extension,
                                    platformProvider = target.providers.provider { "ios" },
                                    variantProvider = extension.variant,
                                    outputDirPath = "generated/kenvy/$sourceSetName/kotlin",
                                    declarationModeProvider = target.providers.provider { GeneratedDeclarationMode.ACTUAL_OBJECT.value }
                                )
                                target.configureKmpGeneratedSource(sourceSetName, iosActualTask.flatMap { it.outputDir })
                            }
                    }
                }
            }
        }

    }
}

private fun Project.registerGenerateKenvyTask(
    taskName: String,
    description: String,
    extension: KenvyExtension,
    platformProvider: Provider<String>,
    variantProvider: Provider<String>,
    outputDirPath: String,
    declarationModeProvider: Provider<String>
): TaskProvider<GenerateKenvyTask> =
    tasks.register(taskName, GenerateKenvyTask::class.java).apply {
        configure {
            group = "kenvy"
            this.description = description

            configFile.set(extension.configFile)
            packageName.set(extension.packageName)
            interfaceName.set(extension.interfaceName)
            platform.set(platformProvider)
            variant.set(variantProvider)
            outputDir.set(layout.buildDirectory.dir(outputDirPath))
            cacheGeneratedOutput.set(extension.cacheGeneratedOutput)
            legacyUnprefixedEnvironmentOverrides.set(extension.legacyUnprefixedEnvironmentOverrides)
            generatedPropertyNameStyle.set(extension.generatedPropertyNameStyle)
            declarationMode.set(declarationModeProvider)

            localPropertiesFiles.from(extension.localPropertiesFiles)
            projectDirectory.set(layout.projectDirectory)
            rootDirectory.set(layout.dir(providers.provider { rootDir }))
            gradleProjectPath.set(path)
            environmentValues.set(
                buildEnvironmentValuesProvider(
                    extension = extension,
                    legacyUnprefixedEnvironmentOverrides = legacyUnprefixedEnvironmentOverrides,
                    projectPath = path,
                    projectDir = projectDir
                )
            )
        }
    }

private fun Project.buildEnvironmentValuesProvider(
    extension: KenvyExtension,
    legacyUnprefixedEnvironmentOverrides: Provider<Boolean>,
    projectPath: String? = null,
    projectDir: java.io.File? = null
): Provider<Map<String, String>> =
    providers.provider {
        val contract = KenvyParser.parseContract(
            file = extension.configFile.get().asFile,
            gradleProjectPath = projectPath,
            gradleProjectDir = projectDir
        )
        KenvyResolver.validateEnvironmentNameCollisions(contract.properties)
        val legacyOptIn = legacyUnprefixedEnvironmentOverrides.getOrElse(false)
        val environmentNames = buildSet {
            contract.properties.forEach { prop ->
                add(KenvyResolver.toEnvVarName(prop.name))
                val legacyEnvName = KenvyResolver.toLegacyEnvVarName(prop.name)
                if (legacyOptIn || legacyEnvName in KNOWN_UNSAFE_LEGACY_ENVIRONMENT_NAMES) {
                    add(legacyEnvName)
                }
            }
        }

        environmentNames.mapNotNull { envName ->
            val value = providers.environmentVariable(envName).orNull?.takeIf { it.isNotBlank() }
            if (value == null) null else envName to value
        }.toMap()
    }

private fun Project.variantProviderFor(targetName: String, extension: KenvyExtension): Provider<String> =
    providers.provider {
        val configuredVariant = extension.variant.orNull?.takeIf { it.isNotBlank() }
        configuredVariant ?: if (targetName == "android") requestedAndroidVariantName().orEmpty() else ""
    }

private fun Project.requestedAndroidVariantName(): String? {
    val variants = gradle.startParameter.taskNames
        .mapNotNull { taskName ->
            val simpleName = taskName.substringAfterLast(':')
            compileAndroidKotlinTaskRegex.matchEntire(simpleName)
                ?.groupValues
                ?.get(1)
                ?: assembleAndroidTaskRegex.matchEntire(simpleName)
                    ?.groupValues
                    ?.get(1)
        }
        .map { it.replaceFirstChar { char -> char.lowercaseChar() } }
        .distinct()

    return variants.singleOrNull()
}

private val compileAndroidKotlinTaskRegex = Regex("compile(.+)KotlinAndroid")
private val assembleAndroidTaskRegex = Regex("assemble(.+)")

private fun Project.configureKmpGeneratedSource(sourceSetName: String, generatedSourceDir: Any) {
    val kmp = extensions.getByName("kotlin")
    val sourceSets = kmp.javaClass.getMethod("getSourceSets").invoke(kmp)
    val named = sourceSets.javaClass.getMethod("named", String::class.java)
    val sourceSetProvider = named.invoke(sourceSets, sourceSetName)
    val configure = sourceSetProvider.javaClass.methods.first {
        it.name == "configure" &&
            it.parameterTypes.size == 1 &&
            Action::class.java.isAssignableFrom(it.parameterTypes[0])
    }

    val addGeneratedSource = object : Action<Any> {
        override fun execute(sourceSet: Any) {
            val kotlinSources = sourceSet.javaClass.getMethod("getKotlin").invoke(sourceSet)
            kotlinSources.javaClass.getMethod("srcDir", Any::class.java).invoke(kotlinSources, generatedSourceDir)
        }
    }
    configure.invoke(sourceSetProvider, addGeneratedSource)
}

private fun Project.hasKmpSourceSet(sourceSetName: String): Boolean {
    val kmp = extensions.getByName("kotlin")
    val sourceSets = kmp.javaClass.getMethod("getSourceSets").invoke(kmp)
    val findByName = sourceSets.javaClass.getMethod("findByName", String::class.java)
    return findByName.invoke(sourceSets, sourceSetName) != null
}

private fun String.toTaskNameSuffix(): String =
    split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }
        .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }

@Suppress("UNCHECKED_CAST")
private fun Project.kmpProductionTargets(): List<KmpProductionTarget> {
    val kmp = extensions.getByName("kotlin")
    val targets = kmp.javaClass.getMethod("getTargets").invoke(kmp) as Iterable<Any>
    return targets.mapNotNull { t ->
        val compilations = t.javaClass.getMethod("getCompilations").invoke(t)
        val mainCompilation = compilations.javaClass.getMethod("findByName", String::class.java)
            .invoke(compilations, "main") ?: return@mapNotNull null
        val defaultSourceSet = mainCompilation.javaClass.getMethod("getDefaultSourceSet").invoke(mainCompilation)
        val sourceSetName = defaultSourceSet.javaClass.getMethod("getName").invoke(defaultSourceSet) as String
        KmpProductionTarget(
            sourceSetName = sourceSetName,
            isIos = t.isIosKotlinNativeTarget()
        )
    }
}

private data class KmpProductionTarget(
    val sourceSetName: String,
    val isIos: Boolean
)

private fun Any.isIosKotlinNativeTarget(): Boolean {
    val konanTarget = runCatching { javaClass.getMethod("getKonanTarget").invoke(this) }.getOrNull()
        ?: return false
    val family = runCatching { konanTarget.javaClass.getMethod("getFamily").invoke(konanTarget) }.getOrNull()
        ?: return false
    val familyName = runCatching { family.javaClass.getMethod("getName").invoke(family) as String }
        .getOrElse { family.toString() }
    return familyName.equals("IOS", ignoreCase = true)
}
