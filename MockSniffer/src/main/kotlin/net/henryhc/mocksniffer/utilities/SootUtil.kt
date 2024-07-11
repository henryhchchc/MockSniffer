package net.henryhc.mocksniffer.utilities

import net.henryhc.mocksniffer.codeinput.Project
import soot.*
import soot.jimple.toolkits.callgraph.CallGraph
import soot.options.Options
import soot.tagkit.VisibilityAnnotationTag
import java.io.File
import java.util.*

fun PackManager.addSceneTransformer(
    phaseName: String,
    body: (phaseName: String, options: Map<String, String>) -> Unit,
) = this.getPack(phaseName.split(".").first()).add(
    Transform(
        phaseName,
        object : SceneTransformer() {
            override fun internalTransform(
                phaseName: String,
                options: Map<String, String>,
            ) = body(phaseName, options)
        },
    ),
)

fun SootClass.includeSuperClasses() =
    generateSequence({ this }, {
        if (it.hasSuperclass()) it.superclass else null
    }).filter { it != Scene.v().objectType.sootClass }.toList()

private val testMethodAnnotations =
    setOf(
        "Lorg/junit/Test;",
        "Lorg/junit/jupiter/api/Test;",
    )

private val setupAnnotations =
    setOf(
        "Lorg/junit/Before;",
        "Lorg/junit/After;",
        "Lorg/junit/BeforeClass;",
        "Lorg/junit/AfterClass;",
        "Lorg/junit/jupiter/api/BeforeAll;",
        "Lorg/junit/jupiter/api/BeforeEach;",
        "Lorg/junit/jupiter/api/AfterAll;",
        "Lorg/junit/jupiter/api/AfterEach;",
    )

fun SootMethod.isTestSetupMethod(): Boolean =
    this
        .getTag("VisibilityAnnotationTag")
        .let { it as? VisibilityAnnotationTag }
        ?.annotations
        ?.any { it.type in setupAnnotations } ?: false

fun SootMethod.isTestMethod(): Boolean =
    this
        .getTag("VisibilityAnnotationTag")
        .let { it as? VisibilityAnnotationTag }
        ?.annotations
        ?.any { it.type in testMethodAnnotations } ?: false

fun SootClass.isFunctionalInterface() =
    this
        .getTag("VisibilityAnnotationTag")
        .let { it as? VisibilityAnnotationTag }
        ?.annotations
        ?.any { it.type == "Ljava/lang/FunctionalInterface;" }
        ?: false

fun generateMethodCallLevels(
    seed: Iterable<SootMethod>,
    callGraph: CallGraph = Scene.v().callGraph,
): Sequence<Set<SootMethod>> {
    val visited = mutableSetOf<SootMethod>()
    visited.addAll(seed)
    return generateSequence({ seed.toSet() }, { ms ->
        ms
            .flatMap { callGraph.edgesOutOf(it).asSequence().toList() }
            .map { it.tgt.method() }
            .filter { it !in visited }
            .toSet()
    })
}

fun List<SootClass>.includeInterfaces() =
    if (this.isEmpty()) {
        emptyList()
    } else {
        Sequence {
            val classIter = this.listIterator()
            iterator {
                val queue = ArrayDeque<SootClass>()
                val visited = mutableSetOf<SootClass>()
                queue.add(classIter.next())
                while (queue.isNotEmpty()) {
                    val visit = queue.pop()
                    if (visit in visited) {
                        continue
                    }
                    visited.add(visit)
                    yield(visit)
                    val nextVisit = visit.interfaces
                    if (nextVisit.isEmpty() && classIter.hasNext()) {
                        queue.add(classIter.next())
                    } else {
                        queue.addAll(nextVisit)
                    }
                }
            }
        }.toList()
    }

fun configureSoot(
    project: Project,
    java8RuntimePath: File,
) {
    with(Options.v()) {
        set_output_format(Options.output_format_none)
        set_src_prec(Options.src_prec_class)

        val sootCp =
            (
                setOf(project.targetClassDir, project.testTargetClassDir)
                    .filter { it.exists() }
                    .map { it.absolutePath } +
                    getRuntimeJars(java8RuntimePath).map { it.absolutePath } + project.classPath
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
        val processDir =
            setOf(project.targetClassDir, project.testTargetClassDir)
                .filter { it.exists() }
                .map { it.absolutePath }
        set_process_dir(processDir)

        if (System.getProperty("sun.boot.class.path") == null) {
            System.setProperty("sun.boot.class.path", "")
        }
        if (System.getProperty("java.ext.dirs") == null) {
            System.setProperty("java.ext.dirs", "")
        }
    }
}

fun SootMethod.expandCallGraph(
    callGraph: CallGraph = Scene.v().callGraph,
    filter: (SootMethod) -> Boolean = { true },
): Set<SootMethod> {
    val result = mutableSetOf<SootMethod>()
    val queue = ArrayDeque<SootMethod>().apply { add(this@expandCallGraph) }
    val visited = mutableSetOf<SootMethod>()
    while (queue.isNotEmpty()) {
        val method = queue.pop()
        visited.add(method)
        result.add(method)
        val targets =
            callGraph
                .edgesOutOf(method)
                .asSequence()
                .map { it.tgt.method() }
                .filter(filter)
                .filterNot { it in visited || it in queue }
                .toSet()
        queue.addAll(targets)
    }
    return result
}
