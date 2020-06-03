package net.henryhc.mocksniffer

import com.github.ajalt.clikt.core.subcommands
import net.henryhc.mocksniffer.dependencyresolving.ExtractTuples

fun main(args: Array<String>) = MainCommand()
    .subcommands(
        ExtractTuples()
    )
    .main(args)
