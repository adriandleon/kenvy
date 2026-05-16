package io.github.adriandleon.kenvy

import org.gradle.api.logging.Logger
import java.io.File

internal fun warnAboutMissingGitIgnoreEntries(
    logger: Logger,
    projectDir: File,
    gitignoreFile: File,
    contract: ParsedKenvyContract
) {
    val gitIgnoreResult = KenvyGitIgnoreVerifier.verify(
        projectDir = projectDir,
        secretFilePaths = contract.security.secretFiles,
        gitignoreFile = gitignoreFile
    )
    if (gitIgnoreResult.missingExclusions.isNotEmpty()) {
        logger.warn(buildGitIgnoreWarning(gitIgnoreResult.missingExclusions))
    }
}

internal fun warnAboutMissingRootLocalPropertiesGitIgnoreEntry(
    logger: Logger,
    projectDir: File,
    rootDir: File,
    rootGitignoreFile: File,
    localPropertiesFiles: Set<File>
) {
    if (sameFile(projectDir, rootDir)) return

    val rootLocalProperties = File(rootDir, "local.properties")
    if (!rootLocalProperties.exists()) return

    val rootLocalPropertiesIsConfigured = localPropertiesFiles.any { file -> sameFile(file, rootLocalProperties) }
    if (!rootLocalPropertiesIsConfigured) return

    val gitIgnoreResult = KenvyGitIgnoreVerifier.verify(
        projectDir = rootDir,
        secretFilePaths = listOf("local.properties"),
        gitignoreFile = rootGitignoreFile
    )
    if (gitIgnoreResult.missingExclusions.isNotEmpty()) {
        logger.warn(buildRootGitIgnoreWarning(rootLocalProperties, gitIgnoreResult.missingExclusions))
    }
}

internal fun buildRootGitIgnoreWarning(
    rootLocalProperties: File,
    missingExclusions: List<KenvyMissingGitIgnoreExclusion>
): String {
    if (missingExclusions.size == 1) {
        val missing = missingExclusions.single()
        return "Kenvy: root ${missing.path} (${rootLocalProperties.absolutePath}) is not in .gitignore - secrets may be committed to version control " +
            "(suggested entry: ${missing.suggestedEntry})"
    }
    return buildString {
        appendLine("Kenvy: Some root local secret files are not in .gitignore - secrets may be committed to version control:")
        missingExclusions.forEach { missing ->
            appendLine("  - root ${missing.path} (${rootLocalProperties.absolutePath}) (suggested entry: ${missing.suggestedEntry})")
        }
    }
}

internal fun buildGitIgnoreWarning(missingExclusions: List<KenvyMissingGitIgnoreExclusion>): String {
    if (missingExclusions.size == 1) {
        val missing = missingExclusions.single()
        return "Kenvy: ${missing.path} is not in .gitignore - secrets may be committed to version control " +
            "(suggested entry: ${missing.suggestedEntry})"
    }

    return buildString {
        appendLine("Kenvy: Some local secret files are not in .gitignore - secrets may be committed to version control:")
        missingExclusions.forEach { missing ->
            appendLine("  - ${missing.path} (suggested entry: ${missing.suggestedEntry})")
        }
    }
}

private fun sameFile(left: File, right: File): Boolean {
    val leftCanonical = runCatching { left.canonicalFile }.getOrDefault(left.absoluteFile)
    val rightCanonical = runCatching { right.canonicalFile }.getOrDefault(right.absoluteFile)
    return leftCanonical == rightCanonical
}

internal fun configuredSecretFiles(
    configFile: File?,
    projectDir: File,
    excludedFiles: Set<File> = emptySet()
): List<File> {
    if (configFile == null) return emptyList()
    if (!configFile.exists()) return emptyList()

    val excludedCanonicalFiles = excludedFiles.mapNotNullTo(mutableSetOf()) { file ->
        runCatching { file.canonicalFile }.getOrNull()
    }
    return KenvyParser.parseContract(configFile)
        .security
        .secretFiles
        .map { path -> File(projectDir, path) }
        .filterNot { file ->
            val canonicalFile = runCatching { file.canonicalFile }.getOrNull()
            canonicalFile != null && canonicalFile in excludedCanonicalFiles
        }
}
