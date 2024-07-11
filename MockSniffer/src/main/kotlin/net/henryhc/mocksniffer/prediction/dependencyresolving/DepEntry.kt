package net.henryhc.mocksniffer.prediction.dependencyresolving

data class DepEntry(
    val cut: String,
    val dep: String,
    val order: Int,
)
