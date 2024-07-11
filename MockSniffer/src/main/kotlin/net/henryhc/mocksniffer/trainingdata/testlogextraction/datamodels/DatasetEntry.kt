package net.henryhc.mocksniffer.trainingdata.testlogextraction.datamodels

import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord

data class DatasetEntry(
    val testClassName: String,
    val testMethodName: String,
    val classUnderTest: String,
    val objType: String,
    val src: String,
    val objClassName: String,
    val paramIdx: Int,
    val methodSignature: String,
)

val datasetEntryCsvHeader =
    arrayOf(
        "test_class_name",
        "test_method_name",
        "cut",
        "obj_type",
        "src",
        "obj_class",
        "param_idx",
        "method_signature",
    )

fun DatasetEntry.printCsvRecord(printer: CSVPrinter) =
    printer.printRecord(
        this.testClassName,
        this.testMethodName,
        this.classUnderTest,
        this.objType,
        this.src,
        this.objClassName,
        this.paramIdx,
        this.methodSignature,
    )

fun CSVRecord.toDatasetEntry() =
    DatasetEntry(
        this["test_class_name"],
        this["test_method_name"],
        this["cut"],
        this["obj_type"],
        this["src"],
        this["obj_class"],
        this["param_idx"].toInt(),
        this["method_signature"],
    )
