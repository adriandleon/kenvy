package io.github.adriandleon.kenvy

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KenvyLocalPropertiesMergeTest {

    @TempDir
    lateinit var tempDir: File

    private fun writePropsFile(name: String, content: String): File =
        File(tempDir, name).also { it.writeText(content) }

    @Test fun `empty file list returns empty map`() {
        val result = KenvyResolver.loadAndMergeLocalProperties(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test fun `single file loads its properties`() {
        val f = writePropsFile("root.properties", "api_key=root-secret\nbase_url=https://example.com")
        val result = KenvyResolver.loadAndMergeLocalProperties(listOf(f))
        assertEquals("root-secret", result["api_key"])
        assertEquals("https://example.com", result["base_url"])
    }

    @Test fun `missing files are silently skipped`() {
        val missing = File(tempDir, "nonexistent.properties")
        val result = KenvyResolver.loadAndMergeLocalProperties(listOf(missing))
        assertTrue(result.isEmpty())
    }

    @Test fun `later file overrides earlier file for the same key`() {
        val root = writePropsFile("root.properties", "api_key=root-secret")
        val module = writePropsFile("module.properties", "api_key=module-secret")
        val result = KenvyResolver.loadAndMergeLocalProperties(listOf(root, module))
        assertEquals("module-secret", result["api_key"])
    }

    @Test fun `earlier file keys not in later file are preserved`() {
        val root = writePropsFile("root.properties", "api_key=root-secret\nroot_only=root-only-value")
        val module = writePropsFile("module.properties", "api_key=module-secret\nmodule_only=module-only-value")
        val result = KenvyResolver.loadAndMergeLocalProperties(listOf(root, module))
        assertEquals("module-secret", result["api_key"])
        assertEquals("root-only-value", result["root_only"])
        assertEquals("module-only-value", result["module_only"])
    }

    @Test fun `missing root file does not prevent module file from loading`() {
        val missing = File(tempDir, "nonexistent-root.properties")
        val module = writePropsFile("module.properties", "api_key=module-secret")
        val result = KenvyResolver.loadAndMergeLocalProperties(listOf(missing, module))
        assertEquals("module-secret", result["api_key"])
    }

    @Test fun `scoped keys from root file are preserved when module file does not define them`() {
        val root = writePropsFile("root.properties", "api_key=root-secret\napi_key.android.debug=root-android-debug")
        val module = writePropsFile("module.properties", "api_key=module-secret")
        val result = KenvyResolver.loadAndMergeLocalProperties(listOf(root, module))
        assertEquals("module-secret", result["api_key"])
        assertEquals("root-android-debug", result["api_key.android.debug"])
    }

    @Test fun `module scoped key overrides root scoped key for the same path`() {
        val root = writePropsFile("root.properties", "api_key.android.debug=root-android-debug")
        val module = writePropsFile("module.properties", "api_key.android.debug=module-android-debug")
        val result = KenvyResolver.loadAndMergeLocalProperties(listOf(root, module))
        assertEquals("module-android-debug", result["api_key.android.debug"])
    }

    @Test fun `three files merge in order with later values winning`() {
        val first = writePropsFile("first.properties", "key=first")
        val second = writePropsFile("second.properties", "key=second")
        val third = writePropsFile("third.properties", "key=third")
        val result = KenvyResolver.loadAndMergeLocalProperties(listOf(first, second, third))
        assertEquals("third", result["key"])
    }
}
