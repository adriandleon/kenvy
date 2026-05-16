package io.github.adriandleon.kenvy

import java.io.File

data class KenvyGitIgnoreVerificationResult(
    val missingExclusions: List<KenvyMissingGitIgnoreExclusion>
)

data class KenvyMissingGitIgnoreExclusion(
    val path: String,
    val suggestedEntry: String
)

object KenvyGitIgnoreVerifier {

    fun verify(
        projectDir: File,
        secretFilePaths: List<String>,
        gitignoreFile: File
    ): KenvyGitIgnoreVerificationResult {
        val rules = readRules(gitignoreFile)
        val missing = secretFilePaths
            .map { normalizePath(it) }
            .distinct()
            .filter { relativePath -> File(projectDir, relativePath).exists() }
            .filterNot { relativePath -> isIgnored(relativePath, rules) }
            .map { relativePath ->
                KenvyMissingGitIgnoreExclusion(
                    path = relativePath,
                    suggestedEntry = relativePath
                )
            }

        return KenvyGitIgnoreVerificationResult(missingExclusions = missing)
    }

    private fun readRules(gitignoreFile: File): List<GitIgnoreRule> {
        if (!gitignoreFile.exists() || gitignoreFile.isDirectory) return emptyList()

        return gitignoreFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val negated = line.startsWith("!")
                val rawPattern = if (negated) line.drop(1) else line
                val anchored = rawPattern.startsWith("/") || 
                    (rawPattern.contains("/") && !rawPattern.endsWith("/"))
                GitIgnoreRule(
                    pattern = normalizePattern(rawPattern),
                    negated = negated,
                    directoryPattern = rawPattern.endsWith("/"),
                    anchored = anchored
                )
            }
    }

    private fun isIgnored(relativePath: String, rules: List<GitIgnoreRule>): Boolean {
        var ignored = false
        for (rule in rules) {
            if (rule.matches(relativePath)) {
                ignored = !rule.negated
            }
        }
        return ignored
    }

    private fun normalizePattern(pattern: String): String =
        pattern.trim().replace('\\', '/').trim('/')

    private fun normalizePath(path: String): String =
        path.trim().replace('\\', '/').trim('/')

    private data class GitIgnoreRule(
        val pattern: String,
        val negated: Boolean,
        val directoryPattern: Boolean,
        val anchored: Boolean
    ) {
        fun matches(relativePath: String): Boolean {
            val normalizedRel = relativePath.replace('\\', '/').trim('/')
            return if (anchored) {
                normalizedRel == pattern || normalizedRel.startsWith("$pattern/")
            } else {
                normalizedRel == pattern || normalizedRel.startsWith("$pattern/") ||
                    normalizedRel.endsWith("/$pattern") || normalizedRel.contains("/$pattern/")
            }
        }
    }
}
