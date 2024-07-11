package net.henryhc.mocksniffer.trainingdata

import net.henryhc.mocksniffer.PreProjectFeatureExtractor
import net.henryhc.mocksniffer.codeinput.ClassType
import net.henryhc.mocksniffer.codeinput.CodeRepository
import net.henryhc.mocksniffer.codeinput.Project
import net.henryhc.mocksniffer.featureengineering.BodyFeatureAnalysis
import net.henryhc.mocksniffer.featureengineering.ControlDepAnalysis
import net.henryhc.mocksniffer.trainingdata.testlogextraction.datamodels.DatasetEntry
import net.henryhc.mocksniffer.trainingdata.testlogextraction.datamodels.datasetEntryCsvHeader
import net.henryhc.mocksniffer.trainingdata.testlogextraction.datamodels.toDatasetEntry
import net.henryhc.mocksniffer.utilities.*
import org.apache.commons.csv.CSVFormat
import org.apache.log4j.BasicConfigurator
import org.apache.log4j.Level
import org.apache.log4j.Logger
import soot.PackManager
import soot.RefType
import soot.Scene
import soot.toolkits.graph.ExceptionalUnitGraph
import java.util.concurrent.Semaphore
import java.util.stream.Collectors

class ExtractTrainingDataPreProject : PreProjectFeatureExtractor("extract-training-data-pre-project") {
    override fun run() {
        val project = CodeRepository(projectDir.normalize().absoluteFile).projects.first()
        val inputData =
            inputCsv.bufferedReader().use { reader ->
                CSVFormat.DEFAULT
                    .withHeader(*datasetEntryCsvHeader)
                    .parse(reader)
                    .drop(1)
                    .map { it.toDatasetEntry() }
                    .filter { it.classUnderTest.isNotEmpty() }
            }
        configureSoot(project)
        configureLoadBodies()

        PackManager.v().addSceneTransformer("wstp.extractfeatures") { _, _ ->

            val semaphore = Semaphore(48)
            val classNames =
                Scene
                    .v()
                    .classes
                    .map { it.name }
                    .toSet()

            val testSetupEntries =
                inputData
                    .parallelStream()
                    .filter {
                        val testClass = Scene.v().getSootClass(it.testClassName)
                        testClass.methods.any { m -> m.name == it.testMethodName && m.isTestSetupMethod() }
                    }.flatMap { entry ->
                        Scene
                            .v()
                            .getSootClass(entry.testClassName)
                            .methods
                            .filter { it.isTestMethod() }
                            .map {
                                entry.copy(testMethodName = it.name)
                            }.stream()
                    }.collect(Collectors.toList())

            val resultDataset =
                (testSetupEntries + inputData)
                    .parallelStream()
                    .filter { it.src == "param" }
                    .map { entry ->
                        semaphore.acquire()
                        extractFeatures(entry, classNames, project).also { semaphore.release() }
                    }.collect(Collectors.toList())
                    .filterNotNull()
            outputCsv.delete()
            outputCsv.bufferedWriter().use { writer ->
                val printer = CSVFormat.DEFAULT.withHeader(*trainingDataEntryCsvHeader).print(writer)
                resultDataset.forEach { it.printCsvRecord(printer) }
            }
        }

        try {
            BasicConfigurator.configure()
            Logger.getRootLogger().level = Level.WARN
            soot.Main.v().run(emptyArray())
        } catch (ex: Exception) {
            System.err.println(project.rootDirectory)
            ex.printStackTrace()
        }
    }

