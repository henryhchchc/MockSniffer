package net.henryhc.mocksniffer.prediction.dependencyresolving

import soot.SootClass

private val excludeList = setOf(
    "java.lang.Object",

    // String related
    "java.lang.String",
    "java.lang.StringBuilder",

    // Primitive types
    "java.lang.Integer",
    "java.lang.Double",
    "java.lang.Float",
    "java.lang.Boolean",
    "java.lang.Short",
    "java.lang.Character",
    "java.lang.Byte",

    "java.lang.Class"
)

fun SootClass.shouldIncludeInDependencies() = excludeList.none { this.name.startsWith(it) }