package net.henryhc.mocksniffer.trainingdata.testlogextraction.datamodels


data class StackFrame(
        val fileName: String,
        val lineNumber: Int,
        val className: String,
        val methodName: String,
        var classType: String = ""
) {
    override fun toString() = "$fileName,$lineNumber,$className,$methodName,$classType"

    companion object {
        fun parseLine(line: String): StackFrame? {
            val parts = line.split(",")
            return when (parts.size) {
                4 -> StackFrame(parts[0], parts[1].toInt(), parts[2], parts[3])
                5 -> StackFrame(parts[0], parts[1].toInt(), parts[2], parts[3], parts[4])
                else -> null
            }
        }
    }
}
