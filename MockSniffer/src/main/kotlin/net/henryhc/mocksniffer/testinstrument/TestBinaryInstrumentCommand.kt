package net.henryhc.mocksniffer.testinstrument

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import net.henryhc.mocksniffer.codeinput.CodeRepository
import net.henryhc.mocksniffer.codeinput.Project
import net.henryhc.mocksniffer.utilities.jarName
import net.henryhc.mocksniffer.utilities.javaExecutable
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Semaphore
import java.util.stream.Collectors

class TestInstrumentCommand : CliktCommand(name = "test-bytecode-instrument") {
    private val sourceRepo by option("--source-repo", "-r")
        .file(folderOkay = true, fileOkay = false, exists = true)
        .required()

    private val rtJarPath by option("--rt-jar", "-rt")
        .file(folderOkay = false, fileOkay = true, exists = true)
        .required()

    private val parallelProjectsCount by option("-pp").int().default(20)

    override fun run() = instrumentByteCode(CodeRepository(sourceRepo.normalize().absoluteFile))

    private fun instrumentByteCode(repo: CodeRepository) {
        val semaphore = Semaphore(parallelProjectsCount)
        val copyBackPairs =
            repo.projects
                .parallelStream()
                .flatMap {
                    semaphore.acquire()
                    startSootProcess(it, rtJarPath.normalize().absolutePath).stream().also { semaphore.release() }
                }.collect(Collectors.toList())
        println("Instrumentation completed, copy back started")
        copyBackPairs.forEach {
            File(it.first).takeIf { d -> d.exists() && d.isDirectory }?.copyRecursively(File(it.second), overwrite = true)
        }
    }

    private fun startSootProcess(
        project: Project,
        rtJarPath: String,
    ): List<Pair<String, String>> {
        val tempDir =
            Files
                .createTempDirectory("soot_temp")
                .toAbsolutePath()
                .normalize()
                .toString()
        ProcessBuilder(
            javaExecutable.absolutePath,
            "-jar",
            jarName,
            PreProjectInstrumentCommand.commandName,
            "-r",
            project.codeRepository.directory.absolutePath,
            "-p",
            project.rootDirectory.absolutePath,
            "-t",
            tempDir,
            "-rt",
            rtJarPath,
            "-tar",
            "src",
        ).redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor()
        ProcessBuilder(
            javaExecutable.absolutePath,
            "-jar",
            jarName,
            PreProjectInstrumentCommand.commandName,
            "-r",
            project.codeRepository.directory.absolutePath,
            "-p",
            project.rootDirectory.absolutePath,
            "-t",
            tempDir,
            "-rt",
            rtJarPath,
            "-tar",
            "test",
        ).redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor()
        return listOf(
            Path
                .of(tempDir, "src")
                .toAbsolutePath()
                .normalize()
                .toString() to project.targetClassDir.absolutePath,
            Path
                .of(tempDir, "test")
                .toAbsolutePath()
                .normalize()
                .toString() to project.testTargetClassDir.absolutePath,
        )
    }
}
