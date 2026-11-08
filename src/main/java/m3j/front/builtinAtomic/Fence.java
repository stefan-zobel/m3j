// Java port of CM3's m3-sys/m3front/src/builtinAtomic/Fence.ig and Fence.mg.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front.builtinAtomic;

import java.util.function.Supplier;

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

// File: Fence.ig
// Last Modified On Thu Aug 20 15:15:16 PDT 1992 By kalsow

/**
 * The generic module Fence; an instance is one instantiation.
 *
 * <p>Port: the generic parameter Atomic is the instance of AtomicModule
 * (the instances refer to each other), read when it is needed. The module
 * variables are instance fields.
 */
public final class Fence {
    private final Supplier<AtomicModule> Atomic;

    public Fence(Supplier<AtomicModule> Atomic) {
        this.Atomic = Atomic;
    }

    // File: Fence.mg

    private CallExpr.MethodList Z;
    private Value.T formals;

    private void check(CallExpr.T ce, M3.CheckState cs) {
        int[] order = TInt.newInt();
        Type.T[] t = new Type.T[1];
        long[] z = new long[1];
        // Port: args is a VAR parameter of CheckArgs.
        Expr.T[][] args = {ce.args};
        Formal.checkArgs(cs, args, formals, ce.proc);
        ce.args = args[0];
        if (EnumExpr.split(ce.args[0], order, t) && TInt.toInt(order, z)) {
            if (z[0] == M3CG.MemoryOrder.Sequential.ordinal()) {
                // ok
            } else {
                Error.warn(0, "Fence currently only supports Order.Sequential");
            }
        } else {
            Error.msg("order must be an enumeration constant");
        }
        ce.type = null;
    }

    private void compile(CallExpr.T ce) {
        int[] order = TInt.newInt();
        Type.T[] t = new Type.T[1];
        long[] z = new long[1];
        EnumExpr.split(ce.args[0], order, t);
        TInt.toInt(order, z);
        CG.fence(M3CG.MemoryOrder.values()[(int) z[0]]);
    }

    public void initialize() {
        Formal.Info order = new Formal.Info();
        order.name = M3ID.add(M3.ids, "order");
        order.mode = Formal.Mode.mVALUE;
        order.offset = 0;
        order.type = Atomic.get().Order;
        order.dfault = Atomic.get().Sequential;
        order.unused = false;
        order.trace = null;
        Type.T t0 = ProcType.new_(null, Formal.new_(order));
        Z = CallExpr.newMethodList(0, 1, false, true, true, null,
                                   null, null,
                                   CallExpr::notAddressable,
                                   this::check,
                                   CallExpr::prepArgs,
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
        Procedure.definePredefined("Fence", Z, false, t0);
        formals = ProcType.formals(t0);
    }
}
