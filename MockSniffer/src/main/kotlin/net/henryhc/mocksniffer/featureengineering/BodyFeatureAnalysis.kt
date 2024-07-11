package net.henryhc.mocksniffer.featureengineering

import net.henryhc.mocksniffer.utilities.includeSuperClasses
import soot.SootClass
import soot.Unit
import soot.Value
import soot.jimple.*
import soot.shimple.PhiExpr
import soot.toolkits.graph.UnitGraph
import soot.toolkits.scalar.ForwardFlowAnalysis
import java.util.*

class BodyFeatureAnalysis(
    graph: UnitGraph,
    dependency: SootClass,
    private val currentClass: SootClass,
) : ForwardFlowAnalysis<Unit, MutableSet<Unit>>(graph) {
    override fun newInitialFlow() = mutableSetOf<Unit>()

    override fun merge(
        leftFlow: MutableSet<Unit>,
        rightFlow: MutableSet<Unit>,
        outFlow: MutableSet<Unit>,
    ) {
        outFlow.addAll(leftFlow)
        outFlow.addAll(rightFlow)
    }

    override fun copy(
        src: MutableSet<Unit>,
        tgt: MutableSet<Unit>,
    ) {
        tgt.addAll(src)
    }

    private val varGraph = mutableMapOf<Value, Set<Value>>()

    private val invocations = mutableSetOf<Stmt>()

    private val depInvocations = mutableSetOf<Stmt>()

    private val depClasses = dependency.includeSuperClasses().toSet()

    private val exceptionCatches = mutableListOf<Pair<Stmt, Stmt>>()

    private val branches = mutableSetOf<IfStmt>()

    private val throwStmts = mutableSetOf<Stmt>()

    private val returnStmts = mutableSetOf<ReturnStmt>()

    override fun flowThrough(
        inFlow: MutableSet<Unit>,
        node: Unit,
        outFlow: MutableSet<Unit>,
    ) {
        outFlow.add(node)
        val stmt = node as Stmt
        if (stmt is DefinitionStmt) {
            val def = stmt.leftOp
            val uses = stmt.useBoxes.map { it.value }.toSet()
            varGraph[def] = uses
        }
        if (stmt.containsInvokeExpr()) {
            invocations.add(stmt)
            if (stmt.invokeExpr.method.isDeclared &&
                stmt.invokeExpr.method.declaringClass in depClasses
            ) {
                depInvocations.add(stmt)
                exceptionCatches.addAll(
                    graph
                        .getSuccsOf(stmt)
                        .filterIsInstance<IdentityStmt>()
                        .filter { it.rightOp is CaughtExceptionRef }
                        .map { Pair(stmt, it) },
                )
            }
        }
        if (stmt is IfStmt) {
            branches.add(stmt)
        }
        if (stmt is ThrowStmt) {
            throwStmts.add(stmt)
        }
        if (stmt is ReturnStmt) {
            returnStmts.add(stmt)
        }
    }

    val caughtInvokeStmts by lazy {
        this.exceptionCatches
            .toSet()
            .map { it.first }
            .count()
    }

    private val invDependencies by lazy {
        depInvocations.map { it.invokeExpr }.map { it.args.flatMap { it.resolveDependencies().toSet() } }
    }

    val invocationsDependsOnBranch by lazy { invDependencies.count { it.any { it is PhiExpr } } }

    val invocationsDependsOnParam by lazy { invDependencies.count { it.any { it is ParameterRef } } }

    val invocationsDependsOnField by lazy { invDependencies.count { it.any { it is FieldRef } } }

    val invocationsDependsOnRet by lazy { invDependencies.count { it.any { it is InvokeExpr } } }

    val returnValueUsedByBranches by lazy {
        val deps =
            branches
                .flatMap {
                    it.condition.useBoxes
                        .map { it.value }
                        .getDependencies()
                }.toSet()
        depInvocations.filterIsInstance<DefinitionStmt>().map { it.leftOp }.count { it in deps }
    }

    val returnValueUsedForReturn by lazy {
        val deps =
            returnStmts
                .flatMap {
                    it.op.useBoxes
                        .map { it.value }
                        .getDependencies()
                }.toSet()
        depInvocations.filterIsInstance<DefinitionStmt>().map { it.leftOp }.count { it in deps }
    }

    val returnValueUsedToField by lazy {
        val deps = varGraph.keys.filterIsInstance<FieldRef>().getDependencies()
        depInvocations.filterIsInstance<DefinitionStmt>().map { it.leftOp }.count { it in deps }
    }

    val returnValueUsedAsArg by lazy {
        val deps = invocations.flatMap { it.invokeExpr.args }.getDependencies()
        depInvocations.filterIsInstance<DefinitionStmt>().map { it.leftOp }.count { it in deps }
    }

    private fun List<Value>.getDependencies() = this.flatMap { it.resolveDependencies().toSet() }

    init {
        doAnalysis()
    }

    private fun Value.resolveDependencies(): Set<Value> {
        val result = mutableSetOf<Value>()
        val queue = ArrayDeque<Value>().also { it.add(this) }
        val visited = mutableSetOf<Value>()
        while (queue.isNotEmpty()) {
            val visit = queue.pop()
            if (visit !in visited) {
                result.add(visit)
                if (visit in varGraph) {
                    queue.addAll(varGraph.getValue(visit).filter { it !in visited })
                }
            }
            visited.add(visit)
        }
        return result
    }
}
