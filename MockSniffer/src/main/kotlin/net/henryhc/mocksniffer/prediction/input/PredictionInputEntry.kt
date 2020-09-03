package net.henryhc.mocksniffer.prediction.input

import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord

data class PredictionInputEntry(
        val classUnderTest: String,
        val dependency: String,
        val dependencyOrder: Int,
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
        val depUnstableAPIsTransitive: Int
)


val predictionInputEntryHeaders = arrayOf(
        "CUT",
        "D",
        "DORD",
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
        "CONDCALL"
)

fun PredictionInputEntry.printCsvRecord(printer: CSVPrinter) =
        printer.printRecord(
                this.classUnderTest,
                this.dependency,
                this.dependencyOrder,
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
                this.controlDominance
        )

fun CSVRecord.parsePredictionInputEntry() = PredictionInputEntry(
        this["CUT"],
        this["D"],
        this["DORD"].toInt(),
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
        this["TUAPI"].toInt()
)
