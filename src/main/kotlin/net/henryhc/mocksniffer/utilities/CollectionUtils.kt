package net.henryhc.mocksniffer.utilities

fun <T> Iterator<T>.toList() = this.asSequence().toList()

