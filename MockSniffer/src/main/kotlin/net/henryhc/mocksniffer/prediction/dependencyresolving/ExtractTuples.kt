package net.henryhc.mocksniffer.prediction.dependencyresolving

import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import net.henryhc.mocksniffer.SootEnvCommand
import net.henryhc.mocksniffer.codeinput.CodeRepository
import net.henryhc.mocksniffer.utilities.jarName
import net.henryhc.mocksniffer.utilities.javaExecutable
import org.apache.commons.csv.CSVFormat
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Semaphore
import kotlin.random.Random

class ExtractTuples : SootEnvCommand(name = "extract-dataset") {

    private val repoDir by option("--repo", "-r").file(folderOkay = true, exists = true).required()

    private val parallelProjects by option("-pp").int().default(10)

    private val outputFile by option("-o", "--output").file().required()

    override fun run() {
        val repo = CodeRepository(repoDir)
        val tempDir = Files.createTempDirectory("mocksniffer_").toFile()
        val semaphore = Semaphore(parallelProjects)
        val projectsPartialFiles =
            repo.projects.associateWith { Path.of(tempDir.absolutePath, "partial_${Random(2333)}.csv").toFile() }
        println("Starting and waiting sub processes")
        repo.projects.parallelStream().forEach {
            semaphore.acquire()
            ProcessBuilder(
                javaExecutable.absolutePath,
                "-jar", jarName,
                "extract-dataset-project",
                "-rt", java8RuntimePath.absolutePath,
                "--project", it.rootDirectory.absolutePath,
                "--output", projectsPartialFiles.getValue(it).absolutePath
            )
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
                .waitFor()
            semaphore.release()
        }
        println("Merging results form sub-processes")
        val result = projectsPartialFiles.values.flatMap { f ->
            CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .parse(f.bufferedReader())
                .map { DepEntry(it["CUT"], it["DEP"], it["ORD"].toInt()) }
        }
        tempDir.deleteRecursively()
        CSVFormat.DEFAULT
            .withHeader("CUT", "DEP", "ORD")
            .print(outputFile.bufferedWriter())
            .use { p -> result.forEach { p.printRecord(it.cut, it.dep, it.order) } }
    }

}
