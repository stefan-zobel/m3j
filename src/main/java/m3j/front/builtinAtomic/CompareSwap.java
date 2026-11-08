// Java port of CM3's m3-sys/m3front/src/builtinAtomic/CompareSwap.ig and
// CompareSwap.mg.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front.builtinAtomic;

import java.util.function.Supplier;

import m3j.front.Bool;
import m3j.front.CG;
import m3j.front.CallExpr;
import m3j.front.EnumExpr;
import m3j.front.Error;
import m3j.front.Expr;
import m3j.front.Formal;
import m3j.front.M3;
import m3j.front.ProcType;
import m3j.front.Procedure;
import m3j.front.Type;
import m3j.front.Value;
import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.TInt;

// File: CompareSwap.ig
// Last Modified On Thu Aug 20 15:15:16 PDT 1992 By kalsow

/**
 * The generic module CompareSwap; an instance is one instantiation.
 *
 * <p>Port: the generic parameters are the constructor parameters. Rep is
 * the variable Rep.T of the instantiating interface and Atomic the
 * instance of AtomicModule (the instances refer to each other), both read
 * when they are needed. The module variables are instance fields.
 */
public final class CompareSwap {
    private final Supplier<Type.T> Rep;
    private final Supplier<AtomicModule> Atomic;

    public CompareSwap(Supplier<Type.T> Rep, Supplier<AtomicModule> Atomic) {
        this.Rep = Rep;
        this.Atomic = Atomic;
    }

    // File: CompareSwap.mg

    private CallExpr.MethodList Z;
    private Value.T formals;

    private void check(CallExpr.T ce, M3.CheckState cs) {
        int[] order = TInt.newInt();
        Type.T[] t = new Type.T[1];
        long[] success = new long[1];
        long[] failure = new long[1];
        // Port: args is a VAR parameter of CheckArgs.
        Expr.T[][] args = {ce.args};
        Formal.checkArgs(cs, args, formals, ce.proc);
        ce.args = args[0];
        if (EnumExpr.split(ce.args[3], order, t) && TInt.toInt(order, success)) {
            if (success[0] == M3CG.MemoryOrder.Sequential.ordinal()) {
                // ok
            } else {
                Error.warn(0, "CompareSwap currently only supports Order.Sequential");
            }
        } else {
            success[0] = M3CG.MemoryOrder.Sequential.ordinal();
            Error.msg("success must be an enumeration constant");
        }
        if (EnumExpr.split(ce.args[4], order, t) && TInt.toInt(order, failure)) {
            if (failure[0] == M3CG.MemoryOrder.Release.ordinal()) {
                Error.msg("Order.Release not allowed for failure");
            } else if (failure[0] == M3CG.MemoryOrder.AcquireRelease.ordinal()) {
                Error.msg("Order.AcquireRelease not allowed for failure");
            } else if (failure[0] == M3CG.MemoryOrder.Sequential.ordinal()) {
                // ok
            } else {
                Error.warn(0, "CompareSwap currently only supports Order.Sequential");
            }
        } else {
            failure[0] = M3CG.MemoryOrder.Sequential.ordinal();
            Error.msg("failure must be an enumeration constant");
        }
        if (failure[0] > success[0]) {
            Error.msg("failure is stronger than success");
        }
        ce.type = Bool.T;
    }

    private void prep(CallExpr.T ce) {
        Expr.prepLValue(ce.args[0], true);
        Expr.prepLValue(ce.args[1], true);
        Expr.prep(ce.args[2]);
    }

    private void compile(CallExpr.T ce) {
        int[] order = TInt.newInt();
        Type.T[] t = new Type.T[1];
        long[] success = new long[1];
        long[] failure = new long[1];
        Expr.compileAddress(ce.args[0], true);
        CG.forceStacked();
        Expr.compileLValue(ce.args[1], true);
        Expr.compile(ce.args[2]);
        EnumExpr.split(ce.args[3], order, t);
        TInt.toInt(order, success);
        EnumExpr.split(ce.args[4], order, t);
        TInt.toInt(order, failure);
        CG.compare_exchange(Type.cgType(Rep.get(), true),
                            M3CG.MemoryOrder.values()[(int) success[0]],
                            M3CG.MemoryOrder.values()[(int) failure[0]]);
        Expr.noteWrite(ce.args[0]);
        Expr.noteWrite(ce.args[1]);
    }

    public void initialize() {
        Formal.Info var = new Formal.Info();
        var.name = M3ID.add(M3.ids, "var");
        var.mode = Formal.Mode.mVAR;
        var.offset = 0;
        var.type = Atomic.get().T;
        var.dfault = null;
        var.unused = false;
        var.trace = null;
        Formal.Info expected = new Formal.Info();
        expected.name = M3ID.add(M3.ids, "expected");
        expected.mode = Formal.Mode.mVAR;
        expected.offset = 1;
        expected.type = Rep.get();
        expected.dfault = null;
        expected.unused = false;
        expected.trace = null;
        Value.T desired = Formal.newBuiltin("desired", 2, Rep.get());
        Formal.Info success = new Formal.Info();
        success.name = M3ID.add(M3.ids, "success");
        success.mode = Formal.Mode.mVALUE;
        success.offset = 3;
        success.type = Atomic.get().Order;
        success.dfault = Atomic.get().Sequential;
        success.unused = false;
        success.trace = null;
        Formal.Info failure = new Formal.Info();
        failure.name = M3ID.add(M3.ids, "failure");
        failure.mode = Formal.Mode.mVALUE;
        failure.offset = 4;
        failure.type = Atomic.get().Order;
        failure.dfault = Atomic.get().Sequential;
        failure.unused = false;
        failure.trace = null;
        Type.T t0 = ProcType.new_(Bool.T, Formal.new_(var), Formal.new_(expected),
                                  desired, Formal.new_(success), Formal.new_(failure));
        Z = CallExpr.newMethodList(3, 5, false, true, true, Bool.T,
                                   null, null,
                                   CallExpr::notAddressable,
                                   this::check,
                                   this::prep,
                                   this::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   CallExpr::noValue,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("CompareSwap", Z, false, t0);
        formals = ProcType.formals(t0);
    }
}
