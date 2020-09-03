package net.henryhc.mocksniffer

import com.github.ajalt.clikt.core.subcommands
import net.henryhc.mocksniffer.prediction.dependencyresolving.ExtractTuples
import net.henryhc.mocksniffer.prediction.dependencyresolving.ExtractTuplesSingleProject
import net.henryhc.mocksniffer.prediction.input.PredictionInputExtractionCommand
import net.henryhc.mocksniffer.prediction.input.PredictionInputExtractionPreProject
import net.henryhc.mocksniffer.prediction.PredictionCommand
import net.henryhc.mocksniffer.testinstrument.AddLoggerScriptCommand
import net.henryhc.mocksniffer.testinstrument.PreProjectInstrumentCommand
import net.henryhc.mocksniffer.testinstrument.TestInstrumentCommand
import net.henryhc.mocksniffer.trainingdata.BatchTrainingDataExtractCommand
import net.henryhc.mocksniffer.trainingdata.ExtractTrainingData
import net.henryhc.mocksniffer.trainingdata.ExtractTrainingDataPreProject
import net.henryhc.mocksniffer.trainingdata.testlogextraction.ExtractTestLogDatasetCommand
import net.henryhc.mocksniffer.trainingdata.testlogextraction.MethodParamsExtraction

fun main(args: Array<String>) {
    MainCommand()
        .subcommands(
            // Dependencies identification
            ExtractTuples(),
            ExtractTuplesSingleProject(),

            // Prediction input extraction
            PredictionInputExtractionCommand(),
            PredictionInputExtractionPreProject(),

            // Prediction
            PredictionCommand(),

            // Training data
            ExtractTestLogDatasetCommand(),
            MethodParamsExtraction(),
            ExtractTrainingData(),
            ExtractTrainingDataPreProject(),
            BatchTrainingDataExtractCommand(),

            // Test instrument
            TestInstrumentCommand(),
            AddLoggerScriptCommand(),
            PreProjectInstrumentCommand()
        )
        .main(args)
}
