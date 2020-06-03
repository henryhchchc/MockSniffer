package net.henryhc.mocksniffer.dependencyresolving

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import net.henryhc.mocksniffer.MainCommand
import net.henryhc.mocksniffer.codeinput.CodeRepository

class ExtractTuples : CliktCommand(name = "extract-tuples") {

    private val java8RuntimePath by option("-rt", "--java-8-runtime")
        .file(folderOkay = true, exists = true)
        .required()

    private val repoDir by option("--repo", "-r").file(folderOkay = true, exists = true).required()

    override fun run() {
        val repo = CodeRepository(repoDir)
        println(repo.projects.joinToString(System.lineSeparator()) { it.rootDirectory.absolutePath })
    }

}
