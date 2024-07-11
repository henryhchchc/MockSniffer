package net.henryhc.mocksniffer.trainingdata

import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord

data class TrainingDataEntry(
    val testClass: String,
    val testMethod: String,
    val classUnderTest: String,
    val dependency: String,
    val label: String,
    val depIsAbstract: Boolean,
    val depIsInterface: Boolean,
    val depIsJDKClass: Boolean,
    val depIsInCodeBase: Boolean,
    val depDeps: Int,
    val depTransitiveDeps: Int,
    val depFields: Int,
    val depImplUnstableInterfaces: Boolean,
    val depUnstableAPIs: Int,
    val depInvSynchronizedMethods: Int,
    val callSitesOnDependency: Int,
    val argsFromFPR: Int,
    val returnValueBFA: Int,
    val exceptionsCaught: Int,
    val controlDominance: Int,
    val methodSignature: String,
    val depUnstableAPIsTransitive: Int,
)

val trainingDataEntryCsvHeader =
    arrayOf(
        "TC",
        "TM",
        "CUT",
        "D",
        "L",
        // Features
        "ABS",
        "INT",
        "JDK",
        "ICB",
        "DEP",
        "TDEP",
        "FIELD",
        "UAPI",
        "TUAPI",
        "UINT",
        "SYNC",
        "CALLSITES",
        "AFPR",
        "RBFA",
        "EXPCAT",
        "CONDCALL",
        "METHOD",
    )

fun TrainingDataEntry.printCsvRecord(printer: CSVPrinter) =
    printer.printRecord(
        this.testClass,
        this.testMethod,
        this.classUnderTest,
        this.dependency,
        this.label,
        // Features
        this.depIsAbstract,
        this.depIsInterface,
        this.depIsJDKClass,
        this.depIsInCodeBase,
        this.depDeps,
        this.depTransitiveDeps,
        this.depFields,
        this.depUnstableAPIs,
        this.depUnstableAPIsTransitive,
        this.depImplUnstableInterfaces,
        this.depInvSynchronizedMethods,
        this.callSitesOnDependency,
        this.argsFromFPR,
        this.returnValueBFA,
        this.exceptionsCaught,
        this.controlDominance,
        this.methodSignature,
    )

fun CSVRecord.parseTrainingDataEntry() =
    TrainingDataEntry(
        this["TC"],
        this["TM"],
        this["CUT"],
        this["D"],
        this["L"],
        this["ABS"]!!.toBoolean(),
        this["INT"]!!.toBoolean(),
        this["JDK"]!!.toBoolean(),
        this["ICB"]!!.toBoolean(),
        this["DEP"].toInt(),
        this["TDEP"].toInt(),
        this["FIELD"].toInt(),
        this["UINT"]!!.toBoolean(),
        this["UAPI"].toInt(),
        this["SYNC"].toInt(),
        this["CALLSITES"].toInt(),
        this["AFPR"].toInt(),
        this["RBFA"].toInt(),
        this["EXPCAT"].toInt(),
        this["CONDCALL"].toInt(),
        this["METHOD"],
        this["TUAPI"].toInt(),
    )
