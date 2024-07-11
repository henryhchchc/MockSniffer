package net.henryhc.mocksniffer.prediction.dependencyresolving

import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import net.henryhc.mocksniffer.SootEnvCommand
import net.henryhc.mocksniffer.codeinput.CodeRepository
import net.henryhc.mocksniffer.utilities.configureSoot
import soot.PackManager
import soot.Transform

class ExtractTuplesSingleProject : SootEnvCommand(name = "extract-dataset-project") {
    private val projectDir by option("-p", "--project")
        .file(folderOkay = true, exists = true)
        .required()

    private val outputFile by option("-o", "--output").file().required()

    private val searchDepth by option("-d", "--search-depth").int().default(5)

    override fun run() {
        val repo = CodeRepository(projectDir.normalize().absoluteFile)
        val project = repo.projects.single { it.rootDirectory == projectDir.normalize().absoluteFile }
        println("Start analyzing ${project.rootDirectory}")
        configureSoot(project, java8RuntimePath)
        PackManager
            .v()
            .getPack("wstp")
            .add(Transform("wstp.extract_data", DataExtractionTransformer(outputFile, searchDepth)))
        soot.Main.v().run(emptyArray())
    }
}
