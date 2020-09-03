package net.henryhc.mocksniffer.trainingdata.testlogextraction

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import me.tongfei.progressbar.ProgressBar
import net.henryhc.mocksniffer.trainingdata.testlogextraction.datamodels.invocationDistillEntryHeaders
import net.henryhc.mocksniffer.trainingdata.testlogextraction.datamodels.printCsvRecord
import net.henryhc.mocksniffer.trainingdata.testlogextraction.datamodels.toDistillEntry
import net.henryhc.mocksniffer.utilities.sscanSeq
import net.henryhc.mocksniffer.utilities.stepSynchronized
import net.henryhc.mocksniffer.codeinput.CodeRepository
import net.henryhc.mocksniffer.trainingdata.testlogextraction.datamodels.parseMethodParamItem
import org.apache.commons.csv.CSVFormat
import redis.clients.jedis.Jedis
import java.io.File
import java.util.stream.Collectors

class MethodParamsExtraction : CliktCommand(name = "extract-method-params") {

    private val sourceRepo by option("--source-repo", "-r")
            .file(folderOkay = true, fileOkay = false, exists = true)
            .required()

    private val redisServer by option("--redis-server").default("127.0.0.1")

    private val redisPort by option("--redis-port").int().default(6379)

    private val objCsvFile by option("--output").file(exists = false).required()

    override fun run() {
        distillStackTrace(
                sourceRepo,
                objCsvFile
        )
    }


    private fun distillStackTrace(sourceRepoDir: File,  objCsv: File) {
        val sourceRepo = CodeRepository(sourceRepoDir.normalize().absoluteFile)
        distillObjects(sourceRepo, objCsv)
    }

    private fun distillObjects(repo: CodeRepository, outputCsv: File) = Jedis(redisServer, redisPort).use { redisClient ->
        val entryCount = redisClient.scard("obj")
        val pb = ProgressBar("Params Extraction", entryCount)

        val printer = CSVFormat.DEFAULT.withHeader(*invocationDistillEntryHeaders)
                .print(outputCsv.bufferedWriter())
        redisClient.sscanSeq("obj", 10000).chunked(10000).forEach {
            it.parallelStream()
                    .map { it.lines().also { pb.stepSynchronized() } }
                    .filter { it.size >= 2 }
                    .map { parseMethodParamItem(it, repo) }
                    .filter { it.testFrame != null && it.isInTest }
                    .map {
                        it.toDistillEntry().apply {
                            classType = repo.classTypeResolver[it.typeName.split("$").first(String::isNotBlank)].toString()
                        }
                    }.collect(Collectors.toList())
                    .forEach { it.printCsvRecord(printer) }
        }

        redisClient.disconnect()
        printer.flush()
        printer.close()
        pb.close()
    }
}
