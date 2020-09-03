package net.henryhc.mocksniffer.codeinput

import java.io.File
import java.nio.file.Files

abstract class Project(val codeRepository: CodeRepository, rootDirectory: File) {

    init {
        require(rootDirectory.isDirectory) { "Directory not exists" }
    }

    val rootDirectory: File = rootDirectory.absoluteFile
    abstract val classPath: List<String>

    val sourceFiles by lazy { scanSourceFiles().toList() }
    val testFiles by lazy { scanTestFiles().toList() }

    val targetFiles by lazy { targetClassDir.walkTopDown().filter { it.extension == "class" }.toList() }
    val testTargetFiles by lazy { testTargetClassDir.walkTopDown().filter { it.extension == "class" }.toList() }

    abstract val targetClassDir: File
    abstract val testTargetClassDir: File

    val sourceDirectory: File by lazy { findSrcDir(rootDirectory)!! }
    val testDirectory: File by lazy { findTestSrcDir(rootDirectory)!! }
    private fun scanSourceFiles() = sourceDirectory.walkTopDown().filter { it.isFile && it.extension == "java" }
    private fun scanTestFiles() = testDirectory.walkTopDown().filter { it.isFile && it.extension == "java" }


    companion object {
        private val stubDir = Files.createTempDirectory("").toFile()
        val EMPTY = object : Project(CodeRepository(
            stubDir
        ), stubDir
        ) {
            override val classPath: List<String> get() = throw NotImplementedError()
            override val targetClassDir: File get() = throw NotImplementedError()
            override val testTargetClassDir: File get() = throw NotImplementedError()
        }
    }
}


private val srcDirs = listOf(
    listOf("src", "main", "java").joinToString(File.separator),
    listOf("src", "java").joinToString(File.separator)
)

private val testSrcDirs = listOf(
    listOf("src", "test", "java").joinToString(File.separator),
    listOf("src", "test").joinToString(File.separator)
)

private fun findCodeDir(rootDir: File, candidates: List<String>) = candidates
    .map { File(rootDir, it).normalize() }
    .firstOrNull { it.isDirectory }

fun findSrcDir(rootDirectory: File) = findCodeDir(
    rootDirectory,
    srcDirs
)
fun findTestSrcDir(rootDirectory: File) = findCodeDir(
    rootDirectory,
    testSrcDirs
)