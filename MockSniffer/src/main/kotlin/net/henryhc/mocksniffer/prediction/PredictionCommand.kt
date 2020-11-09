package net.henryhc.mocksniffer.prediction

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import org.apache.commons.csv.CSVFormat
import org.dmg.pmml.DataType
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder
import org.jpmml.evaluator.ProbabilityDistribution
import org.nield.kotlinstatistics.standardDeviation
import java.util.concurrent.Semaphore
import java.util.stream.Collectors

class PredictionCommand : CliktCommand(name = "batch-predict") {

    private val input by option("-i", "--input")
        .file(exists = true)
        .required()

    private val model by option("-m", "--model").file().required()

    private val output by option("-o", "--output")
        .file()
        .required()

    private val parallelPredictionThreads by option("-pp")
        .int()
        .default(Runtime.getRuntime().availableProcessors())

    override fun run() {
        val modelEvaluator = LoadingModelEvaluatorBuilder()
            .load(model)
            .build()
            .apply { verify() }

        val inputData = CSVFormat.DEFAULT
            .withFirstRecordAsHeader()
            .parse(input.bufferedReader())

        val semaphore = Semaphore(parallelPredictionThreads)
        val result = inputData.toList().parallelStream().map { rec ->
            semaphore.acquire()
            val arguments = modelEvaluator.inputFields
                .associateWith { it.prepare(rec[it.name.value]) }
                .mapKeys { it.key.name }
            val evalValues = modelEvaluator.evaluate(arguments)
            val key = evalValues.keys.single { it.value == "IS_MOCK" }
            val predResult = evalValues[key] as ProbabilityDistribution<Double>
            PredictionResult(rec["CUT"], rec["DEP"], rec["ORD"].toInt(), predResult).also { semaphore.release() }
        }.collect(Collectors.toList()).also { it.sortByDescending { it.probMock } }
        CSVFormat.DEFAULT
            .withHeader("CUT", "DEP", "ORD", "PROB_MOCK", "PROB_NOT_MOCK", "SHOULD_MOCK")
            .print(output.bufferedWriter())
            .use { p ->
                result.forEach {
                    p.printRecord(it.cut, it.dep, it.order, it.probMock, it.probNotMock, it.shouldMock)
                }
            }
    }
}