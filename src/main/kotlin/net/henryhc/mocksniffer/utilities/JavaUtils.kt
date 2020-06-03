package net.henryhc.mocksniffer.utilities

import java.nio.file.Path

val javaExecutable = System.getProperty("java.home").let {
    Path.of(it, "bin", "java")
}.toAbsolutePath().normalize().toFile()

