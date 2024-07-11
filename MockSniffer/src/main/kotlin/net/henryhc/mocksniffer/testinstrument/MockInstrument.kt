package net.henryhc.mocksniffer.testinstrument

import soot.*
import soot.jimple.*
import java.util.concurrent.ConcurrentHashMap

class MockInstrument : BodyTransformer() {
    override fun internalTransform(
        body: Body,
        phase: String,
        options: Map<String, String>,
    ) {
        val method = body.method
        if (method.declaringClass.name.startsWith("tool.")) {
            return
        }
        val units = body.units
        val iterator = units.snapshotIterator()
        while (iterator.hasNext()) {
            val stmt = iterator.next() as Stmt
            try {
                if (stmt.containsInvokeExpr()) {
                    instrumentParams(stmt, units, body)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val localNames = ConcurrentHashMap<Body, Int>()

    private fun instrumentParams(
        stmt: Stmt,
        units: UnitPatchingChain,
        body: Body,
    ) {
        localNames.putIfAbsent(body, 0)
        val recordObjMethod = Scene.v().getMethod("<tool.MockLogger: void recordObj(java.lang.Object,int,java.lang.String)>")
        if (stmt.invokeExpr is InstanceInvokeExpr ||
            (
                stmt.invokeExpr is SpecialInvokeExpr &&
                    stmt.invokeExpr.methodRef
                        .tryResolve()
                        ?.isConstructor == true
            )
        ) {
            for (i in 0 until stmt.invokeExpr.argCount) {
                val type = stmt.invokeExpr.getArg(i).type
                if (type !is RefType) {
                    continue
                }
                if (type.toString() == "java.lang.String") {
                    continue
                }
                val alias =
                    Jimple
                        .v()
                        .newLocal(
                            "loc_tmp_${localNames.put(body, localNames.getValue(body) + 1)!!}",
                            type,
                        ).also { body.locals.add(it) }
                val aliasAssign = Jimple.v().newAssignStmt(alias, stmt.invokeExpr.getArg(i))
                val invStmt =
                    Jimple
                        .v()
                        .newStaticInvokeExpr(
                            recordObjMethod.makeRef(),
                            alias,
                            IntConstant.v(i),
                            StringConstant.v(stmt.invokeExpr.methodRef.signature),
                        ).let { Jimple.v().newInvokeStmt(it) }
                units.insertBefore(listOf(aliasAssign, invStmt), stmt)
            }
        }
    }
}
