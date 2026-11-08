// Java port of CM3's m3-sys/m3front/src/builtinAtomic/IsLockFree.ig and
// IsLockFree.mg.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front.builtinAtomic;

import java.util.function.Supplier;

import m3j.front.Bool;
import m3j.front.CallExpr;
import m3j.front.Expr;
import m3j.front.Formal;
import m3j.front.M3;
import m3j.front.ProcType;
import m3j.front.Procedure;
import m3j.front.Type;
import m3j.front.Value;

// File: IsLockFree.ig
// Last Modified On Thu Aug 20 15:15:16 PDT 1992 By kalsow

/**
 * The generic module IsLockFree; an instance is one instantiation.
 *
 * <p>Port: the generic parameter Rep is the variable Rep.T of the
 * instantiating interface, read when it is needed. The module variables
 * are instance fields.
 */
public final class IsLockFree {
    private final Supplier<Type.T> Rep;

    public IsLockFree(Supplier<Type.T> Rep) {
        this.Rep = Rep;
    }

    // File: IsLockFree.mg

    private CallExpr.MethodList Z;
    private Value.T formals;

    private void check(CallExpr.T ce, M3.CheckState cs) {
        // Port: args is a VAR parameter of CheckArgs.
        Expr.T[][] args = {ce.args};
        Formal.checkArgs(cs, args, formals, ce.proc);
        ce.args = args[0];
        ce.type = Bool.T;
    }

    private void compile(CallExpr.T ce) {
        if (M3.target.Atomic_lock_free[Type.cgType(Rep.get(), true).ordinal()]) {
            Value.load(Bool.True);
        } else {
            Value.load(Bool.False);
        }
    }

    private Expr.T fold(CallExpr.T ce) {
        boolean bool = M3.target.Atomic_lock_free[Type.cgType(Rep.get(), true).ordinal()];
        // Port: Bool.Map is indexed by ORD(bool).
        return Bool.Map[bool ? 1 : 0];
    }

    public void initialize() {
        Type.T t0 = ProcType.new_(Bool.T);
        Z = CallExpr.newMethodList(0, 0, true, false, false, Bool.T,
                                   null, null,
                                   CallExpr::notAddressable,
                                   this::check,
                                   CallExpr::prepArgs,
                                   this::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::prepNoBranch,
                                   CallExpr::noBranch,
                                   this::fold,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("IsLockFree", Z, false, t0);
        formals = ProcType.formals(t0);
    }
}
