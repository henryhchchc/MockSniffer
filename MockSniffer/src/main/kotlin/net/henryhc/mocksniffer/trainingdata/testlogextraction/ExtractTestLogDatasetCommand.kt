package net.henryhc.mocksniffer.trainingdata.testlogextraction

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import me.tongfei.progressbar.ProgressBar
import net.henryhc.mocksniffer.codeinput.ClassType
import net.henryhc.mocksniffer.codeinput.CodeRepository
import net.henryhc.mocksniffer.trainingdata.testlogextraction.datamodels.DatasetEntry
import net.henryhc.mocksniffer.trainingdata.testlogextraction.datamodels.TestLogParamEntry
import net.henryhc.mocksniffer.trainingdata.testlogextraction.datamodels.datasetEntryCsvHeader
import net.henryhc.mocksniffer.trainingdata.testlogextraction.datamodels.invocationDistillEntryHeaders
import net.henryhc.mocksniffer.trainingdata.testlogextraction.datamodels.printCsvRecord
import net.henryhc.mocksniffer.trainingdata.testlogextraction.datamodels.toInvOrObjDistillEntry
import net.henryhc.mocksniffer.utilities.*
import org.apache.commons.csv.CSVFormat
import java.io.File
import java.util.stream.Collectors

class ExtractTestLogDatasetCommand : CliktCommand(name = "test-log-dataset") {
    private val sourceRepo by option("--source-repo", "-r")
        .file(folderOkay = true, fileOkay = false, exists = true)
        .required()

    private val objCsvFile by option("--obj-csv").file(exists = true).required()

    private val outputCsv by option("--output", "-o").file(exists = false).required()

    override fun run() {
        constructDataset(
            CodeRepository(sourceRepo.normalize().absoluteFile),
            objCsvFile,
            outputCsv,
        )
    }

    private fun constructDataset(
        repo: CodeRepository,
        objCsvFile: File,
        outputDir: File,
    ) {
        println("Reading obj params csv")
        val objParams =
            CSVFormat.DEFAULT
                .withHeader(*invocationDistillEntryHeaders)
                .parse(objCsvFile.bufferedReader())
                .toList()
                .drop(1)
                .parallelStream()
                .map { it.toInvOrObjDistillEntry() }
                .collect(Collectors.toList())
                .distinct()
        val testClassNames = objParams.map { it.testClassName }.toSet()
        println("Constructing CUT map")
        val cutMap = constructCUTMap(repo, testClassNames)
        println("${cutMap.keys.size} test class with CUT found")

        println("Constructing dataset of ${objParams.size} records")
        val dataset = createDataset(repo, cutMap, objParams)

        println("Writing CSV")
        CSVFormat.DEFAULT
            .withHeader(*datasetEntryCsvHeader)
            .print(outputDir.bufferedWriter())
            .use { printer ->
                dataset.forEach { it.printCsvRecord(printer) }
            }
    }

    private val depBlacklist =
        setOf(
            "java.lang.Object",
            "java.lang.String",
            "java.lang.StringBuilder",
            "java.lang.Integer",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Boolean",
            "java.lang.Short",
            "java.lang.Character",
            "java.lang.Byte",
            "java.lang.Class",
        )

    private fun createDataset(
        sourceRepo: CodeRepository,
        cutMap: Map<String, String>,
        objParams: List<TestLogParamEntry>,
    ): List<DatasetEntry> {
        val pb = ProgressBar("Construct dataset", objParams.size.toLong())
        val entries =
            objParams
                .map { Pair(it, "param") }
                .chunkedByProcessorCount()
                .parallelStream()
                .flatMap { pg ->
                    pg.map { it.first.toDataSetEntry(sourceRepo, cutMap, it.second).also { pb.stepSynchronized() } }.stream()
                }.filter {
                    it.objClassName !in depBlacklist
                }.collect(Collectors.toList())
        pb.close()
        return entries
    }

    private fun TestLogParamEntry.toDataSetEntry(
        repo: CodeRepository,
        cutMap: Map<String, String>,
        src: String,
    ): DatasetEntry {
        val cut = cutMap[this.testClassName] ?: ""
        return DatasetEntry(
            this.testClassName,
            this.testMethodName,
            cut,
            finalObjType(this),
            src,
            this.typeName.trimMockObjectTypeName(),
            this.paramIdx,
            this.methodSignature,
        )
    }

    private fun finalObjType(entry: TestLogParamEntry) =
        when {
            entry.objType != "real" -> entry.objType
            mockRegexps.any { entry.typeName.contains(it) } -> "mock"
            else -> "real"
        }

    private fun constructCUTMap(
        sourceRepo: CodeRepository,
        testClassNames: Set<String>,
    ) = testClassNames
        .associateWith { findCUT(sourceRepo, it) }
        .filterValues { it != null }
        .mapValues { (_, v) -> v as String }

    private fun findCUT(
        sourceRepo: CodeRepository,
        testClassName: String,
    ): String? {
        val cut =
            testClassName
                .trimWord("test")
                .trimWord("tests")
                .trimWord("testcase")
        when (ClassType.PRODUCTION_CODE) {
            sourceRepo.classTypeResolver[cut] -> return cut
            sourceRepo.classTypeResolver["${cut}Impl"] -> return "${cut}Impl"
            sourceRepo.classTypeResolver[cut.trimWord("abstract", trimStart = true, trimEnd = false)] -> return cut.trimWord("abstract")
        }
        val pkgName = cut.split(".").dropLast(1).joinToString(".")
        val simpleName = cut.split(".").last()
        val classNameWords = simpleName.splitCamelCase()
        for (drop in 1 until classNameWords.size) {
            val className = "$pkgName.${classNameWords.dropLast(drop).joinToString("")}"
            if (sourceRepo.classTypeResolver[className] == ClassType.PRODUCTION_CODE) {
                return className
            }
        }
        return null
    }

    private fun String.trimWord(
        word: String,
        trimStart: Boolean = true,
        trimEnd: Boolean = true,
    ): String {
        val packageName = this.split(".").dropLast(1).joinToString(".")
        var className = this.split(".").last()
        if (trimStart) {
            className = className.trimWordStart(word)
        }
        if (trimEnd) {
            className = className.trimWordEnd(word)
        }
        return "$packageName.$className"
    }

    private fun String.trimWordStart(word: String) = if (this.toLowerCase().endsWith(word)) this.dropLast(word.length) else this

    private fun String.trimWordEnd(word: String) = if (this.toLowerCase().startsWith(word)) this.drop(word.length) else this
}
