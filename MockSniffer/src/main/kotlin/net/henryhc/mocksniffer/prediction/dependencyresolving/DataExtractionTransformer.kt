package net.henryhc.mocksniffer.prediction.dependencyresolving

import com.google.common.collect.Sets
import org.apache.commons.csv.CSVFormat
import soot.RefType
import soot.Scene
import soot.SceneTransformer
import soot.SootClass
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class DataExtractionTransformer(
    private val outputFile: File,
    private val searchDepth: Int
) : SceneTransformer() {

    private val firstOrderDeps = ConcurrentHashMap<SootClass, Set<SootClass>>()

    override fun internalTransform(phaseName: String, options: Map<String, String>) {
        Scene.v().applicationClasses.parallelStream().forEach { appClass ->
            firstOrderDeps[appClass] = appClass.getDependencies()
        }
        data class QueueEntry(val rootClass: SootClass, val dep: SootClass, val order: Int)

        val processingQueue = ConcurrentLinkedQueue<QueueEntry>()
        firstOrderDeps.keys.map { QueueEntry(it, it, 1) }.also { processingQueue.addAll(it) }
        val resultSet = Sets.newConcurrentHashSet(firstOrderDeps.flatMap { (k, v) ->
            v.map { DepEntry(k.name, it.name, 1) }
        })
        while (processingQueue.isNotEmpty()) {
            val current = processingQueue.poll() ?: continue
            resultSet.add(DepEntry(current.rootClass.name, current.dep.name, current.order))
            if (current.order <= searchDepth) {
                firstOrderDeps[current.dep]
                    ?.map { QueueEntry(current.rootClass, it, current.order + 1) }
                    ?.also { processingQueue.addAll(it) }
            }
        }
        CSVFormat.DEFAULT
            .withHeader("CUT", "DEP", "ORD")
            .print(outputFile.bufferedWriter())
            .use { p ->
                resultSet.forEach { p.printRecord(it.cut, it.dep, it.order) }
            }

    }

    private fun SootClass.getDependencies() = this.methods
        .flatMap { it.parameterTypes }
        .filterIsInstance<RefType>()
        .map { it.sootClass }
        .filter { it.shouldIncludeInDependencies() }
        .toSet()
}
