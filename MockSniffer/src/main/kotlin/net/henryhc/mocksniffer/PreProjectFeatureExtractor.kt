package net.henryhc.mocksniffer

import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import net.henryhc.mocksniffer.codeinput.ClassType
import net.henryhc.mocksniffer.codeinput.CodeRepository
import net.henryhc.mocksniffer.codeinput.Project
import net.henryhc.mocksniffer.utilities.*
import soot.*
import soot.jimple.Jimple
import soot.shimple.Shimple
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

abstract class PreProjectFeatureExtractor(name: String) : SootEnvCommand(name = name) {
    protected val inputCsv by option("-i").file().required()
    protected val outputCsv by option("-o").file().required()
    protected val projectDir by option("-p").file().required()

    protected val cgExpand by option("-cgexp").int().required()
    private val synchronizedMethodInvCache = ConcurrentHashMap<SootClass, Int>()
    private val transitiveDependenciesCache = ConcurrentHashMap<SootClass, Set<SootClass>>()
    private val unstableAPICountTransitiveCache = ConcurrentHashMap<SootClass, Int>()
    private val unstableAPICountCache = ConcurrentHashMap<SootClass, Int>()
    private val methodUnstableAPIsCountCache = ConcurrentHashMap<SootMethod, Int>()
    private val referencedTypesCache = ConcurrentHashMap<SootClass, Set<SootClass>>()
    private val mockKeywords = setOf(
        "mock", "dummy", "fake"
    )

    protected fun configureLoadBodies() = PackManager.v().addSceneTransformer("wspp.loadbodies") { _, _ ->
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

    protected fun SootClass.countInvokeSynchronizedMethods() = synchronizedMethodInvCache.getOrPut(this) {
        this.includeSuperClasses()
            .flatMap { it.methods }
            .flatMap { Scene.v().callGraph.edgesOutOf(it).toList() }
            .map { it.tgt.method() }
            .count { it.isSynchronized }
    }

    protected fun SootClass.transitiveDependencies() = transitiveDependenciesCache.getOrPut(this) {
        this.dependencyChain().take(cgExpand).flatten().toSet()
    }

    protected fun SootClass.directDependencies() = includeSuperClasses().toSet()
        .flatMap { it.extractReferencedTypes() }.toSet()

    protected fun countUnstableAPIsTransitive(depClass: SootClass) = unstableAPICountTransitiveCache
        .getOrPut(depClass) {
            generateMethodCallLevels(depClass.includeSuperClasses().toSet().flatMap { it.methods })
                .take(cgExpand)
                .flatten()
                .filter { it.isConcrete }
                .sumBy { it.countUnstableAPIs() }
        }

    protected fun countUnstableAPIs(depClass: SootClass) = unstableAPICountCache.getOrPut(depClass) {
        depClass.includeSuperClasses().toSet()
            .flatMap { it.methods }
            .filter { it.isConcrete }
            .sumBy { it.countUnstableAPIs() }
    }

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

    protected fun isInUnstableAPIList(it: String) =
        net.henryhc.mocksniffer.featureengineering.unstableAPIs.any { u ->
            if (u.endsWith(".")) it.startsWith(u) else it == u
        }

    private fun SootClass.extractReferencedTypes() = synchronized(referencedTypesCache) {
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

    protected fun resolveDepClass(
        sourceRepository: CodeRepository,
        depClass: SootClass,
        paramClass: SootClass,
        label: String
    ): Pair<SootClass, String> {
        // Treat impls in test script as mock
        var depClassRet = depClass
        var labelRet = label

        if (sourceRepository.classTypeResolver[depClassRet.name] == ClassType.TEST_SCRIPT
            && paramClass != depClassRet
        ) {
            labelRet = "mock"
            val depSuperTypes = depClassRet.includeSuperClasses().includeInterfaces()

            depClassRet = if (paramClass in depSuperTypes)
                paramClass
            else {
                depSuperTypes.firstOrNull() { sourceRepository.classTypeResolver[it.name] != ClassType.TEST_SCRIPT }
                    ?: depClass.also { labelRet = "real" }
            }
        }

        // compare the parameter type with object type name
        if (labelRet == "real" && paramClass != depClassRet) {
            val depSuperTypes = depClassRet.includeSuperClasses().includeInterfaces()
            if (paramClass != Scene.v().objectType.sootClass
                && paramClass in depSuperTypes
                && (paramClass.isAbstract || paramClass.isInterface)
            )
                depClassRet = paramClass

            if (depClass.name.split(".")
                    .flatMap { it.splitCamelCase() }
                    .map { it.toLowerCase() }
                    .toSet().intersect(mockKeywords)
                    .any()
            )
                labelRet = "mock"
        }
        return Pair(depClassRet, labelRet)
    }

    protected fun configureSoot(project: Project) = with(soot.options.Options.v()) {
        set_output_format(soot.options.Options.output_format_none)
        set_src_prec(soot.options.Options.src_prec_class)

        val sootCp = (
                setOf(project.targetClassDir, project.testTargetClassDir).filter { it.exists() }.map { it.absolutePath }
                        + getRuntimeJars(java8RuntimePath).map { it.absolutePath } + project.classPath
                ).joinToString(File.pathSeparator)
        set_soot_classpath(sootCp)

        set_via_shimple(true)
        set_whole_program(true)
        set_whole_shimple(true)
        set_ignore_resolution_errors(true)
        set_ignore_resolving_levels(true)
        set_allow_phantom_refs(true)
        setPhaseOption("cg", "library:any-subtype")
        setPhaseOption("cg", "all-reachable:true")
        setPhaseOption("cg", "jdkver:8")
        setPhaseOption("cg.cha", "apponly:true")
        setPhaseOption("shimple", "node-elim-opt:false")

        set_unfriendly_mode(true)
        val processDir = setOf(project.targetClassDir, project.testTargetClassDir)
            .filter { it.exists() }.map { it.absolutePath }
        set_process_dir(processDir)

        if (System.getProperty("sun.boot.class.path") == null)
            System.setProperty("sun.boot.class.path", "")
        if (System.getProperty("java.ext.dirs") == null)
            System.setProperty("java.ext.dirs", "")
    }

    private fun SootClass.dependencyChain() = generateSequence({ this.extractReferencedTypes() }) { ref ->
        val nextRef = ref.flatMap { this.extractReferencedTypes() }.toSet()
        if (nextRef.isEmpty()) null else nextRef
    }

    abstract override fun run()
}