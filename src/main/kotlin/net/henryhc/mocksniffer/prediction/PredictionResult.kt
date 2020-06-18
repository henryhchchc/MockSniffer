package net.henryhc.mocksniffer.prediction

import fj.test.Bool
import org.jpmml.evaluator.ProbabilityDistribution


data class PredictionResult(
    val cut: String,
    val dep: String,
    val order: Int,
    val probMock: Double,
    val probNotMock: Double,
    val shouldMock: Boolean
) {
    constructor(cut: String, dep: String, order: Int, predResult: ProbabilityDistribution<Double>)
            : this(
        cut,
        dep,
        order,
        predResult.getProbability(true),
        predResult.getProbability(false),
        predResult.result as Boolean
    )
}