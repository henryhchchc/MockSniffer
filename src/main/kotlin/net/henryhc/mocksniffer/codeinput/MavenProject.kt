package net.henryhc.mocksniffer.codeinput

import java.io.File
import java.nio.file.Path


class MavenProject(codeRepository: CodeRepository, rootDirectory: File) : Project(codeRepository, rootDirectory) {
    override val targetClassDir = File(this.rootDirectory, "target/classes").normalize()
    override val testTargetClassDir = File(this.rootDirectory, "target/test-classes").normalize()
    override val classPath =
            Path.of(rootDirectory.absolutePath, "target", "dependency").toFile()
                    .walkTopDown().filter { it.extension == "jar" }.map { it.absolutePath }.toList()
}

fun File.getMavenProject(codeRepository: CodeRepository) = if (this.containsMavenProject())
    MavenProject(codeRepository, this)
else null

fun File.containsMavenProject() = this.isDirectory && this.listFiles { f -> f.name == "pom.xml" }?.isNotEmpty() == true
        && findSrcDir(this) != null && findTestSrcDir(
    this
) != null
