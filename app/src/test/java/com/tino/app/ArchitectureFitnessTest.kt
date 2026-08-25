package com.tino.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Small, executable architecture guardrails for the boundaries that are easiest to regress.
 * This intentionally checks source imports instead of trying to build an academic dependency
 * framework around the current Android module.
 */
class ArchitectureFitnessTest {
    @Test
    fun agentLayerDoesNotReachRoomDaosOrDatabase() {
        val sources = sourceFiles("domain/agent")
        assertTrue("agent source directory must exist", sources.isNotEmpty())
        assertForbiddenImports(
            sources = sources,
            forbidden = listOf("core.database.*Dao", "core.database.TinoDatabase"),
        )
    }

    @Test
    fun a2uiRendererDoesNotReachCommerceRepository() {
        val sources = sourceFiles("ui/a2ui")
        assertTrue("A2UI source directory must exist", sources.isNotEmpty())
        assertForbiddenImports(
            sources = sources,
            forbidden = listOf("domain.commerce.CommerceRepository", "core.database.*Dao"),
        )
    }

    @Test
    fun globalScopeIsNotIntroduced() {
        val sources = sourceFiles("")
        val offenders = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if ("GlobalScope" in line) "${file.path}:${index + 1}" else null
            }
        }
        assertTrue("GlobalScope creates work without lifecycle ownership: $offenders", offenders.isEmpty())
    }

    @Test
    fun mainActivityRemainsACompositionHost() {
        val mainActivity = sourceFiles("")
            .firstOrNull { it.name == "MainActivity.kt" }
            ?: error("MainActivity.kt must exist")

        assertTrue(
            "MainActivity should remain a thin host; move composition and screens to TinoApp.kt",
            mainActivity.readLines().size < 100,
        )
        assertForbiddenImports(
            sources = listOf(mainActivity),
            forbidden = listOf(
                "CommerceRepository",
                "core.database.",
                "registerSale(",
                "registerCredit(",
            ),
        )
    }

    private fun assertForbiddenImports(sources: List<File>, forbidden: List<String>) {
        val offenders = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (forbidden.any { token -> token in line }) "${file.path}:${index + 1}: $line" else null
            }
        }
        assertFalse("Architecture boundary crossed: $offenders", offenders.isNotEmpty())
    }

    private fun sourceFiles(relativePath: String): List<File> {
        var projectRoot = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (File(projectRoot, "app/src/main/java").isDirectory) return@repeat
            projectRoot.parentFile?.let { projectRoot = it }
        }
        val sourceRoot = File(projectRoot, "app/src/main/java/com/tino/app")
        if (!sourceRoot.isDirectory) return emptyList()
        return File(sourceRoot, relativePath)
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }
}
