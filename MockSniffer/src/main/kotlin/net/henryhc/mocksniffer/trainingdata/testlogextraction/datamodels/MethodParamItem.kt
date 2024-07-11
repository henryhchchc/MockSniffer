package net.henryhc.mocksniffer.trainingdata.testlogextraction.datamodels

import net.henryhc.mocksniffer.codeinput.CodeRepository

class MethodParamItem(
    val objType: String,
    val typeName: String,
    val paramIdx: Int,
    val methodSignature: String,
    private val stackTrace: List<StackFrame>,
) {
    val testFrame = stackTrace.lastOrNull { it.classType == "test" }
    val isInTest: Boolean = stackTrace.lastOrNull()?.classType == "test"
}

fun parseMethodParamItem(
    lines: List<String>,
    sourceRepo: CodeRepository,
) = try {
    MethodParamItem(
        lines[0],
        lines[1],
        lines[2].toInt(),
        lines[3],
        lines.drop(4).mapNotNull {
            StackFrame.parseLine(it)?.apply {
                classType = sourceRepo.classTypeResolver[className.split("$").first(String::isNotBlank)].toString()
            }
        },
    )
} catch (ex: Exception) {
    println(lines[1])
    throw ex
}
