package net.henryhc.mocksniffer.trainingdata

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import net.henryhc.mocksniffer.utilities.jarName
import net.henryhc.mocksniffer.utilities.javaExecutable

class BatchTrainingDataExtractCommand : CliktCommand(name = "batch-extract") {

    private val projectInfoList by option("-i")
            .file().required()

    private val j8RtPath by option("-rt")
            .file(folderOkay = true, fileOkay = false, exists = true)
            .required()

    private val dryRun by option("--dry-run").flag()

    private val parallelProjectsCount by option("-pp").int().default(30)

    private val cgExpand by option("-cgexp").int().default(5)

    private val noTestLogExtraction by option("-nc").flag()

    private val noFeature by option("-nf").flag()

    override fun run() {
        val lines = projectInfoList.readLines().map { it.trim() }
        for (l in lines) {
            val parts = l.split(",")
            val projectName = parts[0]
            val distillCsv = parts[1]
            val repoDir = parts[2]
            val output = parts[3]

            println("======================== $projectName ===========================")
            println("$distillCsv -> ${projectName}_tuples.csv -> $output")

            if (dryRun)
                continue

            if (!noTestLogExtraction) {
                ProcessBuilder(javaExecutable.absolutePath, "-jar", jarName, "test-log-dataset",
                        "-r", repoDir,
                        "--obj-csv", distillCsv,
                        "-o", "./tuple_data/${projectName}_tuples.csv"
                )
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .start()
                        .waitFor()
            }
            if (!noFeature) {
                ProcessBuilder(javaExecutable.absolutePath, "-jar", jarName, "extract-training-data",
                        "-rt", j8RtPath.absolutePath,
                        "-r", repoDir,
                        "-ds", "./tuple_data/${projectName}_tuples.csv",
                        "-o", output,
                        "-pp", parallelProjectsCount.toString(),
                        "-cgexp", cgExpand.toString()
                )
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .start()
                        .waitFor()
            }

        }
    }
}