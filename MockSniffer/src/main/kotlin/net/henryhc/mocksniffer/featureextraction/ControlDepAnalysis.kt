package net.henryhc.mocksniffer.featureextraction

import net.henryhc.mocksniffer.utilities.includeSuperClasses
import soot.Body
import soot.SootClass
import soot.Unit
import soot.jimple.IfStmt
import soot.jimple.InstanceInvokeExpr
import soot.jimple.Stmt
import soot.toolkits.graph.BriefUnitGraph
import soot.toolkits.graph.MHGDominatorsFinder
import soot.toolkits.graph.MHGPostDominatorsFinder

class ControlDepAnalysis(body: Body, depClass: SootClass) {

    private val trapHandlers = body.traps.map { it.handlerUnit }.toSet()

    private val cfg = BriefUnitGraph(body)

    private val invocations: Set<Unit>

    init {
        val depSuperClasses = depClass.includeSuperClasses()
        invocations = body.units
                .filterIsInstance<Stmt>()
                .filter {
                    it.containsInvokeExpr()
                            && it.invokeExpr is InstanceInvokeExpr
                            && it.invokeExpr.method.declaringClass in depSuperClasses
                }.toSet()
    }

    val invDominatedBranchAnTraps by lazy {
        invocations.associateWith { getControlDependencies(it) }
    }

    private val dominantFinder = MHGDominatorsFinder(cfg)

    private val postDominatorsFinder = MHGPostDominatorsFinder(cfg)

    private fun getControlDependencies(unit: Unit): Set<Unit> =
            dominantFinder.getDominators(unit)
                    .filterIsInstance<IfStmt>()
                    .filter { !postDominatorsFinder.isDominatedBy(it, unit) || it in trapHandlers }
                    .toSet()


}