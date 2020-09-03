package net.henryhc.mocksniffer.trainingdata.testlogextraction.datamodels

import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord

data class TestLogParamEntry(
        val testClassName: String,
        val testMethodName: String,
        val typeName: String,
        val objType: String,
        val paramIdx: Int,
        val methodSignature: String,
        var classType: String = ""
)

fun MethodParamItem.toDistillEntry() = TestLogParamEntry(
        this.testFrame!!.className,
        this.testFrame.methodName,
        this.typeName,
        this.objType,
        this.paramIdx,
        this.methodSignature
)

val invocationDistillEntryHeaders = arrayOf(
        "test_class_name",
        "test_method_name",
        "type_name",
        "obj_type",
        "param_idx",
        "method_signature",
        "class_type"
)

fun CSVRecord.toInvOrObjDistillEntry() = TestLogParamEntry(
        this["test_class_name"],
        this["test_method_name"],
        this["type_name"],
        this["obj_type"],
        this["param_idx"].toInt(),
        this["method_signature"],
        this["class_type"]
)

fun TestLogParamEntry.printCsvRecord(printer: CSVPrinter) = printer.printRecord(
        this.testClassName,
        this.testMethodName,
        this.typeName,
        this.objType,
        this.paramIdx,
        this.methodSignature,
        this.classType
)