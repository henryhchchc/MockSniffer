package net.henryhc.mocksniffer.codeinput

import net.henryhc.mocksniffer.utilities.ConcurrentCachingResolver
import java.io.File

class CodeRepository(private val directory: File) {

    init {
        require(directory.isDirectory)
    }

    val projects by lazy { scanForProjects() }

    private fun scanForProjects(): List<Project> =
        this.directory.walkTopDown().filter { it.isDirectory && it.containsMavenProject() }
            .map { MavenProject(this, it.normalize().absoluteFile) }
            .toList()

    private val sourceDirectories by lazy { projects.map { it.sourceDirectory }.toList() }
    private val testDirectories by lazy { projects.map { it.testDirectory }.toList() }

    private val targetDirectories by lazy { projects.map { it.targetClassDir }.toList() }
    private val testTargetDirectories by lazy { projects.map { it.testTargetClassDir }.toList() }

    val targetTypeResolver =
        ConcurrentCachingResolver.of { typeName: String ->
            val fileNameWithPath = typeName.replace(".", File.separator)
            (targetDirectories + testTargetDirectories)
                .asSequence()
                .map { File(it, "$fileNameWithPath.class").normalize() }
                .firstOrNull { it.isFile }?.absolutePath ?: ""
        }

    val classTypeResolver =
        ConcurrentCachingResolver.of { className: String ->
            val fileName = targetTypeResolver[className]
            if (fileName.isNotEmpty()) {
                val classFile = File(fileName)
                if (testTargetDirectories.any { classFile.startsWith(it) })
                    ClassType.TEST_SCRIPT
                else
                    ClassType.PRODUCTION_CODE
            } else ClassType.UNKNOWN
        }

    val typeToProjectResolver =
        ConcurrentCachingResolver.of { typeName: String ->
            val sourceFileName = targetTypeResolver[typeName]
            if (sourceFileName.isEmpty()) Project.EMPTY
            else projects.first { (it.targetFiles + it.testTargetFiles).any { f -> f.absolutePath == sourceFileName } }
        }
}
