package net.henryhc.mocksniffer.prediction.input

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import net.henryhc.mocksniffer.codeinput.CodeRepository
import net.henryhc.mocksniffer.prediction.dependencyresolving.DepEntry
import net.henryhc.mocksniffer.utilities.jarName
import net.henryhc.mocksniffer.utilities.javaExecutable
import org.apache.commons.csv.CSVFormat
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors

class PredictionInputExtractionCommand : CliktCommand(name = "extract-prediction-input") {
    private val repoDir by option("-r", "--repo")
        .file(exists = true, folderOkay = true, fileOkay = false)
        .required()

    private val datasetFile by option("-ds", "--dataset", help = "The dataset CSV file")
        .file(exists = true, folderOkay = false, fileOkay = true)
        .required()

    private val rtPath by option("-rt")
        .file(folderOkay = true, fileOkay = false, exists = true)
        .required()

    private val parallelProjectsCount by option("-pp").int().default(20)

    private val outputCsv by option("-o", "--output").file().required()

    private val cgExpand by option("-cgexp").int().required()

    override fun run() {
        val sourceRepo = CodeRepository(repoDir.normalize().absoluteFile)
        println("Loading dataset")
        val datasetEntries =
            CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .parse(datasetFile.bufferedReader())
                .map { DepEntry(it["CUT"], it["DEP"], it["ORD"].toInt()) }
                .groupBy { sourceRepo.typeToProjectResolver[it.cut] }
        val projectsProcessed = AtomicInteger(0)
        val tempDir = Files.createTempDirectory("").toFile()
        val semaphore = java.util.concurrent.Semaphore(parallelProjectsCount)
        println("Splitting dataset by projects")
        val partialFiles =
            sourceRepo.projects
                .parallelStream()
                .filter { it in datasetEntries }
                .map { project ->
                    val inputFile = File(tempDir, "${project.hashCode()}.csv")
                    val outputFile = File(tempDir, "${project.hashCode()}_out.csv")
                    inputFile
                        .bufferedWriter()
                        .let { CSVFormat.DEFAULT.withHeader("CUT", "DEP", "ORD").print(it) }
                        .use { p ->
                            datasetEntries.getValue(project).forEach { p.printRecord(it.cut, it.dep, it.order) }
                        }
                    semaphore.acquire()
                    ProcessBuilder(
                        javaExecutable.absolutePath,
                        "-jar",
                        jarName,
                        "extract-prediction-input-pre-project",
                        "-p",
                        project.rootDirectory.absolutePath,
                        "-rt",
                        rtPath.absolutePath,
                        "-i",
                        inputFile.absolutePath,
                        "-o",
                        outputFile.absolutePath,
                        "-cgexp",
                        cgExpand.toString(),
                    ).redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .start()
                        .waitFor()
                    semaphore.release()
                    println("${projectsProcessed.incrementAndGet()} projects processed")
                    return@map outputFile
                }.collect(Collectors.toList())
        println("Merging CSVs from projects")
        outputCsv.delete()
        outputCsv.bufferedWriter().use { outputWriter ->
            val printer = CSVFormat.DEFAULT.withHeader(*predictionInputEntryHeaders).print(outputWriter)
            partialFiles
                .filter { it.exists() }
                .flatMap {
                    it.bufferedReader().use { reader ->
                        CSVFormat.DEFAULT
                            .withHeader(*predictionInputEntryHeaders)
                            .parse(reader)
                            .drop(1)
                            .map { it.parsePredictionInputEntry() }
                    }
                }.forEach { it.printCsvRecord(printer) }
        }
        tempDir.deleteRecursively()
    }
}