    private fun extractFeatures(
        entry: DatasetEntry,
        classNames: Set<String>,
        project: Project,
    ): TrainingDataEntry? {
        if (entry.objClassName !in classNames) {
            return null
        }

        val method = Scene.v().grabMethod(entry.methodSignature) ?: return null

        val paramType = method.parameterTypes[entry.paramIdx]
        if (paramType !is RefType) {
            return null
        }
        val paramClass = paramType.sootClass

        var depClass = Scene.v().getSootClass(entry.objClassName)
        var label = entry.objType
        val pair = resolveDepClass(project.codeRepository, depClass, paramClass, label)
        depClass = pair.first
        label = pair.second

        if (project.codeRepository.classTypeResolver[depClass.name] == ClassType.TEST_SCRIPT) {
            return null
        }

        if (depClass.isFunctionalInterface()) {
            return null
        }

        val testClass = Scene.v().getSootClass(entry.testClassName)
        val testMethod =
            testClass.methods
                .filter { it.isTestMethod() }
                .firstOrNull { it.name == entry.testMethodName } ?: return null
        val involvedTestMethods =
            testMethod
                .expandCallGraph {
                    project.codeRepository.classTypeResolver[it.declaringClass.name] == ClassType.TEST_SCRIPT
                }.toSet()

        var classUnderTest = Scene.v().getSootClass(entry.classUnderTest)
        if (classUnderTest.isInterface && "${classUnderTest.name}Impl" in classNames) {
            classUnderTest = Scene.v().getSootClass("${classUnderTest.name}Impl")
        }
        if (classUnderTest == depClass) {
            return null
        }

        val cutSuperClasses = classUnderTest.includeSuperClasses().includeInterfaces().toSet()
        val methodsUnderTest =
            generateMethodCallLevels(involvedTestMethods)
                .take(cgExpand)
                .flatten()
                .filter { it.declaringClass in cutSuperClasses && !it.isStaticInitializer }
                .toSet() - involvedTestMethods

        if (methodsUnderTest.isEmpty()) {
            return null
        }

        val depSuperClasses =
            depClass.includeSuperClasses().toSet().apply {
                forEach { Scene.v().loadClassAndSupport(it.name) }
            }

        // Feature extraction begin
        val depRefClasses = depClass.directDependencies()
        val depTransitiveRefs = depClass.transitiveDependencies()

        val depUnstableAPIs = countUnstableAPIs(depClass)
        val depUnstableAPIsTransitive = countUnstableAPIsTransitive(depClass)

        val depImplUnstableInterfaces =
            depClass
                .includeSuperClasses()
                .includeInterfaces()
                .filter { it.isInterface }
                .map { it.name }
                .any {
                    net.henryhc.mocksniffer.featureengineering.unstableInterfaces.any { ui ->
                        if (ui.endsWith(".")) {
                            it.startsWith(
                                ui,
                            )
                        } else {
                            it == ui
                        }
                    }
                }

        val depInvSynchronizedMethods = depClass.countInvokeSynchronizedMethods()

        val callSiteOnDep =
            methodsUnderTest
                .flatMap {
                    Scene
                        .v()
                        .callGraph
                        .edgesOutOf(it)
                        .toList()
                }.map { it.tgt.method().declaringClass }
                .count { it in depSuperClasses }

        val analyses =
            cutSuperClasses
                .flatMap { it.methods }
                .filter { it.isConcrete }
                .map { ExceptionalUnitGraph(it.retrieveActiveBody()) }
                .map { BodyFeatureAnalysis(it, depClass, classUnderTest) }

        val caughtInv = analyses.sumBy { it.caughtInvokeStmts }
        val invArgsFPR =
            analyses.sumBy {
//            it.invocationsDependsOnBranch
                it.invocationsDependsOnParam // + it.invocationsDependsOnField + it.invocationsDependsOnRet
            }
        val retBFA =
            analyses.sumBy {
                it.returnValueUsedByBranches // + it.returnValueUsedAsArg + it.returnValueUsedForReturn + it.returnValueUsedToField
            }
        val dominance =
            methodsUnderTest
                .filter { it.isConcrete }
                .map { it.retrieveActiveBody() }
                .map { ControlDepAnalysis(it, depClass) }
                .sumBy { it.invDominatedBranchAnTraps.values.count { it.isNotEmpty() } }

        return TrainingDataEntry(
            testClass = testClass.name,
            testMethod = testMethod.name,
            classUnderTest = classUnderTest.name,
            dependency = depClass.name,
            label = label,
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
            controlDominance = dominance,
            methodSignature = method.signature,
        )
    }
}
