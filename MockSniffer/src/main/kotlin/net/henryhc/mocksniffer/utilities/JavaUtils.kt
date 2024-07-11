package net.henryhc.mocksniffer.utilities

import java.io.File
import java.nio.file.Path

val javaExecutable =
    System
        .getProperty("java.home")
        .let {
            Path.of(it, "bin", "java")
        }.toAbsolutePath()
        .normalize()
        .toFile()

const val jarName = "mocksniffer.jar"

fun getRuntimeJars(runtimeFolder: File) = File(runtimeFolder, "jre/lib").walkTopDown().filter { it.extension == "jar" }
