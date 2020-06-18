package net.henryhc.mocksniffer

import com.github.ajalt.clikt.core.subcommands
import net.henryhc.mocksniffer.dependencyresolving.ExtractTuples
import net.henryhc.mocksniffer.dependencyresolving.ExtractTuplesSingleProject
import net.henryhc.mocksniffer.featureextraction.ExtractCodeLevelFeaturesCommand
import net.henryhc.mocksniffer.featureextraction.ProjectExtractorCommand
import net.henryhc.mocksniffer.prediction.PredictionCommand

fun main(args: Array<String>) {
    MainCommand()
        .subcommands(
            ExtractTuples(),
            ExtractTuplesSingleProject(),
            ExtractCodeLevelFeaturesCommand(),
            ProjectExtractorCommand(),
            PredictionCommand()
        )
        .main(args)
}
