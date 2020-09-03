package net.henryhc.mocksniffer.prediction.input

import net.henryhc.mocksniffer.PreProjectFeatureExtractor
import net.henryhc.mocksniffer.codeinput.ClassType
import net.henryhc.mocksniffer.codeinput.CodeRepository
import net.henryhc.mocksniffer.codeinput.Project
import net.henryhc.mocksniffer.featureengineering.BodyFeatureAnalysis
import net.henryhc.mocksniffer.featureengineering.ControlDepAnalysis
import net.henryhc.mocksniffer.featureengineering.unstableInterfaces
import net.henryhc.mocksniffer.prediction.dependencyresolving.DepEntry
import net.henryhc.mocksniffer.utilities.*
import org.apache.commons.csv.CSVFormat
import org.apache.log4j.BasicConfigurator
import org.apache.log4j.Level
import org.apache.log4j.Logger
import soot.Main
import soot.PackManager
import soot.RefType
import soot.Scene
import soot.toolkits.graph.ExceptionalUnitGraph
import java.util.concurrent.Semaphore
import java.util.stream.Collectors

class PredictionInputExtractionPreProject : PreProjectFeatureExtractor("extract-prediction-input-pre-project") {

    override fun run() {
        val project = CodeRepository(projectDir.normalize().absoluteFile).projects.first()
        val inputData = inputCsv.bufferedReader().use { reader ->
            CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)
                .map { DepEntry(it["CUT"], it["DEP"], it["ORD"].toInt()) }
        }
        configureSoot(project, java8RuntimePath)
        configureLoadBodies()

        PackManager.v().addSceneTransformer("wstp.extractfeatures") { _, _ ->

            val semaphore = Semaphore(48)
            val classNames = Scene.v().classes.map { it.name }.toSet()

            val resultDataset = inputData.parallelStream().map { entry ->
                semaphore.acquire()
                extractFeatures(entry, classNames, project).also { semaphore.release() }
            }.collect(Collectors.toList()).filterNotNull()
            outputCsv.delete()
            outputCsv.bufferedWriter().use { writer ->
                val printer = CSVFormat.DEFAULT.withHeader(*predictionInputEntryHeaders).print(writer)
                resultDataset.forEach { it.printCsvRecord(printer) }
            }
        }

        try {
            BasicConfigurator.configure()
            Logger.getRootLogger().level = Level.WARN
            Main.v().run(emptyArray())
        } catch (ex: Exception) {
            System.err.println(project.rootDirectory)
            ex.printStackTrace()
        }
    }



    private fun extractFeatures(entry: DepEntry, classNames: Set<String>, project: Project): PredictionInputEntry? {
        if (entry.dep !in classNames)
            return null

        val depClass = Scene.v().getSootClass(entry.dep)

        if (depClass.type !is RefType)
            return null

        if (depClass.isFunctionalInterface())
            return null

        var classUnderTest = Scene.v().getSootClass(entry.cut)
        if (classUnderTest.isInterface && "${classUnderTest.name}Impl" in classNames)
            classUnderTest = Scene.v().getSootClass("${classUnderTest.name}Impl")
        if (classUnderTest == depClass) {
            return null
        }

        val cutSuperClasses = classUnderTest.includeSuperClasses().includeInterfaces().toSet()

        val depSuperClasses = depClass.includeSuperClasses().toSet().apply {
            forEach { Scene.v().loadClassAndSupport(it.name) }
        }

        // Feature extraction begin
        val depRefClasses = depClass.directDependencies()
        val depTransitiveRefs = depClass.transitiveDependencies()

        val depUnstableAPIs = countUnstableAPIs(depClass)
        val depUnstableAPIsTransitive = countUnstableAPIsTransitive(depClass)

        val depImplUnstableInterfaces = depClass.includeSuperClasses().includeInterfaces()
            .filter { it.isInterface }
            .map { it.name }
            .any { unstableInterfaces.any { ui -> if (ui.endsWith(".")) it.startsWith(ui) else it == ui } }


        val depInvSynchronizedMethods = depClass.countInvokeSynchronizedMethods()

        val callSiteOnDep = cutSuperClasses
            .flatMap { it.methods }
            .filter { it.isConcrete }
            .flatMap { Scene.v().callGraph.edgesOutOf(it).toList() }
            .map { it.tgt.method().declaringClass }
            .count { it in depSuperClasses }

        val analyses = cutSuperClasses
            .flatMap { it.methods }
            .filter { it.isConcrete }
            .map { ExceptionalUnitGraph(it.retrieveActiveBody()) }
            .map { BodyFeatureAnalysis(it, depClass, classUnderTest) }

        val caughtInv = analyses.sumBy { it.caughtInvokeStmts }
        val invArgsFPR = analyses.sumBy {
//            it.invocationsDependsOnBranch
            it.invocationsDependsOnParam //+ it.invocationsDependsOnField + it.invocationsDependsOnRet
        }
        val retBFA = analyses.sumBy {
            it.returnValueUsedByBranches //+ it.returnValueUsedAsArg + it.returnValueUsedForReturn + it.returnValueUsedToField
        }

        val dominance = cutSuperClasses
            .flatMap { it.methods }
            .filter { it.isConcrete }
            .map { it.retrieveActiveBody() }
            .map { ControlDepAnalysis(it, depClass) }
            .sumBy { it.invDominatedBranchAnTraps.values.count { it.isNotEmpty() } }

        return PredictionInputEntry(
            classUnderTest = classUnderTest.name,
            dependency = depClass.name,
            dependencyOrder = entry.order,
            // features starts here
            depIsAbstract = depClass.isAbstract,
            depIsInterface = depClass.isInterface,
            depIsJDKClass = depClass.isJavaLibraryClass && !isInUnstableAPIList(depClass.name),
            depIsInCodeBase = project.codeRepository.classTypeResolver[depClass.name] == ClassType.PRODUCTION_CODE,
            depDeps = depRefClasses.size,
            depTransitiveDeps = depTransitiveRefs.size,
            depFields = depSuperClasses.sumBy { it.fieldCount },
            depUnstableAPIs = depUnstableAPIs,
            depUnstableAPIsTransitive = depUnstableAPIsTransitive,
            depImplUnstableInterfaces = depImplUnstableInterfaces,
            depInvSynchronizedMethods = depInvSynchronizedMethods,
            callSitesOnDependency = callSiteOnDep,
            argsFromFPR = invArgsFPR,
            returnValueBFA = retBFA,
            exceptionsCaught = caughtInv,
            controlDominance = dominance
        )
    }

}


