package net.henryhc.mocksniffer.featureextraction

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.google.common.collect.Maps
import net.henryhc.academic.mockingproject.codelevelfeatures.unstableAPIs
import net.henryhc.academic.mockingproject.codelevelfeatures.unstableInterfaces
import net.henryhc.mocksniffer.SootEnvCommand
import net.henryhc.mocksniffer.codeinput.ClassType
import net.henryhc.mocksniffer.codeinput.CodeRepository
import net.henryhc.mocksniffer.codeinput.Project
import net.henryhc.mocksniffer.dependencyresolving.DepEntry
import net.henryhc.mocksniffer.utilities.*
import org.apache.commons.csv.CSVFormat
import org.apache.log4j.BasicConfigurator
import org.apache.log4j.Level
import org.apache.log4j.Logger
import soot.*
import soot.jimple.Jimple
import soot.shimple.Shimple
import soot.toolkits.graph.ExceptionalUnitGraph
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.stream.Collectors

class ProjectExtractorCommand : SootEnvCommand(name = "code-level-features-project") {

    private val inputCsv by option("-i").file().required()
    private val outputCsv by option("-o").file().required()
    private val projectDir by option("-p").file().required()
    private val cgExpand by option("-cgexp").int().required()

    override fun run() {
        val project = CodeRepository(projectDir.normalize().absoluteFile).projects.first()
        val inputData = inputCsv.bufferedReader().use { reader ->
            CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)
                .map { DepEntry(it["CUT"], it["DEP"], it["ORD"].toInt()) }
        }
        configureSoot(project, java8RuntimePath)
        PackManager.v().addSceneTransformer("wspp.loadbodies") { _, _ ->
            val semaphore = Semaphore(128)
            Scene.v().classes.snapshotIterator().toList().parallelStream().forEach { clz ->
                semaphore.acquire()
                clz.methods.filter { it.isConcrete }.forEach { m ->
                    try {
                        m.retrieveActiveBody()
                    } catch (ex: Throwable) {
                        m.activeBody = Shimple.v().newBody(Jimple.v().newBody(m).apply {
                            units.add(Jimple.v().newReturnVoidStmt())
                        })
                    }
                }
                semaphore.release()
            }
        }

