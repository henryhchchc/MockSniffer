package net.henryhc.mocksniffer.testinstrument

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import net.henryhc.mocksniffer.codeinput.CodeRepository
import net.henryhc.mocksniffer.testinstrument.sourceresources.logScript
import net.henryhc.mocksniffer.testinstrument.sourceresources.redisClientScript
import java.io.File

class AddLoggerScriptCommand : CliktCommand(name = "test-instrument-add-logger-script") {

    private val sourceRepo by option("--source-repo", "-r")
            .file(folderOkay = true, fileOkay = false, exists = true)
            .required()

    private val redisServer by option("--redis-server").default("127.0.0.1")

    private val redisPort by option("--redis-port").int().default(6379)

    override fun run() = prepareSource(CodeRepository(sourceRepo.normalize().absoluteFile))

    private fun prepareSource(repo: CodeRepository) {
        for (project in repo.projects) {

            val loggerFile = File(File(project.sourceDirectory, "tool"), "MockLogger.java")
            loggerFile.parentFile.mkdir()
            loggerFile.writeText(
                    logScript
                            .replace("<redis_host>", redisServer)
                            .replace("<redis_port>", redisPort.toString())
            )
            File(loggerFile.parentFile, "Redis.java").writeText(redisClientScript)
        }
    }

}
