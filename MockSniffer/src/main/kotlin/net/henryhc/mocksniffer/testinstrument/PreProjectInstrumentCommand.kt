package net.henryhc.mocksniffer.testinstrument

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import net.henryhc.mocksniffer.codeinput.CodeRepository

class PreProjectInstrumentCommand : CliktCommand(name = commandName) {

    companion object {
        const val commandName = "soot-instrument-test"
    }

    private val sourceRepo by option("--source-repo", "-r")
        .file(folderOkay = true, fileOkay = false, exists = true)
        .required()

    private val projectDir by option("--project-dir", "-p")
        .file(folderOkay = true, fileOkay = false, exists = true)
        .required()

    private val tempDir by option("--temp-dir", "-t")
        .file(folderOkay = true, fileOkay = false, exists = true)
        .required()

    private val rtJarPath by option("--rt-jar", "-rt")
        .file(folderOkay = false, fileOkay = true, exists = true)
        .required()

    private val targetType by option("-tar", "--target-type")
        .choice("src", "test")
        .required()

    override fun run() {
        val repo = CodeRepository(sourceRepo.absoluteFile)
        val project = repo.projects.first { it.rootDirectory.absolutePath == projectDir.normalize().absolutePath }
        SootLauncher(project, tempDir.normalize().absolutePath, rtJarPath.normalize().absolutePath, targetType).start()
    }
}