        PackManager.v().addSceneTransformer("wstp.extractfeatures") { _, _ ->

            val semaphore = Semaphore(48)
            val classNames = Scene.v().classes.map { it.name }.toSet()

            val resultDataset = inputData.parallelStream().map { entry ->
                semaphore.acquire()
                extractFeatures(entry, classNames, project).also { semaphore.release() }
            }.collect(Collectors.toList()).filterNotNull()
            outputCsv.delete()
            outputCsv.bufferedWriter().use { writer ->
                val printer = CSVFormat.DEFAULT.withHeader(*codeLevelFeatureEntryHeader).print(writer)
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

    private fun extractFeatures(entry: DepEntry, classNames: Set<String>, project: Project): CodeLevelFeatureEntry? {
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

        val depInUnstableList = depSuperClasses.any { isInUnstableAPIList(it.name) }

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
            it.invocationsDependsOnParam +
                    it.invocationsDependsOnField +
                    it.invocationsDependsOnRet
        }
        val retBFA = analyses.sumBy {
            it.returnValueUsedByBranches +
                    it.returnValueUsedAsArg +
                    it.returnValueUsedForReturn +
                    it.returnValueUsedToField
        }

        val dominance = cutSuperClasses
            .flatMap { it.methods }
            .filter { it.isConcrete }
            .map { it.retrieveActiveBody() }
            .map { ControlDepAnalysis(it, depClass) }
            .sumBy { it.invDominatedBranchAnTraps.values.count { it.isNotEmpty() } }

        return CodeLevelFeatureEntry(
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
            depInUnstableAPIList = depInUnstableList,
            depIsFinal = depClass.isFinal,
            depUnstableAPIs = depUnstableAPIs,
            depUnstableAPIsTransitive = depUnstableAPIsTransitive,
            depImplUnstableInterfaces = depImplUnstableInterfaces,
//                depInvNativeMethods = depInvNativeMethods,
            depInvSynchronizedMethods = depInvSynchronizedMethods,
            callSitesOnDependency = callSiteOnDep,
            argsFromFPR = invArgsFPR,
            returnValueBFA = retBFA,
            exceptionsCaught = caughtInv,
            controlDominance = dominance
        )
    }

    private val synchronizedMethodInvCache = ConcurrentHashMap<SootClass, Int>()

    private fun SootClass.countInvokeSynchronizedMethods() = synchronizedMethodInvCache.getOrPut(this) {
        this.includeSuperClasses()
            .flatMap { it.methods }
            .flatMap { Scene.v().callGraph.edgesOutOf(it).toList() }
            .map { it.tgt.method() }
            .count { it.isSynchronized }
    }

    private val transitiveDependenciesCache = ConcurrentHashMap<SootClass, Set<SootClass>>()

    private fun SootClass.transitiveDependencies() = transitiveDependenciesCache.getOrPut(this) {
        this.dependencyChain().take(cgExpand).flatten().toSet()
    }

    private fun SootClass.directDependencies() = includeSuperClasses().toSet()
        .flatMap { it.extractReferencedTypes() }.toSet()

    private val unstableAPICountTransitiveCache = ConcurrentHashMap<SootClass, Int>()

    private fun countUnstableAPIsTransitive(depClass: SootClass) = unstableAPICountTransitiveCache
        .getOrPut(depClass) {
            generateMethodCallLevels(depClass.includeSuperClasses().toSet().flatMap { it.methods })
                .take(cgExpand)
                .flatten()
                .filter { it.isConcrete }
                .sumBy { it.countUnstableAPIs() }
        }

    private val unstableAPICountCache = ConcurrentHashMap<SootClass, Int>()

    private fun countUnstableAPIs(depClass: SootClass) = unstableAPICountCache.getOrPut(depClass) {
        depClass.includeSuperClasses().toSet()
            .flatMap { it.methods }
            .filter { it.isConcrete }
            .sumBy { it.countUnstableAPIs() }
    }


    private val callingContextCache = ConcurrentHashMap<Pair<SootMethod, SootClass>, Set<List<SootMethod>>>()

    private fun searchAllCallingContext(
        sourceMethod: SootMethod,
        targetClass: SootClass,
        depthLimit: Int
    ): Set<List<SootMethod>> {
        return callingContextCache.getOrPut(Pair(sourceMethod, targetClass)) {
            searchAllCallingContextInternal(sourceMethod, targetClass, depthLimit)
        }
    }

    private fun searchAllCallingContextInternal(
        sourceMethod: SootMethod,
        targetClass: SootClass,
        depthLimit: Int
    ): Set<List<SootMethod>> {
        val result = mutableSetOf<List<SootMethod>>()
        val targetMethods = targetClass.includeSuperClasses().flatMap { it.methods }.toSet()
        val pathStack = Stack<SootMethod>()
        fun dls(current: SootMethod, context: Stack<SootMethod>, depth: Int) {
            if (context.peek() in targetMethods) {
                result.add(pathStack.toList())
                return
            }
            if (depth > depthLimit)
                return
            for (next in Scene.v().callGraph.edgesOutOf(current)) {
                if (next.tgt.method() in pathStack)
                    continue
                pathStack.push(next.tgt.method())
                dls(next.tgt.method(), pathStack, depth + 1)
                pathStack.pop()
            }
        }
        pathStack.push(sourceMethod)
        dls(sourceMethod, pathStack, 0)
        return result
    }

    private val methodUnstableAPIsCountCache = ConcurrentHashMap<SootMethod, Int>()

    private fun SootMethod.countUnstableAPIs(): Int {
        require(this.isConcrete)
        return methodUnstableAPIsCountCache.getOrPut(this) {
            val localTypes = this.retrieveActiveBody().locals.map { it.type }
            val paramTypes = this.parameterTypes
            return (localTypes + paramTypes + this.returnType)
                .filterIsInstance<RefType>()
                .map { it.sootClass.name }
                .count { isInUnstableAPIList(it) }
        }
    }

    private fun isInUnstableAPIList(it: String) =
        unstableAPIs.any { u -> if (u.endsWith(".")) it.startsWith(u) else it == u }

    private val referencedTypesCache = Maps.newConcurrentMap<SootClass, Set<SootClass>>()

    private fun SootClass.extractReferencedTypes(): Set<SootClass> {
        return synchronized(referencedTypesCache) {
            referencedTypesCache.getOrPut(this) {
                val returnTypes = this.methods
                    .map { it.returnType }
                val localTypes = this.methods
                    .filter { it.isConcrete }
                    .flatMap { it.retrieveActiveBody().locals }
                    .map { it.type }
                val paramTypes = this.methods
                    .flatMap { it.parameterTypes }
                val fieldTypes = this.fields
                    .map { it.type }

                return (returnTypes + localTypes + paramTypes + fieldTypes)
                    .map { if (it is ArrayType) it.baseType else it }
                    .filterIsInstance<RefType>()
                    .map { it.sootClass }
                    .toSet()
            }
        }
    }

    private fun SootClass.dependencyChain() = generateSequence({ this.extractReferencedTypes() }) { ref ->
        val nextRef = ref.flatMap { this.extractReferencedTypes() }.toSet()
        if (nextRef.isEmpty()) null else nextRef
    }

}


