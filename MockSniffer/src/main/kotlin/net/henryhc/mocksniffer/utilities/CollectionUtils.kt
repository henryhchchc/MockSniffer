package net.henryhc.mocksniffer.utilities

fun <T> Iterator<T>.toList() = this.asSequence().toList()

fun <E> Iterable<E>.chunkedByProcessorCount(factor: Double = 0.8) =
    this.chunked((Runtime.getRuntime().availableProcessors() * factor).toInt())
