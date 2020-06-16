package net.henryhc.mocksniffer.featureextraction

import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord

data class CodeLevelFeatureEntry(
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
        val depInUnstableAPIList: Boolean,
        val depIsFinal: Boolean,
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


val codeLevelFeatureEntryHeader = arrayOf(
        "CUT",
        "DEP",
        "ORD",
        // Features
        "DEP_ABS",
        "DEP_INT",
        "DEP_JDK",
        "DEP_ICB",
        "DEP_NDEP",
        "DEP_NTRANDEP",
        "DEP_NF",
        "DEP_IN_UAPI",
        "DEP_FINAL",
        "DEP_UAPI",
        "DEP_UAPI_TRAN",
        "DEP_UINT",
        "DEP_INV_SYNC",
        "CALL_SITE_ON_DEP",
        "ARG_FPR",
        "RET_BFA",
        "EXP_CAT",
        "CTRL_DOM"
)

fun CodeLevelFeatureEntry.printCsvRecord(printer: CSVPrinter) =
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
                this.depInUnstableAPIList,
                this.depIsFinal,
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

fun CSVRecord.parseCodeLevelFeatureEntry() = CodeLevelFeatureEntry(
        this["CUT"],
        this["DEP"],
        this["ORD"].toInt(),
        this["DEP_ABS"]!!.toBoolean(),
        this["DEP_INT"]!!.toBoolean(),
        this["DEP_JDK"]!!.toBoolean(),
        this["DEP_ICB"]!!.toBoolean(),
        this["DEP_NDEP"].toInt(),
        this["DEP_NTRANDEP"].toInt(),
        this["DEP_NF"].toInt(),
        this["DEP_IN_UAPI"]!!.toBoolean(),
        this["DEP_FINAL"]!!.toBoolean(),
        this["DEP_UINT"]!!.toBoolean(),
        this["DEP_UAPI"].toInt(),
        this["DEP_INV_SYNC"].toInt(),
        this["CALL_SITE_ON_DEP"].toInt(),
        this["ARG_FPR"].toInt(),
        this["RET_BFA"].toInt(),
        this["EXP_CAT"].toInt(),
        this["CTRL_DOM"].toInt(),
        this["DEP_UAPI_TRAN"].toInt()
)
